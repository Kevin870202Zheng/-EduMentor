package com.edumentor.diagnosis.service;

import com.edumentor.diagnosis.dto.*;
import com.edumentor.diagnosis.repository.AnswerRecordRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.diagnosis.repository.StudentProfileRepository;
import com.edumentor.diagnosis.repository.StudySessionRepository;
import com.edumentor.record.entity.AnswerRecord;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学情诊断服务 — 提供诊断分析、认知画像、雷达图、热力图等核心业务逻辑。
 *
 * <h3>核心功能</h3>
 * <ol>
 *   <li><b>诊断分析</b>：基于作答记录计算学生整体学情，识别薄弱/优势知识点</li>
 *   <li><b>认知画像</b>：构建学生各知识维度的掌握度画像</li>
 *   <li><b>雷达图</b>：生成 6 大维度的雷达图数据，直观展示能力分布</li>
 *   <li><b>热力图</b>：按天统计学习强度，生成学习热力图</li>
 * </ol>
 *
 * <h3>数据来源</h3>
 * <ul>
 *   <li>作答记录表 (answer_records) — 核心分析数据源</li>
 *   <li>知识点表 (knowledge_points) — 知识点元数据</li>
 *   <li>学习会话表 (study_sessions) — 热力图补充数据</li>
 *   <li>学生档案表 (student_profiles) — 缓存画像结果</li>
 * </ul>
 */
