package com.edumentor.alert.service;

import com.edumentor.alert.dto.AlertDto;
import com.edumentor.alert.dto.AlertHandleRequest;
import com.edumentor.alert.dto.AlertStatisticsDto;
import com.edumentor.alert.entity.AlertRecord;
import com.edumentor.alert.repository.AlertRecordRepository;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.entity.enums.AlertSeverity;
import com.edumentor.entity.enums.AlertType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预警系统服务 — 多级预警生成、处理、统计核心业务逻辑。
 * <p>
 * 提供完整的预警生命周期管理：
 * <ol>
 *   <li><b>预警生成</b>：基于学情数据评估规则，自动生成 LOW/MEDIUM/HIGH/CRITICAL 四级预警</li>
 *   <li><b>预警查询</b>：按学生、类型、级别、状态等多维度筛选</li>
 *   <li><b>预警处理</b>：教师处理（解决/忽略/升级），记录处理人和备注</li>
 *   <li><b>预警统计</b>：按级别/类型聚合、趋势分析、处理率计算</li>
 *   <li><b>预警推送</b>：通过 WebSocket 实时推送预警到前端（预留接口）</li>
 * </ol>
 * </p>
 *
 * <h3>多级预警规则</h3>
 * <table>
 *   <tr><th>预警类型</th><th>触发条件</th><th>默认级别</th></tr>
 *   <tr><td>PERFORMANCE_DECLINE</td><td>最近 3 次正确率持续下降超过 20%</td><td>MEDIUM→HIGH</td></tr>
 *   <tr><td>KNOWLEDGE_GAP</td><td>BKT 掌握度 &lt; 0.4</td><td>MEDIUM</td></tr>
 *   <tr><td>STUDY_ENGAGEMENT</td><td>连续 7 天未学习</td><td>LOW→MEDIUM</td></tr>
 *   <tr><td>ERROR_RATE</td><td>错误率 &gt; 60% 持续一周</td><td>MEDIUM→HIGH</td></tr>
 *   <tr><td>TIME_PRESSURE</td><td>考试 &lt; 30 天 + 进度 &lt; 60%</td><td>HIGH→CRITICAL</td></tr>
 *   <tr><td>COMPARISON</td><td>低于班级平均正确率 &gt; 30%</td><td>LOW→MEDIUM</td></tr>
 *   <tr><td>COMBINED</td><td>上述条件同时满足 2 条及以上</td><td>提升一级</td></tr>
 * </table>
 *
 * @author EduMentor Team
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRecordRepository alertRecordRepository;

    public AlertService(AlertRecordRepository alertRecordRepository) {
        this.alertRecordRepository = alertRecordRepository;
    }

    // ══════════════════════════════════════════════════════════════
    //  预警生成
    // ══════════════════════════════════════════════════════════════

    /**
     * 创建预警记录。
     *
     * @param studentId  学生 ID
     * @param alertType  预警类型
     * @param severity   预警级别
     * @param title      预警标题
     * @param description 预警详情
     * @param triggerData 触发数据（JSON 格式）
     * @param teacherId  负责教师 ID（可选）
     * @return 创建的预警记录
     */
    @Transactional
    public AlertRecord createAlert(UUID studentId, AlertType alertType, AlertSeverity severity,
                                    String title, String description, String triggerData,
                                    UUID teacherId) {
        AlertRecord record = new AlertRecord(studentId, alertType, severity, title, description);
        record.setTriggerData(triggerData);
        record.setTeacherId(teacherId);
        // 设置自动过期时间：LOW=30天, MEDIUM=14天, HIGH=7天, CRITICAL=3天
        int expiryDays = switch (severity) {
            case LOW -> 30;
            case MEDIUM -> 14;
            case HIGH -> 7;
            case CRITICAL -> 3;
        };
        record.setExpiresAt(LocalDateTime.now().plusDays(expiryDays));

        AlertRecord saved = alertRecordRepository.save(record);
        log.info("预警已生成: studentId={}, type={}, severity={}, title={}",
                studentId, alertType, severity, title);
        return saved;
    }

    /**
     * 异步评估预警规则 — 根据学情数据判断是否需要生成预警。
     * <p>
     * 该方法由 {@code AlertRuleEvaluator} 触发，通常在 BKT 更新、
     * 作答记录提交或定时任务中调用。
     * </p>
     *
     * @param studentId       学生 ID
     * @param teacherId       负责教师 ID
     * @param recentAccuracy  最近正确率（0.0-1.0）
     * @param masteryLevel    BKT 掌握度（0.0-1.0）
     * @param daysSinceLastStudy 距离上次学习的天数
     * @param errorRate       错误率（0.0-1.0）
     * @param examDaysLeft    距离考试的天数
     * @param progressRate    学习进度（0.0-1.0）
     * @param peerAvgAccuracy 同龄平均正确率（0.0-1.0）
     */
    @Async("alertTaskExecutor")
    @Transactional
    public void evaluateRules(UUID studentId, UUID teacherId,
                              double recentAccuracy, double masteryLevel,
                              int daysSinceLastStudy, double errorRate,
                              int examDaysLeft, double progressRate,
                              double peerAvgAccuracy) {

        log.debug("评估预警规则: studentId={}, accuracy={}, mastery={}, daysSinceStudy={}",
                studentId, recentAccuracy, masteryLevel, daysSinceLastStudy);

        List<String> triggeredRules = new ArrayList<>();
        AlertSeverity maxSeverity = AlertSeverity.LOW;

        // 规则1: 成绩下滑预警
        if (recentAccuracy < 0.4 && recentAccuracy < peerAvgAccuracy * 0.7) {
            AlertSeverity severity = recentAccuracy < 0.2 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
            createAlertIfNotDuplicate(studentId, AlertType.PERFORMANCE_DECLINE, severity,
                    "成绩下滑预警",
                    String.format("最近正确率 %.0f%%，显著低于班级平均 %.0f%%，请关注学生学习状态。",
                            recentAccuracy * 100, peerAvgAccuracy * 100),
                    buildTriggerData("recentAccuracy", recentAccuracy, "peerAvgAccuracy", peerAvgAccuracy),
                    teacherId);
            triggeredRules.add("PERFORMANCE_DECLINE");
            maxSeverity = maxSeverity.ordinal() < severity.ordinal() ? severity : maxSeverity;
        }

        // 规则2: 知识点薄弱预警
        if (masteryLevel < 0.4) {
            AlertSeverity severity = masteryLevel < 0.2 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
            createAlertIfNotDuplicate(studentId, AlertType.KNOWLEDGE_GAP, severity,
                    "知识点薄弱预警",
                    String.format("BKT 知识掌握度仅为 %.0f%%，已低于掌握阈值（40%%），建议安排针对性复习。",
                            masteryLevel * 100),
                    buildTriggerData("masteryLevel", masteryLevel, "threshold", 0.4),
                    teacherId);
            triggeredRules.add("KNOWLEDGE_GAP");
            maxSeverity = maxSeverity.ordinal() < severity.ordinal() ? severity : maxSeverity;
        }

        // 规则3: 学习参与度预警
        if (daysSinceLastStudy >= 7) {
            AlertSeverity severity = daysSinceLastStudy >= 14 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
            createAlertIfNotDuplicate(studentId, AlertType.STUDY_ENGAGEMENT, severity,
                    "学习参与度预警",
                    String.format("学生已连续 %d 天未进行学习活动，请及时了解情况并鼓励学习。",
                            daysSinceLastStudy),
                    buildTriggerData("daysSinceLastStudy", daysSinceLastStudy, "threshold", 7),
                    teacherId);
            triggeredRules.add("STUDY_ENGAGEMENT");
            maxSeverity = maxSeverity.ordinal() < severity.ordinal() ? severity : maxSeverity;
        }

        // 规则4: 错误率过高预警
        if (errorRate > 0.6) {
            AlertSeverity severity = errorRate > 0.8 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
            createAlertIfNotDuplicate(studentId, AlertType.ERROR_RATE, severity,
                    "错误率过高预警",
                    String.format("近期错误率高达 %.0f%%，超过警戒线（60%%），可能存在系统性知识漏洞。",
                            errorRate * 100),
                    buildTriggerData("errorRate", errorRate, "threshold", 0.6),
                    teacherId);
            triggeredRules.add("ERROR_RATE");
            maxSeverity = maxSeverity.ordinal() < severity.ordinal() ? severity : maxSeverity;
        }

        // 规则5: 时间紧迫预警
        if (examDaysLeft > 0 && examDaysLeft <= 30 && progressRate < 0.6) {
            AlertSeverity severity = (examDaysLeft <= 14 || progressRate < 0.3)
                    ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
            createAlertIfNotDuplicate(studentId, AlertType.TIME_PRESSURE, severity,
                    "时间紧迫预警",
                    String.format("距考试仅剩 %d 天，但学习进度仅完成 %.0f%%，需要加快学习进度。",
                            examDaysLeft, progressRate * 100),
                    buildTriggerData("examDaysLeft", examDaysLeft, "progressRate", progressRate),
                    teacherId);
            triggeredRules.add("TIME_PRESSURE");
            maxSeverity = maxSeverity.ordinal() < severity.ordinal() ? severity : maxSeverity;
        }

        // 规则6: 横向对比预警
        if (peerAvgAccuracy > 0 && recentAccuracy < peerAvgAccuracy * 0.7) {
            AlertSeverity severity = recentAccuracy < peerAvgAccuracy * 0.5
                    ? AlertSeverity.HIGH : AlertSeverity.LOW;
            createAlertIfNotDuplicate(studentId, AlertType.COMPARISON, severity,
                    "横向对比预警",
                    String.format("学生正确率 %.0f%%，低于班级平均水平（%.0f%%），差距明显。",
                            recentAccuracy * 100, peerAvgAccuracy * 100),
                    buildTriggerData("recentAccuracy", recentAccuracy, "peerAvgAccuracy", peerAvgAccuracy),
                    teacherId);
            triggeredRules.add("COMPARISON");
            maxSeverity = maxSeverity.ordinal() < severity.ordinal() ? severity : maxSeverity;
        }

        // 规则7: 综合预警（同时触发 2 条及以上规则）
        if (triggeredRules.size() >= 2) {
            AlertSeverity combinedSeverity = maxSeverity.ordinal() < AlertSeverity.HIGH.ordinal()
                    ? AlertSeverity.HIGH : maxSeverity;
            createAlertIfNotDuplicate(studentId, AlertType.COMBINED, combinedSeverity,
                    "综合预警",
                    String.format("触发 %d 项预警规则：%s，建议全面评估学情并制定干预方案。",
                            triggeredRules.size(), String.join("、", triggeredRules)),
                    buildTriggerData("triggeredRules", String.join(",", triggeredRules),
                            "count", triggeredRules.size()),
                    teacherId);
        }

        if (triggeredRules.isEmpty()) {
            log.debug("未触发预警规则: studentId={}", studentId);
        }
    }

    /**
     * 检查是否存在同类未处理的预警，避免重复创建。
     */
    private void createAlertIfNotDuplicate(UUID studentId, AlertType alertType,
                                            AlertSeverity severity, String title,
                                            String description, String triggerData,
                                            UUID teacherId) {
        List<AlertRecord> existing = alertRecordRepository
                .findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId);

        boolean hasDuplicate = existing.stream()
                .anyMatch(a -> a.getAlertType() == alertType
                        && !a.getIsResolved()
                        && a.getCreatedAt().isAfter(LocalDateTime.now().minusDays(3)));

        if (!hasDuplicate) {
            createAlert(studentId, alertType, severity, title, description, triggerData, teacherId);
        } else {
            log.debug("检测到重复预警，跳过: studentId={}, type={}", studentId, alertType);
        }
    }

    /**
     * 构建触发数据 JSON 字符串。
     */
    private String buildTriggerData(Object... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(",");
            }
            Object value = keyValues[i + 1];
            sb.append("\"").append(keyValues[i]).append("\":");
            if (value instanceof Number) {
                sb.append(value);
            } else if (value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(value).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    //  预警查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询预警列表（支持分页和排序）。
     *
     * @param page     页码（1-based）
     * @param size     页大小
     * @param severity 预警级别过滤（可选）
     * @param type     预警类型过滤（可选）
     * @param resolved 处理状态过滤（可选）
     * @return 分页的预警 DTO 列表
     */
    @Transactional(readOnly = true)
    public Page<AlertDto> getAlerts(int page, int size,
                                     AlertSeverity severity, AlertType type,
                                     Boolean resolved) {
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AlertRecord> recordPage;
        if (severity != null && type != null) {
            recordPage = alertRecordRepository.findByStudentIdAndSeverityOrderByCreatedAtDesc(
                    null, severity, pageable); // 使用通用查询
            recordPage = alertRecordRepository.findBySeverityOrderByCreatedAtDesc(severity, pageable);
        } else if (severity != null) {
            recordPage = alertRecordRepository.findBySeverityOrderByCreatedAtDesc(severity, pageable);
        } else if (type != null) {
            recordPage = alertRecordRepository.findByAlertTypeOrderByCreatedAtDesc(type, pageable);
        } else if (resolved != null) {
            recordPage = resolved
                    ? alertRecordRepository.findByIsResolvedTrueOrderByResolvedAtDesc(pageable)
                    : Page.empty(pageable);
        } else {
            recordPage = alertRecordRepository.findAll(pageable);
        }

        // 如果指定了 resolved=false 但没走上面的分支
        if (resolved != null && !resolved && (severity == null && type == null)) {
            // Need to fetch unresolved from repository
            List<AlertRecord> unresolved = alertRecordRepository.findUnresolvedAlertsOrderedBySeverity();
            // Manual pagination
            int start = (page - 1) * size;
            int end = Math.min(start + size, unresolved.size());
            List<AlertRecord> paged = start >= unresolved.size()
                    ? List.of() : unresolved.subList(start, end);
            return new org.springframework.data.domain.PageImpl<>(
                    paged, pageable, unresolved.size()).map(AlertDto::fromEntity);
        }

        return recordPage.map(AlertDto::fromEntity);
    }

    /**
     * 根据 ID 获取预警详情。
     *
     * @param id 预警 ID
     * @return 预警 DTO
     * @throws ResourceNotFoundException 如果预警不存在
     */
    @Transactional(readOnly = true)
    public AlertDto getAlertById(UUID id) {
        AlertRecord record = alertRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("预警", id));
        return AlertDto.fromEntity(record);
    }

    /**
     * 查询学生的预警列表。
     *
     * @param studentId 学生 ID
     * @param page      页码
     * @param size      页大小
     * @return 分页的预警 DTO 列表
     */
    @Transactional(readOnly = true)
    public Page<AlertDto> getAlertsByStudent(UUID studentId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return alertRecordRepository
                .findByStudentIdOrderByCreatedAtDesc(studentId, pageable)
                .map(AlertDto::fromEntity);
    }

    /**
     * 查询学生未处理的预警列表。
     *
     * @param studentId 学生 ID
     * @return 未处理预警 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<AlertDto> getUnresolvedAlertsByStudent(UUID studentId) {
        return alertRecordRepository
                .findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId)
                .stream()
                .map(AlertDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 查询教师负责的所有未处理预警。
     *
     * @param teacherId 教师 ID
     * @return 未处理预警 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<AlertDto> getUnresolvedAlertsByTeacher(UUID teacherId) {
        return alertRecordRepository
                .findByTeacherIdAndIsResolvedFalseOrderByCreatedAtDesc(teacherId)
                .stream()
                .map(AlertDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有未处理预警（按级别优先级排序）。
     *
     * @return 未处理预警 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<AlertDto> getUnresolvedAlerts() {
        return alertRecordRepository.findUnresolvedAlertsOrderedBySeverity()
                .stream()
                .map(AlertDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════
    //  预警处理
    // ══════════════════════════════════════════════════════════════

    /**
     * 标记预警为已读。
     *
     * @param id 预警 ID
     * @return 更新后的预警 DTO
     */
    @Transactional
    public AlertDto markAsRead(UUID id) {
        AlertRecord record = alertRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("预警", id));
        record.setIsRead(true);
        return AlertDto.fromEntity(alertRecordRepository.save(record));
    }

    /**
     * 处理预警（解决/忽略/升级）。
     *
     * @param request 处理请求
     * @return 处理后的预警 DTO
     * @throws ValidationException 如果请求参数无效
     */
    @Transactional
    public AlertDto handleAlert(AlertHandleRequest request) {
        if (request.getAlertId() == null) {
            throw new ValidationException("预警 ID 不能为空");
        }

        AlertRecord record = alertRecordRepository.findById(request.getAlertId())
                .orElseThrow(() -> new ResourceNotFoundException("预警", request.getAlertId()));

        if (record.getIsResolved()) {
            throw new ValidationException("预警已被处理，不可重复处理");
        }

        String action = request.getAction();
        switch (action.toUpperCase()) {
            case "RESOLVE" -> {
                record.setIsResolved(true);
                record.setHandleNote(request.getNote());
                record.setResolvedBy(request.getResolvedBy());
                record.setResolvedAt(LocalDateTime.now());
                log.info("预警已解决: id={}, resolvedBy={}", record.getId(), request.getResolvedBy());
            }
            case "DISMISS" -> {
                record.setIsResolved(true);
                record.setHandleNote("已忽略: " + (request.getNote() != null ? request.getNote() : "无需处理"));
                record.setResolvedBy(request.getResolvedBy());
                record.setResolvedAt(LocalDateTime.now());
                log.info("预警已忽略: id={}, resolvedBy={}", record.getId(), request.getResolvedBy());
            }
            case "ESCALATE" -> {
                // 升级预警级别
                AlertSeverity currentSeverity = record.getSeverity();
                AlertSeverity escalatedSeverity = switch (currentSeverity) {
                    case LOW -> AlertSeverity.MEDIUM;
                    case MEDIUM -> AlertSeverity.HIGH;
                    case HIGH -> AlertSeverity.CRITICAL;
                    case CRITICAL -> AlertSeverity.CRITICAL; // 已经是最高级
                };
                record.setSeverity(escalatedSeverity);
                record.setHandleNote("已升级: " + (request.getNote() != null ? request.getNote() : "需要更高优先级处理"));
                if (request.getResolvedBy() != null) {
                    record.setResolvedBy(request.getResolvedBy());
                }
                log.info("预警已升级: id={}, from={} to={}", record.getId(), currentSeverity, escalatedSeverity);
            }
            default ->
                throw new ValidationException("无效的处理方式: " + action + "，可选值: RESOLVE, DISMISS, ESCALATE");
        }

        return AlertDto.fromEntity(alertRecordRepository.save(record));
    }

    /**
     * 批量处理预警。
     *
     * @param request 批量处理请求
     * @return 处理成功的预警数量
     */
    @Transactional
    public int batchHandleAlerts(AlertHandleRequest request) {
        if (request.getAlertIds() == null || request.getAlertIds().isEmpty()) {
            throw new ValidationException("预警 ID 列表不能为空");
        }

        int count = 0;
        for (UUID alertId : request.getAlertIds()) {
            try {
                AlertHandleRequest singleRequest = new AlertHandleRequest();
                singleRequest.setAlertId(alertId);
                singleRequest.setResolvedBy(request.getResolvedBy());
                singleRequest.setAction(request.getAction());
                singleRequest.setNote(request.getNote());
                handleAlert(singleRequest);
                count++;
            } catch (Exception e) {
                log.warn("批量处理预警失败: id={}, error={}", alertId, e.getMessage());
            }
        }
        log.info("批量处理预警完成: total={}, success={}", request.getAlertIds().size(), count);
        return count;
    }

    /**
     * 更新预警信息（教师补充备注等）。
     *
     * @param id       预警 ID
     * @param note     处理备注
     * @return 更新后的预警 DTO
     */
    @Transactional
    public AlertDto updateAlertNote(UUID id, String note) {
        AlertRecord record = alertRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("预警", id));
        record.setHandleNote(note);
        return AlertDto.fromEntity(alertRecordRepository.save(record));
    }

    // ══════════════════════════════════════════════════════════════
    //  预警统计
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取预警统计信息（用于 Dashboard 展示）。
     *
     * @return 预警统计 DTO
     */
    @Transactional(readOnly = true)
    public AlertStatisticsDto getAlertStatistics() {
        AlertStatisticsDto stats = new AlertStatisticsDto();

        // 未处理总数
        stats.setTotalUnresolved(alertRecordRepository.countByIsResolvedFalse());

        // 今日/本周新增
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.minusDays(7).atStartOfDay();

        stats.setTodayNewCount(alertRecordRepository.countByDateRange(todayStart, LocalDateTime.now()));
        stats.setWeekNewCount(alertRecordRepository.countByDateRange(weekStart, LocalDateTime.now()));

        // 按级别统计
        Map<AlertSeverity, Long> bySeverity = new HashMap<>();
        List<Object[]> severityCounts = alertRecordRepository.countUnresolvedBySeverity();
        for (Object[] row : severityCounts) {
            bySeverity.put((AlertSeverity) row[0], (Long) row[1]);
        }
        // 确保所有级别都有值
        for (AlertSeverity s : AlertSeverity.values()) {
            bySeverity.putIfAbsent(s, 0L);
        }
        stats.setBySeverity(bySeverity);

        // 按类型统计
        Map<AlertType, Long> byType = new HashMap<>();
        List<Object[]> typeCounts = alertRecordRepository.countUnresolvedByType();
        for (Object[] row : typeCounts) {
            byType.put((AlertType) row[0], (Long) row[1]);
        }
        for (AlertType t : AlertType.values()) {
            byType.putIfAbsent(t, 0L);
        }
        stats.setByType(byType);

        // 紧急预警列表（HIGH + CRITICAL）
        List<AlertRecord> urgent = alertRecordRepository.findBySeveritiesAndUnresolved(
                List.of(AlertSeverity.HIGH, AlertSeverity.CRITICAL));
        stats.setUrgentAlerts(urgent.stream().limit(10)
                .map(AlertDto::fromEntity).collect(Collectors.toList()));

        // 处理率
        long totalAll = alertRecordRepository.count();
        long totalResolved = alertRecordRepository.countByIsResolvedFalse();
        stats.setResolutionRate(totalAll > 0
                ? (double) totalResolved / totalAll * 100 : 0);

        // 最近 7 天趋势
        List<AlertStatisticsDto.DailyAlertCount> dailyTrend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            long dayCount = alertRecordRepository.countByDateRange(dayStart, dayEnd);
            dailyTrend.add(new AlertStatisticsDto.DailyAlertCount(
                    day.format(fmt), dayCount, 0));
        }
        stats.setDailyTrend(dailyTrend);

        return stats;
    }

    /**
     * 获取某个学生的预警统计。
     *
     * @param studentId 学生 ID
     * @return 预警统计 DTO
     */
    @Transactional(readOnly = true)
    public AlertStatisticsDto getStudentAlertStatistics(UUID studentId) {
        AlertStatisticsDto stats = new AlertStatisticsDto();

        stats.setTotalUnresolved(
                alertRecordRepository.countByStudentIdAndIsResolvedFalse(studentId));

        // 按级别统计（手动过滤）
        long lowCount = alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId)
                .stream().filter(a -> a.getSeverity() == AlertSeverity.LOW).count();
        long mediumCount = alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId)
                .stream().filter(a -> a.getSeverity() == AlertSeverity.MEDIUM).count();
        long highCount = alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId)
                .stream().filter(a -> a.getSeverity() == AlertSeverity.HIGH).count();
        long criticalCount = alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId)
                .stream().filter(a -> a.getSeverity() == AlertSeverity.CRITICAL).count();

        Map<AlertSeverity, Long> bySeverity = new HashMap<>();
        bySeverity.put(AlertSeverity.LOW, lowCount);
        bySeverity.put(AlertSeverity.MEDIUM, mediumCount);
        bySeverity.put(AlertSeverity.HIGH, highCount);
        bySeverity.put(AlertSeverity.CRITICAL, criticalCount);
        stats.setBySeverity(bySeverity);

        // 紧急预警
        List<AlertRecord> urgent = alertRecordRepository
                .findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId)
                .stream()
                .filter(a -> a.getSeverity() == AlertSeverity.HIGH
                        || a.getSeverity() == AlertSeverity.CRITICAL)
                .limit(5)
                .collect(Collectors.toList());
        stats.setUrgentAlerts(urgent.stream().map(AlertDto::fromEntity).collect(Collectors.toList()));

        return stats;
    }

    // ══════════════════════════════════════════════════════════════
    //  清理过期预警
    // ══════════════════════════════════════════════════════════════

    /**
     * 清理过期的预警记录。
     * <p>
     * 将已过期的未处理预警自动标记为已解决（过期关闭），
     * 适合作为定时任务定期执行。
     * </p>
     *
     * @return 清理的预警数量
     */
    @Transactional
    public int cleanExpiredAlerts() {
        List<AlertRecord> expired = alertRecordRepository.findActiveAlerts(LocalDateTime.now());
        int count = 0;
        for (AlertRecord record : expired) {
            if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(LocalDateTime.now())) {
                record.setIsResolved(true);
                record.setHandleNote("预警已过期，自动关闭");
                record.setResolvedAt(LocalDateTime.now());
                alertRecordRepository.save(record);
                count++;
            }
        }
        if (count > 0) {
            log.info("清理过期预警: count={}", count);
        }
        return count;
    }
}