@Service
public class DiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);

    /** 薄弱知识点掌握度阈值 */
    private static final BigDecimal WEAK_THRESHOLD = new BigDecimal("0.50");

    /** 已掌握知识点掌握度阈值 */
    private static final BigDecimal MASTERED_THRESHOLD = new BigDecimal("0.80");

    /** 热力图默认回溯天数 */
    private static final int HEATMAP_DEFAULT_DAYS = 30;

    /** 热力图最大回溯天数 */
    private static final int HEATMAP_MAX_DAYS = 365;

    /** 诊断 TOP N 数量 */
    private static final int TOP_N = 5;

    /** 雷达图维度名称及对应颜色 */
    private static final List<RadarDimensionDef> RADAR_DIMENSIONS = List.of(
            new RadarDimensionDef("基础知识", "#1890FF"),
            new RadarDimensionDef("概念理解", "#52C41A"),
            new RadarDimensionDef("应用分析", "#FAAD14"),
            new RadarDimensionDef("综合推理", "#F5222D"),
            new RadarDimensionDef("计算能力", "#722ED1"),
            new RadarDimensionDef("解题速度", "#13C2C2")
    );

    private record RadarDimensionDef(String name, String color) {}

    private final AnswerRecordRepository answerRecordRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final StudySessionRepository studySessionRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;

    public DiagnosisService(AnswerRecordRepository answerRecordRepository,
                            KnowledgePointRepository knowledgePointRepository,
                            StudySessionRepository studySessionRepository,
                            StudentProfileRepository studentProfileRepository,
                            UserRepository userRepository) {
        this.answerRecordRepository = answerRecordRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.studySessionRepository = studySessionRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
    }

    // ══════════════════════════════════════════════════════════════
    //  1. 诊断分析
    // ══════════════════════════════════════════════════════════════

    /**
     * 执行学情诊断分析。
     *
     * @param studentId 学生 ID
     * @param courseId  课程 ID（可选，为空则分析所有课程）
     * @param daysBack  回溯天数
     * @return 诊断分析结果
     */
    @Transactional(readOnly = true)
    public DiagnosisResponse diagnose(UUID studentId, UUID courseId, int daysBack) {
        log.info("开始学情诊断: studentId={}, courseId={}, daysBack={}", studentId, courseId, daysBack);

        // 校验学生存在
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在: " + studentId));

        // 计算时间范围
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(daysBack);

        // 查询作答记录（按课程过滤）
        List<AnswerRecord> records;
        if (courseId != null) {
            records = answerRecordRepository.findByStudentIdAndCourseIdAndAttemptedAtBetween(
                    studentId, courseId, startTime, endTime);
        } else {
            records = answerRecordRepository.findByStudentIdAndAttemptedAtBetween(
                    studentId, startTime, endTime);
        }

        if (records.isEmpty()) {
            log.warn("在指定时间范围内无作答记录: studentId={}, daysBack={}", studentId, daysBack);
            return buildEmptyDiagnosisResponse(studentId, student.getDisplayName());
        }

        // --- 基础统计 ---
        long totalQuestions = records.size();
        long correctCount = records.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
        long totalTimeSpent = records.stream()
                .mapToLong(r -> r.getTimeSpentSeconds() != null ? r.getTimeSpentSeconds() : 0)
                .sum();

        BigDecimal accuracyRate = totalQuestions > 0
                ? BigDecimal.valueOf(correctCount)
                        .divide(BigDecimal.valueOf(totalQuestions), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal avgTimePerQuestion = totalQuestions > 0
                ? BigDecimal.valueOf(totalTimeSpent)
                        .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // --- 按知识点聚合 ---
        List<Object[]> kpAggregations;
        if (courseId != null) {
            kpAggregations = answerRecordRepository.aggregateByKnowledgePointAndCourse(
                    studentId, courseId, startTime, endTime);
        } else {
            kpAggregations = answerRecordRepository.aggregateByKnowledgePoint(
                    studentId, startTime, endTime);
        }

        List<KnowledgeMasteryDTO> kpMasteries = buildKnowledgeMasteries(kpAggregations);

        // --- 计算知识覆盖率 ---
        long totalKpInScope = countTotalKnowledgePoints(courseId);
        long coveredKpCount = kpMasteries.size();
        BigDecimal knowledgeCoverage = totalKpInScope > 0
                ? BigDecimal.valueOf(coveredKpCount)
                        .divide(BigDecimal.valueOf(totalKpInScope), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // --- 识别薄弱和优势知识点 ---
        List<KnowledgeMasteryDTO> weakKps = kpMasteries.stream()
                .filter(km -> km.getMasteryLevel().compareTo(WEAK_THRESHOLD) < 0)
                .sorted(Comparator.comparing(KnowledgeMasteryDTO::getMasteryLevel))
                .limit(TOP_N)
                .toList();

        List<KnowledgeMasteryDTO> strongKps = kpMasteries.stream()
                .filter(km -> km.getMasteryLevel().compareTo(MASTERED_THRESHOLD) >= 0)
                .sorted(Comparator.comparing(KnowledgeMasteryDTO::getMasteryLevel).reversed())
                .limit(TOP_N)
                .toList();

        // --- 近期趋势 ---
        List<DiagnosisResponse.DailyAccuracy> recentTrend = computeRecentTrend(records, daysBack);

        // --- 诊断总结与建议 ---
        String summary = generateDiagnosisSummary(accuracyRate, weakKps.size(), kpMasteries.size());
        List<String> recommendations = generateRecommendations(weakKps, accuracyRate);

        log.info("学情诊断完成: studentId={}, totalQuestions={}, accuracy={}",
                studentId, totalQuestions, accuracyRate);

        return DiagnosisResponse.builder()
                .studentId(studentId)
                .studentName(student.getDisplayName())
                .totalQuestions(totalQuestions)
                .correctCount(correctCount)
                .accuracyRate(accuracyRate)
                .totalTimeSpentSec(totalTimeSpent)
                .avgTimePerQuestion(avgTimePerQuestion)
                .knowledgeCoverage(knowledgeCoverage)
                .weakKpCount(weakKps.size())
                .strongKpCount(strongKps.size())
                .topWeakKps(weakKps)
                .topStrongKps(strongKps)
                .recentTrend(recentTrend)
                .diagnosisSummary(summary)
                .recommendations(recommendations)
                .analysisDate(LocalDate.now())
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 认知画像
    // ══════════════════════════════════════════════════════════════

    /**
     * 构建学生认知画像。
     *
     * @param studentId 学生 ID
     * @return 认知画像
     */
    @Transactional(readOnly = true)
    public CognitiveProfile buildCognitiveProfile(UUID studentId) {
        log.info("开始构建认知画像: studentId={}", studentId);

        // 校验学生
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在: " + studentId));

        // 获取全量作答聚合数据（所有时间）
        List<Object[]> kpAggregations = answerRecordRepository.aggregateByKnowledgePointAll(studentId);

        List<KnowledgeMasteryDTO> kpMasteries = buildKnowledgeMasteries(kpAggregations);

        // --- 统计各掌握等级 ---
        int masteredCount = (int) kpMasteries.stream()
                .filter(km -> km.getMasteryLevel().compareTo(MASTERED_THRESHOLD) >= 0)
                .count();

        int weakCount = (int) kpMasteries.stream()
                .filter(km -> km.getMasteryLevel().compareTo(WEAK_THRESHOLD) < 0)
                .count();

        int learningCount = kpMasteries.size() - masteredCount - weakCount;

        // --- 整体掌握度 ---
        BigDecimal overallMastery = kpMasteries.isEmpty() ? BigDecimal.ZERO
                : kpMasteries.stream()
                        .map(KnowledgeMasteryDTO::getMasteryLevel)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(kpMasteries.size()), 4, RoundingMode.HALF_UP);

        // --- 雷达图数据 ---
        List<CognitiveProfile.RadarDimension> radarData = buildRadarDimensions(kpMasteries);

        // --- 画像总结 ---
        String summary = buildProfileSummary(student.getDisplayName(), overallMastery,
                masteredCount, learningCount, weakCount);

        log.info("认知画像构建完成: studentId={}, kpCount={}, overallMastery={}",
                studentId, kpMasteries.size(), overallMastery);

        return CognitiveProfile.builder()
                .overallMasteryLevel(overallMastery)
                .totalKpCount((int) kpMasteries.stream()
                        .map(KnowledgeMasteryDTO::getKnowledgePointId).distinct().count())
                .masteredKpCount(masteredCount)
                .learningKpCount(learningCount)
                .weakKpCount(weakCount)
                .knowledgeMasteries(kpMasteries)
                .radarChartData(radarData)
                .summary(summary)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 雷达图
    // ══════════════════════════════════════════════════════════════

    /**
     * 生成雷达图数据。
     *
     * @param studentId 学生 ID
     * @return 雷达图数据
     */
    @Transactional(readOnly = true)
    public RadarChartData generateRadarChart(UUID studentId) {
        log.info("生成雷达图数据: studentId={}", studentId);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在: " + studentId));

        // 获取知识点的按类掌握度
        List<Object[]> kpAggregations = answerRecordRepository.aggregateByKnowledgePointAll(studentId);
        List<KnowledgeMasteryDTO> kpMasteries = buildKnowledgeMasteries(kpAggregations);

        // 将知识点分到 6 大雷达维度
        List<RadarChartData.Dimension> dimensions = assignToRadarDimensions(kpMasteries);

        // 综合评分
        BigDecimal overallScore = dimensions.isEmpty() ? BigDecimal.ZERO
                : dimensions.stream()
                        .map(RadarChartData.Dimension::getValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(dimensions.size()), 2, RoundingMode.HALF_UP);

        log.info("雷达图数据生成完成: studentId={}, dimensions={}", studentId, dimensions.size());

        return RadarChartData.builder()
                .studentId(studentId)
                .studentName(student.getDisplayName())
                .dimensions(dimensions)
                .overallScore(overallScore)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 热力图
    // ══════════════════════════════════════════════════════════════

    /**
     * 生成学习热力图数据。
     *
     * @param studentId 学生 ID
     * @param daysBack  回溯天数
     * @return 热力图数据
     */
    @Transactional(readOnly = true)
    public HeatMapData generateHeatMap(UUID studentId, int daysBack) {
        log.info("生成学习热力图: studentId={}, daysBack={}", studentId, daysBack);

        int safeDaysBack = daysBack <= 0 ? HEATMAP_DEFAULT_DAYS
                : Math.min(daysBack, HEATMAP_MAX_DAYS);

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(safeDaysBack);
        LocalDate startDate = startTime.toLocalDate();
        LocalDate endDate = endTime.toLocalDate();

        // 获取日统计数据
        List<Object[]> dailyRecords = answerRecordRepository.dailyAggregate(
                studentId, startTime, endTime);
        List<Object[]> dailySessions = studySessionRepository.dailySessionAggregate(
                studentId, startTime, endTime);

        // 构建日期 -> HeatMapDay 映射
        Map<LocalDate, HeatMapData.HeatMapDay> dayMap = new LinkedHashMap<>();

        // 初始化所有日期
        for (int i = 0; i <= safeDaysBack; i++) {
            LocalDate date = startDate.plusDays(i);
            dayMap.put(date, HeatMapData.HeatMapDay.builder()
                    .date(date)
                    .questionCount(0)
                    .correctCount(0)
                    .accuracyRate(BigDecimal.ZERO)
                    .durationMinutes(0)
                    .focusScore(BigDecimal.ZERO)
                    .kpCovered(0)
                    .build());
        }

        // 合并作答统计数据（dailyAggregate 返回: [date, totalCount, correctCount]）
        for (Object[] row : dailyRecords) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            long total = ((Number) row[1]).longValue();
            long correct = ((Number) row[2]).longValue();

            HeatMapData.HeatMapDay existing = dayMap.get(date);
            if (existing != null) {
                BigDecimal accuracy = total > 0
                        ? BigDecimal.valueOf(correct).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                dayMap.put(date, HeatMapData.HeatMapDay.builder()
                        .date(date)
                        .questionCount((int) total)
                        .correctCount((int) correct)
                        .accuracyRate(accuracy)
                        .durationMinutes(existing.getDurationMinutes())
                        .focusScore(existing.getFocusScore())
                        .kpCovered(existing.getKpCovered())
                        .build());
            }
        }

        // 合并学习会话数据（dailySessionAggregate 返回: [date, sumDuration, count, avgFocus]）
        for (Object[] row : dailySessions) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            long totalDurationSec = ((Number) row[1]).longValue();
            // row[2] = session count, skip
            double avgFocus = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

            HeatMapData.HeatMapDay existing = dayMap.get(date);
            if (existing != null) {
                int durationMin = (int) (totalDurationSec / 60);
                dayMap.put(date, HeatMapData.HeatMapDay.builder()
                        .date(date)
                        .questionCount(existing.getQuestionCount())
                        .correctCount(existing.getCorrectCount())
                        .accuracyRate(existing.getAccuracyRate())
                        .durationMinutes(durationMin)
                        .focusScore(BigDecimal.valueOf(avgFocus).setScale(2, RoundingMode.HALF_UP))
                        .kpCovered(existing.getKpCovered())
                        .build());
            }
        }

        // 计算活跃天数（有答题记录的天数）
        int activeDays = (int) dayMap.values().stream()
                .filter(d -> d.getQuestionCount() > 0)
                .count();

        List<HeatMapData.HeatMapDay> heatData = new ArrayList<>(dayMap.values());

        log.info("学习热力图生成完成: studentId={}, totalDays={}, activeDays={}",
                studentId, dayMap.size(), activeDays);

        return HeatMapData.builder()
                .studentId(studentId)
                .startDate(startDate)
                .endDate(endDate)
                .totalDays(dayMap.size())
                .activeDays(activeDays)
                .heatData(heatData)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    //  私有辅助方法
    // ══════════════════════════════════════════════════════════════

    /**
     * 从聚合结果构建知识点掌握度列表。
     * <p>
     * 聚合结果每行格式: [kpId (UUID), totalCount (long), correctCount (long)]
     * </p>
     */
    private List<KnowledgeMasteryDTO> buildKnowledgeMasteries(List<Object[]> aggregations) {
        if (aggregations == null || aggregations.isEmpty()) {
            return List.of();
        }

        return aggregations.stream()
                .map(row -> {
                    UUID kpId = (UUID) row[0];
                    long totalCount = ((Number) row[1]).longValue();
                    long correctCount = ((Number) row[2]).longValue();

                    // 尝试获取知识点名称（从第三个位置之后如果有额外信息）
                    String kpName = "未知知识点";
                    if (row.length > 3 && row[3] != null) {
                        kpName = (String) row[3];
                    }

                    BigDecimal masteryLevel = totalCount > 0
                            ? BigDecimal.valueOf(correctCount)
                                    .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    return KnowledgeMasteryDTO.builder()
                            .knowledgePointId(kpId)
                            .knowledgePointName(kpName)
                            .masteryLevel(masteryLevel)
                            .totalAttempts((int) totalCount)
                            .correctCount((int) correctCount)
                            .lastAttemptedAt(null) // 由调用方补充
                            .build();
                })
                .sorted(Comparator.comparing(KnowledgeMasteryDTO::getMasteryLevel))
                .toList();
    }

    /**
     * 构建空诊断结果（无作答记录时）。
     */
    private DiagnosisResponse buildEmptyDiagnosisResponse(UUID studentId, String studentName) {
        return DiagnosisResponse.builder()
                .studentId(studentId)
                .studentName(studentName)
                .totalQuestions(0L)
                .correctCount(0L)
                .accuracyRate(BigDecimal.ZERO)
                .totalTimeSpentSec(0L)
                .avgTimePerQuestion(BigDecimal.ZERO)
                .knowledgeCoverage(BigDecimal.ZERO)
                .weakKpCount(0)
                .strongKpCount(0)
                .topWeakKps(List.of())
                .topStrongKps(List.of())
                .recentTrend(List.of())
                .diagnosisSummary("暂无足够的学习数据，请先完成一些练习题目。")
                .recommendations(List.of("开始作答题目以获得学情诊断分析"))
                .analysisDate(LocalDate.now())
                .build();
    }

    /**
     * 计算近期正确率趋势（最多 7 天）。
     */
    private List<DiagnosisResponse.DailyAccuracy> computeRecentTrend(
            List<AnswerRecord> records, int daysBack) {
        int trendDays = Math.min(daysBack, 7);
        LocalDate today = LocalDate.now();

        // 按日期分组
        Map<LocalDate, List<AnswerRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getAttemptedAt().toLocalDate()));

        List<DiagnosisResponse.DailyAccuracy> trend = new ArrayList<>();
        for (int i = trendDays - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<AnswerRecord> dayRecords = byDate.getOrDefault(date, List.of());
            if (dayRecords.isEmpty()) {
                trend.add(DiagnosisResponse.DailyAccuracy.builder()
                        .date(date)
                        .accuracy(null)
                        .count(0)
                        .build());
            } else {
                long correct = dayRecords.stream()
                        .filter(r -> Boolean.TRUE.equals(r.getIsCorrect()))
                        .count();
                BigDecimal accuracy = BigDecimal.valueOf(correct)
                        .divide(BigDecimal.valueOf(dayRecords.size()), 4, RoundingMode.HALF_UP);
                trend.add(DiagnosisResponse.DailyAccuracy.builder()
                        .date(date)
                        .accuracy(accuracy)
                        .count(dayRecords.size())
                        .build());
            }
        }
        return trend;
    }

    /**
     * 生成诊断总结文本。
     */
    private String generateDiagnosisSummary(
            BigDecimal accuracyRate, int weakCount, int totalKpCount) {
        if (totalKpCount == 0) {
            return "暂无学情数据，建议开始练习。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前整体正确率为 ")
                .append(accuracyRate.multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP))
                .append("%");

        if (accuracyRate.compareTo(new BigDecimal("0.80")) >= 0) {
            sb.append("，表现优秀，已掌握大部分知识点。");
        } else if (accuracyRate.compareTo(new BigDecimal("0.60")) >= 0) {
            sb.append("，基础较好，部分知识点有待加强。");
        } else if (accuracyRate.compareTo(new BigDecimal("0.40")) >= 0) {
            sb.append("，处于中等水平，建议重点关注薄弱环节。");
        } else {
            sb.append("，需要加大练习力度，系统性地巩固基础知识。");
        }

        if (weakCount > 0) {
            sb.append(" 共有 ").append(weakCount)
                    .append(" 个薄弱知识点需要重点复习。");
        }

        return sb.toString();
    }

    /**
     * 生成学习建议列表。
     */
    private List<String> generateRecommendations(
            List<KnowledgeMasteryDTO> weakKps, BigDecimal accuracyRate) {
        List<String> recommendations = new ArrayList<>();

        if (weakKps.isEmpty()) {
            recommendations.add("表现优秀，建议挑战更高难度的题目以持续提升。");
            return recommendations;
        }

        recommendations.add("优先复习以下薄弱知识点：");
        for (KnowledgeMasteryDTO weak : weakKps) {
            recommendations.add("- " + weak.getKnowledgePointName()
                    + "（掌握度：" + weak.getMasteryLevel().multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP) + "%）");
        }

        if (accuracyRate.compareTo(new BigDecimal("0.50")) < 0) {
            recommendations.add("建议从最基础的知识点开始，循序渐进地学习。");
        }

        recommendations.add("建议每天安排固定时间进行知识点专项练习。");
        recommendations.add("利用错题复盘功能定期回顾和巩固易错知识点。");

        return recommendations;
    }

    /**
     * 构建雷达图维度数据（6大维度）。
     */
    private List<CognitiveProfile.RadarDimension> buildRadarDimensions(
            List<KnowledgeMasteryDTO> kpMasteries) {
        if (kpMasteries.isEmpty()) {
            return RADAR_DIMENSIONS.stream()
                    .map(def -> CognitiveProfile.RadarDimension.builder()
                            .dimension(def.name())
                            .value(BigDecimal.ZERO)
                            .maxValue(BigDecimal.valueOf(100))
                            .build())
                    .toList();
        }

        // 将知识点轮播分配到 6 个维度
        int perDimension = Math.max(1, kpMasteries.size() / RADAR_DIMENSIONS.size());
        List<CognitiveProfile.RadarDimension> dimensions = new ArrayList<>();

        for (int i = 0; i < RADAR_DIMENSIONS.size(); i++) {
            int start = i * perDimension;
            int end = Math.min(start + perDimension, kpMasteries.size());
            if (start >= kpMasteries.size()) {
                dimensions.add(CognitiveProfile.RadarDimension.builder()
                        .dimension(RADAR_DIMENSIONS.get(i).name())
                        .value(BigDecimal.ZERO)
                        .maxValue(BigDecimal.valueOf(100))
                        .build());
                continue;
            }

            List<KnowledgeMasteryDTO> slice = kpMasteries.subList(start, end);
            BigDecimal avgValue = slice.stream()
                    .map(km -> km.getMasteryLevel().multiply(BigDecimal.valueOf(100)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(slice.size()), 2, RoundingMode.HALF_UP);

            dimensions.add(CognitiveProfile.RadarDimension.builder()
                    .dimension(RADAR_DIMENSIONS.get(i).name())
                    .value(avgValue)
                    .maxValue(BigDecimal.valueOf(100))
                    .build());
        }

        return dimensions;
    }

    /**
     * 将知识点分配到雷达图维度（带颜色）。
     */
    private List<RadarChartData.Dimension> assignToRadarDimensions(
            List<KnowledgeMasteryDTO> kpMasteries) {
        if (kpMasteries.isEmpty()) {
            return RADAR_DIMENSIONS.stream()
                    .map(def -> RadarChartData.Dimension.builder()
                            .name(def.name())
                            .value(BigDecimal.ZERO)
                            .maxValue(BigDecimal.valueOf(100))
                            .color(def.color())
                            .build())
                    .toList();
        }

        int perDimension = Math.max(1, kpMasteries.size() / RADAR_DIMENSIONS.size());
        List<RadarChartData.Dimension> dimensions = new ArrayList<>();

        for (int i = 0; i < RADAR_DIMENSIONS.size(); i++) {
            RadarDimensionDef def = RADAR_DIMENSIONS.get(i);
            int start = i * perDimension;
            int end = Math.min(start + perDimension, kpMasteries.size());
            if (start >= kpMasteries.size()) {
                dimensions.add(RadarChartData.Dimension.builder()
                        .name(def.name())
                        .value(BigDecimal.ZERO)
                        .maxValue(BigDecimal.valueOf(100))
                        .color(def.color())
                        .build());
                continue;
            }

            List<KnowledgeMasteryDTO> slice = kpMasteries.subList(start, end);
            BigDecimal avgValue = slice.stream()
                    .map(km -> km.getMasteryLevel().multiply(BigDecimal.valueOf(100)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(slice.size()), 2, RoundingMode.HALF_UP);

            dimensions.add(RadarChartData.Dimension.builder()
                    .name(def.name())
                    .value(avgValue)
                    .maxValue(BigDecimal.valueOf(100))
                    .color(def.color())
                    .build());
        }

        return dimensions;
    }

    /**
     * 构建画像总结文本。
     */
    private String buildProfileSummary(String studentName, BigDecimal overallMastery,
                                       int masteredCount, int learningCount, int weakCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(studentName).append("同学，");

        double pct = overallMastery.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP).doubleValue();

        if (pct >= 80) {
            sb.append("整体学习状态优秀");
        } else if (pct >= 60) {
            sb.append("整体学习状态良好");
        } else if (pct >= 40) {
            sb.append("整体学习状态一般，有较大提升空间");
        } else {
            sb.append("整体学习状态需重点关注");
        }

        sb.append("（综合掌握度 ").append(pct).append("%）。");
        sb.append("已掌握 ").append(masteredCount).append(" 个知识点，");
        sb.append("学习中 ").append(learningCount).append(" 个，");
        sb.append("薄弱 ").append(weakCount).append(" 个。");

        if (weakCount > 0) {
            sb.append("建议优先攻克薄弱知识点，再巩固已学内容。");
        }

        return sb.toString();
    }

    /**
     * 统计指定课程下的知识点总数。如果 courseId 为空，返回 0。
     */
    private long countTotalKnowledgePoints(UUID courseId) {
        if (courseId == null) {
            return 0;
        }
        return knowledgePointRepository.countByCourseId(courseId);
    }
}
