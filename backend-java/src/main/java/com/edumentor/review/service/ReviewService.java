package com.edumentor.review.service;

import com.edumentor.review.dto.*;
import com.edumentor.review.entity.ErrorRecord;
import com.edumentor.review.entity.ReviewRecord;
import com.edumentor.entity.enums.ErrorType;
import com.edumentor.review.entity.enums.ReviewStatus;
import com.edumentor.entity.enums.ReviewType;
import com.edumentor.review.repository.ErrorRecordRepository;
import com.edumentor.review.repository.ReviewRecordRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 错题复盘服务 — 提供错题 CRUD、错题分析、复习计划生成、
 * 艾宾浩斯遗忘曲线排程、进度更新等核心业务逻辑。
 *
 * <h3>艾宾浩斯复习间隔（天）</h3>
 * <pre>
 *   第 1 次 → 1 天后
 *   第 2 次 → 3 天后
 *   第 3 次 → 7 天后
 *   第 4 次 → 14 天后
 *   第 5 次 → 30 天后
 *   第 6 次 → 60 天后
 *   第 7 次 → 120 天后
 * </pre>
 *
 * <h3>核心功能</h3>
 * <ol>
 *   <li>错题 CRUD — 记录、更新分析、标记已复习、删除</li>
 *   <li>错题分析 — 统计薄弱知识点、错因分布、正确率趋势</li>
 *   <li>复习记录 CRUD — 创建、更新、删除、查询</li>
 *   <li>进度更新 — 标记完成/跳过，自动计算下次排程</li>
 *   <li>复习计划生成 — 基于待复习列表生成每日计划</li>
 *   <li>补充排程 — 为新错题补充复习任务</li>
 *   <li>重新排程 — 调整复习计划的日期</li>
 *   <li>统计查询 — 聚合错题与复习的多维度统计</li>
 * </ol>
 *
 * @author EduMentor Team
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** 艾宾浩斯复习间隔（天）：第 N 次复习后的间隔 */
    private static final int[] EBBINGHAUS_INTERVALS = {1, 3, 7, 14, 30, 60, 120};

    private final ErrorRecordRepository errorRecordRepository;
    private final ReviewRecordRepository reviewRecordRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ReviewService(ErrorRecordRepository errorRecordRepository,
                         ReviewRecordRepository reviewRecordRepository) {
        this.errorRecordRepository = errorRecordRepository;
        this.reviewRecordRepository = reviewRecordRepository;
    }


    // ══════════════════════════════════════════════════════════════
    //  1. 错题 CRUD
    // ══════════════════════════════════════════════════════════════

    /**
     * 记录错题 — 将作答错误的题目记录到错题本。
     *
     * @param studentId        学生 ID
     * @param questionId       题目 ID
     * @param knowledgePointId 知识点 ID
     * @param knowledgePointName 知识点名称
     * @param questionContent  题目内容
     * @param studentAnswer    学生作答
     * @param correctAnswer    正确答案
     * @param errorType        错因类型（可选，后续可分析补充）
     * @param difficulty       难度（1-5，默认 3）
     * @return 创建的错题记录
     */
    @Transactional
    public ErrorRecord recordError(UUID studentId, UUID questionId,
                                   UUID knowledgePointId, String knowledgePointName,
                                   String questionContent, String studentAnswer,
                                   String correctAnswer, ErrorType errorType,
                                   Integer difficulty) {
        ErrorRecord record = new ErrorRecord();
        record.setStudentId(studentId);
        record.setQuestionId(questionId);
        record.setKnowledgePointId(knowledgePointId);
        record.setKnowledgePointName(knowledgePointName);
        record.setQuestionContent(questionContent);
        record.setStudentAnswer(studentAnswer);
        record.setCorrectAnswer(correctAnswer);
        record.setErrorType(errorType);
        record.setDifficulty(difficulty != null ? difficulty : 3);
        record.setIsReviewed(false);
        record.setErrorCount(1);
        ErrorRecord saved = errorRecordRepository.save(record);
        log.info("Error recorded: student={}, question={}, kp={}, errorType={}",
                studentId, questionId, knowledgePointId, errorType);
        return saved;
    }

    /**
     * 根据 ID 查询错题记录。
     *
     * @param errorRecordId 错题记录 ID
     * @return 错题记录
     * @throws IllegalArgumentException 如果记录不存在
     */
    @Transactional(readOnly = true)
    public ErrorRecord getErrorById(UUID errorRecordId) {
        return errorRecordRepository.findById(errorRecordId)
                .orElseThrow(() -> new IllegalArgumentException("错题记录不存在: " + errorRecordId));
    }

    /**
     * 分页查询某学生的错题记录。
     *
     * @param studentId 学生 ID
     * @param page      页码（1-based）
     * @param size      每页大小
     * @return 错题记录列表
     */
    @Transactional(readOnly = true)
    public List<ErrorRecord> getErrorRecords(UUID studentId, int page, int size) {
        return errorRecordRepository.findByStudentId(studentId)
                .stream()
                .sorted(Comparator.comparing(ErrorRecord::getCreatedAt).reversed())
                .skip((long) (page - 1) * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    /**
     * 查询某学生未复习的错题记录。
     *
     * @param studentId 学生 ID
     * @return 未复习的错题记录列表
     */
    @Transactional(readOnly = true)
    public List<ErrorRecord> getUnreviewedErrors(UUID studentId) {
        return errorRecordRepository.findByStudentIdAndIsReviewedFalse(studentId);
    }

    /**
     * 更新错题分析结果 — 设置错因分类、分析内容和复习建议。
     *
     * @param errorRecordId   错题记录 ID
     * @param errorType       错因分类
     * @param errorAnalysis   分析内容
     * @param reviewSuggestion 复习建议
     * @return 更新后的错题记录
     */
    @Transactional
    public ErrorRecord updateErrorAnalysis(UUID errorRecordId, ErrorType errorType,
                                           String errorAnalysis, String reviewSuggestion) {
        ErrorRecord record = getErrorById(errorRecordId);
        record.setErrorType(errorType);
        record.setErrorAnalysis(errorAnalysis);
        record.setReviewSuggestion(reviewSuggestion);
        errorRecordRepository.save(record);
        log.info("Error analysis updated: id={}, errorType={}", errorRecordId, errorType);
        return record;
    }

    /**
     * 标记错题为已复习。
     *
     * @param errorRecordId  错题记录 ID
     * @param reviewAccuracy 复习正确率（可选）
     * @param notes          复习笔记
     * @return 更新后的错题记录
     */
    @Transactional
    public ErrorRecord markErrorReviewed(UUID errorRecordId, BigDecimal reviewAccuracy, String notes) {
        ErrorRecord record = getErrorById(errorRecordId);
        record.setIsReviewed(true);
        record.setReviewAccuracy(reviewAccuracy);
        errorRecordRepository.save(record);
        log.info("Error marked as reviewed: id={}, accuracy={}", errorRecordId, reviewAccuracy);
        return record;
    }

    /**
     * 删除错题记录。
     *
     * @param errorRecordId 错题记录 ID
     */
    @Transactional
    public void deleteError(UUID errorRecordId) {
        ErrorRecord record = getErrorById(errorRecordId);
        errorRecordRepository.delete(record);
        log.info("Error deleted: id={}", errorRecordId);
    }

    /**
     * 统计某学生的错题总数。
     *
     * @param studentId 学生 ID
     * @return 错题总数
     */
    @Transactional(readOnly = true)
    public long countErrors(UUID studentId) {
        return errorRecordRepository.countByStudentId(studentId);
    }


    // ══════════════════════════════════════════════════════════════
    //  2. 错题分析
    // ══════════════════════════════════════════════════════════════

    /**
     * 执行错题分析 — 统计薄弱知识点、错因分布、正确率趋势。
     * <p>
     * 使用 EntityManager 进行跨模块查询（如从 course 模块获取知识点名称）。
     * </p>
     *
     * @param studentId 学生 ID
     * @return 错题分析结果 DTO
     */
    @Transactional(readOnly = true)
    public ErrorAnalysisDto analyzeErrors(UUID studentId) {
        log.info("Analyzing errors for student: {}", studentId);
        ErrorAnalysisDto dto = new ErrorAnalysisDto();
        dto.setStudentId(studentId);
        dto.setAnalysisTimestamp(LocalDateTime.now());

        // 1. 基本统计
        long totalErrors = errorRecordRepository.countByStudentId(studentId);
        long unreviewedCount = errorRecordRepository.countByStudentIdAndIsReviewed(studentId, false);
        dto.setTotalErrors(totalErrors);
        dto.setUnreviewedCount(unreviewedCount);

        // 2. 薄弱知识点分析（按错题频次降序）
        List<Object[]> kpFrequency = errorRecordRepository.findTopErrorKnowledgePointsByFrequency(studentId);
        List<ErrorAnalysisDto.WeakKnowledgePoint> weakKps = new ArrayList<>();
        for (Object[] row : kpFrequency) {
            UUID kpId = (UUID) row[0];
            long errorCount = ((Number) row[1]).longValue();
            ErrorAnalysisDto.WeakKnowledgePoint wk = new ErrorAnalysisDto.WeakKnowledgePoint();
            wk.setKnowledgePointId(kpId);
            wk.setKnowledgePointName(resolveKnowledgePointName(kpId));
            wk.setErrorCount(errorCount);
            // 查询该知识点未复习数
            List<ErrorRecord> kpErrors = errorRecordRepository
                    .findByStudentIdAndKnowledgePointId(studentId, kpId);
            long unreviewedKp = kpErrors.stream().filter(e -> !Boolean.TRUE.equals(e.getIsReviewed())).count();
            wk.setUnreviewedCount(unreviewedKp);
            wk.setErrorRate(totalErrors > 0
                    ? BigDecimal.valueOf(errorCount * 100.0 / totalErrors)
                            .setScale(1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0);
            weakKps.add(wk);
        }
        dto.setWeakKnowledgePoints(weakKps);

        // 3. 错因类型分布
        List<Object[]> typeDist = errorRecordRepository.findErrorTypeDistribution(studentId);
        List<ErrorAnalysisDto.ErrorTypeDistribution> typeList = new ArrayList<>();
        for (Object[] row : typeDist) {
            ErrorType et = (ErrorType) row[0];
            long count = ((Number) row[1]).longValue();
            ErrorAnalysisDto.ErrorTypeDistribution etd = new ErrorAnalysisDto.ErrorTypeDistribution();
            etd.setErrorType(et);
            etd.setCount(count);
            etd.setPercentage(totalErrors > 0
                    ? BigDecimal.valueOf(count * 100.0 / totalErrors)
                            .setScale(1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0);
            typeList.add(etd);
        }
        dto.setErrorTypeDistribution(typeList);

        // 4. 正确率趋势（最近 7 天）
        List<ErrorAnalysisDto.AccuracyTrend> trends = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            ErrorAnalysisDto.AccuracyTrend trend = new ErrorAnalysisDto.AccuracyTrend();
            trend.setDate(date);
            // 查询该日完成的复习记录
            List<ReviewRecord> dailyRecords = reviewRecordRepository
                    .findByStudentIdAndScheduledDate(studentId, date)
                    .stream()
                    .filter(r -> r.getStatus() == ReviewStatus.COMPLETED)
                    .collect(Collectors.toList());
            long total = dailyRecords.size();
            long correct = dailyRecords.stream()
                    .filter(r -> r.getAccuracy() != null && r.getAccuracy().compareTo(BigDecimal.valueOf(60)) >= 0)
                    .count();
            trend.setTotalCount(total);
            trend.setCorrectCount(correct);
            trend.setAccuracy(total > 0
                    ? BigDecimal.valueOf(correct * 100.0 / total)
                            .setScale(1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0);
            trends.add(trend);
        }
        dto.setAccuracyTrend(trends);

        return dto;
    }


    // ══════════════════════════════════════════════════════════════
    //  3. 复习记录 CRUD
    // ══════════════════════════════════════════════════════════════

    /**
     * 创建复习记录 — 手动创建一条复习计划。
     *
     * @param studentId        学生 ID
     * @param knowledgePointId 知识点 ID
     * @param knowledgePointName 知识点名称
     * @param errorRecordId    关联的错题记录 ID（可选）
     * @param reviewType       复习类型
     * @param reviewCycle      复习周期（第几次复习）
     * @param daysUntilReview  距今天数（用于计算 scheduledDate）
     * @return 创建的复习记录
     */
    @Transactional
    public ReviewRecord createReview(UUID studentId, UUID knowledgePointId,
                                     String knowledgePointName, UUID errorRecordId,
                                     ReviewType reviewType, Integer reviewCycle,
                                     int daysUntilReview) {
        ReviewRecord record = new ReviewRecord();
        record.setStudentId(studentId);
        record.setKnowledgePointId(knowledgePointId);
        record.setKnowledgePointName(knowledgePointName);
        record.setErrorRecordId(errorRecordId);
        record.setReviewType(reviewType != null ? reviewType : ReviewType.CUSTOM_REVIEW);
        record.setStatus(ReviewStatus.PENDING);
        record.setReviewCycle(reviewCycle != null ? reviewCycle : 1);
        record.setScheduledDate(LocalDate.now().plusDays(daysUntilReview));
        // 根据艾宾浩斯计算下次复习日期
        int currentCycle = record.getReviewCycle();
        record.setNextReviewDate(calculateNextReviewDate(currentCycle));
        ReviewRecord saved = reviewRecordRepository.save(record);
        log.info("Review created: student={}, kp={}, cycle={}, scheduled={}",
                studentId, knowledgePointId, record.getReviewCycle(), record.getScheduledDate());
        return saved;
    }

    /**
     * 根据 ID 查询复习记录。
     *
     * @param reviewId 复习记录 ID
     * @return 复习记录
     * @throws IllegalArgumentException 如果记录不存在
     */
    @Transactional(readOnly = true)
    public ReviewRecord getReviewById(UUID reviewId) {
        return reviewRecordRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("复习记录不存在: " + reviewId));
    }

    /**
     * 分页查询某学生的复习记录。
     *
     * @param studentId 学生 ID
     * @param page      页码（1-based）
     * @param size      每页大小
     * @return 复习记录列表
     */
    @Transactional(readOnly = true)
    public List<ReviewRecord> getReviewRecords(UUID studentId, int page, int size) {
        return reviewRecordRepository.findByStudentId(studentId)
                .stream()
                .sorted(Comparator.comparing(ReviewRecord::getScheduledDate).reversed())
                .skip((long) (page - 1) * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    /**
     * 查询某学生指定状态的复习记录。
     *
     * @param studentId 学生 ID
     * @param status    复习状态
     * @return 复习记录列表
     */
    @Transactional(readOnly = true)
    public List<ReviewRecord> getReviewsByStatus(UUID studentId, ReviewStatus status) {
        return reviewRecordRepository.findByStudentIdAndStatus(studentId, status);
    }

    /**
     * 更新复习记录。
     *
     * @param reviewId  复习记录 ID
     * @param updateFn  更新函数，接收现有记录并做修改
     * @return 更新后的复习记录
     */
    @Transactional
    public ReviewRecord updateReview(UUID reviewId,
                                     java.util.function.Consumer<ReviewRecord> updateFn) {
        ReviewRecord record = getReviewById(reviewId);
        updateFn.accept(record);
        reviewRecordRepository.save(record);
        return record;
    }

    /**
     * 删除复习记录。
     *
     * @param reviewId 复习记录 ID
     */
    @Transactional
    public void deleteReview(UUID reviewId) {
        ReviewRecord record = getReviewById(reviewId);
        reviewRecordRepository.delete(record);
        log.info("Review deleted: id={}", reviewId);
    }


    // ══════════════════════════════════════════════════════════════
    //  4. 复习进度更新（艾宾浩斯遗忘曲线）
    // ══════════════════════════════════════════════════════════════

    /**
     * 更新复习进度 — 标记完成或跳过，自动计算下次排程。
     * <p>
     * 艾宾浩斯复习间隔（天）：1, 3, 7, 14, 30, 60, 120
     * 每次完成复习后进入下一个周期，并按照对应间隔安排下次复习。
     * 如果正确率达标（≥80%），按标准间隔；否则缩短间隔重试。
     * </p>
     *
     * @param reviewId          复习记录 ID
     * @param status            COMPLETED 或 SKIPPED
     * @param spentMinutes      复习耗时（分钟）
     * @param effectivenessScore 成效自评（1-5）
     * @param notes             复习笔记
     * @param accuracy          本次正确率（0-100）
     * @return 更新后的复习记录；如果生成了下次排程，可通过 {@link ReviewRecord#getNextReviewDate()} 获取
     */
    @Transactional
    public ReviewRecord updateProgress(UUID reviewId, ReviewStatus status,
                                       Integer spentMinutes, Integer effectivenessScore,
                                       String notes, BigDecimal accuracy) {
        ReviewRecord record = getReviewById(reviewId);
        log.info("Updating review progress: id={}, status={}, accuracy={}",
                reviewId, status, accuracy);

        // 更新公共字段
        record.setSpentMinutes(spentMinutes);
        record.setEffectivenessScore(effectivenessScore);
        record.setNotes(notes);
        record.setAccuracy(accuracy);

        if (status == ReviewStatus.COMPLETED) {
            record.setStatus(ReviewStatus.COMPLETED);
            record.setCompletedDate(LocalDate.now());

            // 计算当前周期在艾宾浩斯间隔数组中的索引
            int currentCycle = record.getReviewCycle() != null ? record.getReviewCycle() : 1;
            boolean mastered = accuracy != null && accuracy.compareTo(BigDecimal.valueOf(80)) >= 0;

            if (mastered) {
                // 掌握良好 → 进入下一个周期
                int nextCycle = Math.min(currentCycle + 1, EBBINGHAUS_INTERVALS.length);
                record.setReviewCycle(nextCycle);
                record.setNextReviewDate(calculateNextReviewDate(nextCycle));
            } else {
                // 未掌握 → 缩短间隔（保持当前周期，用更短间隔）
                int retryInterval = Math.max(1, getIntervalByCycle(currentCycle) / 2);
                record.setNextReviewDate(LocalDate.now().plusDays(retryInterval));
                // 不递增 cycle，留在当前周期继续巩固
            }

            log.info("Review completed: id={}, cycle={}, nextReview={}",
                    reviewId, record.getReviewCycle(), record.getNextReviewDate());

        } else if (status == ReviewStatus.SKIPPED) {
            record.setStatus(ReviewStatus.SKIPPED);
            // 跳过的复习自动延后一天
            record.setNextReviewDate(LocalDate.now().plusDays(1));
            log.info("Review skipped: id={}, rescheduled to={}", reviewId, record.getNextReviewDate());

        } else {
            throw new IllegalArgumentException("不支持的状态: " + status);
        }

        reviewRecordRepository.save(record);
        return record;
    }

    /**
     * 获取指定周期的艾宾浩斯间隔天数。
     *
     * @param cycle 周期（1-based）
     * @return 间隔天数
     */
    private int getIntervalByCycle(int cycle) {
        int index = Math.min(Math.max(cycle - 1, 0), EBBINGHAUS_INTERVALS.length - 1);
        return EBBINGHAUS_INTERVALS[index];
    }

    /**
     * 计算指定周期的下次复习日期。
     *
     * @param cycle 当前周期
     * @return 下次复习日期
     */
    private LocalDate calculateNextReviewDate(int cycle) {
        int interval = getIntervalByCycle(cycle);
        return LocalDate.now().plusDays(interval);
    }


    // ══════════════════════════════════════════════════════════════
    //  5. 复习计划生成
    // ══════════════════════════════════════════════════════════════

    /**
     * 生成复习计划 — 基于待复习列表生成每日计划。
     * <p>
     * 包含今日待复习任务、未来排程概览、完成情况统计。
     * </p>
     *
     * @param studentId 学生 ID
     * @return 复习计划 DTO
     */
    @Transactional(readOnly = true)
    public ReviewPlanDto generateReviewPlan(UUID studentId) {
        log.info("Generating review plan for student: {}", studentId);
        LocalDate today = LocalDate.now();

        // 1. 今日待复习任务（PENDING 或 OVERDUE，且 scheduledDate <= today）
        List<ReviewRecord> pendingToday = reviewRecordRepository
                .findByStudentIdAndStatusAndScheduledDateLessThanEqual(
                        studentId, ReviewStatus.PENDING, today);
        // 同时查询今日 OVERDUE 的记录（已过期但未完成的）
        List<ReviewRecord> overdueRecords = reviewRecordRepository
                .findByStudentIdAndStatusAndScheduledDateLessThanEqual(
                        studentId, ReviewStatus.OVERDUE, today);
        pendingToday.addAll(overdueRecords);

        long todayPendingCount = pendingToday.size();

        // 2. 未来 7 天排程概览
        LocalDate weekLater = today.plusDays(7);
        List<ReviewRecord> upcomingAll = reviewRecordRepository
                .findByStudentIdAndScheduledDateAfter(studentId, today);
        // 过滤出 PENDING 状态的未来记录
        List<ReviewRecord> upcoming = upcomingAll.stream()
                .filter(r -> r.getStatus() == ReviewStatus.PENDING
                        || r.getStatus() == ReviewStatus.OVERDUE)
                .filter(r -> r.getScheduledDate() != null
                        && !r.getScheduledDate().isAfter(weekLater))
                .collect(Collectors.toList());

        Map<LocalDate, Long> dateTaskCount = new HashMap<>();
        Map<LocalDate, Long> dateCompletedCount = new HashMap<>();
        for (ReviewRecord r : upcoming) {
            LocalDate d = r.getScheduledDate();
            dateTaskCount.merge(d, 1L, Long::sum);
            if (r.getStatus() == ReviewStatus.COMPLETED) {
                dateCompletedCount.merge(d, 1L, Long::sum);
            }
        }

        List<ReviewPlanDto.DailySchedule> schedule = new ArrayList<>();
        for (int i = 0; i <= 7; i++) {
            LocalDate date = today.plusDays(i);
            long taskCount = dateTaskCount.getOrDefault(date, 0L);
            long completedCount = dateCompletedCount.getOrDefault(date, 0L);
            schedule.add(new ReviewPlanDto.DailySchedule(date, taskCount, completedCount));
        }

        // 3. 完成情况统计
        long totalScheduled = reviewRecordRepository.countByStudentId(studentId);
        long totalCompleted = reviewRecordRepository.countByStudentIdAndStatus(
                studentId, ReviewStatus.COMPLETED);
        double completionRate = totalScheduled > 0
                ? BigDecimal.valueOf(totalCompleted * 100.0 / totalScheduled)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        ReviewPlanDto.CompletionStats stats = new ReviewPlanDto.CompletionStats(
                totalScheduled, totalCompleted, completionRate, BigDecimal.ZERO);

        // 4. 今日任务详情
        List<ReviewPlanDto.ReviewTask> todayTasks = pendingToday.stream()
                .map(r -> new ReviewPlanDto.ReviewTask(
                        r.getId(),
                        r.getKnowledgePointId(),
                        r.getKnowledgePointName() != null ? r.getKnowledgePointName() : "",
                        r.getReviewType(),
                        r.getReviewCycle() != null ? r.getReviewCycle() : 1,
                        r.getScheduledDate(),
                        r.getStatus() == ReviewStatus.COMPLETED,
                        r.getAccuracy()))
                .collect(Collectors.toList());

        return new ReviewPlanDto(
                studentId, todayPendingCount, todayTasks,
                schedule, stats, today);
    }


    // ══════════════════════════════════════════════════════════════
    //  6. 补充排程
    // ══════════════════════════════════════════════════════════════

    /**
     * 补充复习排程 — 为未安排复习的知识点生成复习计划。
     * <p>
     * 基于学生的错题记录，找出尚未安排复习的知识点，
     * 按艾宾浩斯间隔生成第 1 次复习排程。
     * </p>
     *
     * @param studentId         学生 ID
     * @param maxDailyReviews   每天最大新增排程数
     * @param prioritizeHighFrequency 是否优先排程高频错题知识点
     * @return 创建的复习记录列表
     */
    @Transactional
    public List<ReviewRecord> supplementReviewSchedule(UUID studentId, int maxDailyReviews,
                                                       boolean prioritizeHighFrequency) {
        log.info("Supplementing review schedule for student={}, maxDaily={}, prioritizeHighFreq={}",
                studentId, maxDailyReviews, prioritizeHighFrequency);

        // 1. 查询学生所有错题涉及的知识点
        List<Object[]> kpFrequency = errorRecordRepository
                .findTopErrorKnowledgePointsByFrequency(studentId);

        // 2. 过滤出已有哪些知识点有复习排程
        List<ReviewRecord> existingReviews = reviewRecordRepository.findByStudentId(studentId);
        Set<UUID> scheduledKpIds = existingReviews.stream()
                .map(ReviewRecord::getKnowledgePointId)
                .collect(Collectors.toSet());

        // 3. 找出需要补充排程的知识点
        List<UUID> needScheduleKpIds = new ArrayList<>();
        for (Object[] row : kpFrequency) {
            UUID kpId = (UUID) row[0];
            if (!scheduledKpIds.contains(kpId)) {
                needScheduleKpIds.add(kpId);
            }
            if (needScheduleKpIds.size() >= maxDailyReviews) {
                break;
            }
        }

        // 如果不优先高频，则打乱顺序
        if (!prioritizeHighFrequency) {
            Collections.shuffle(needScheduleKpIds);
        }

        // 4. 为每个知识点创建排程
        List<ReviewRecord> created = new ArrayList<>();
        for (UUID kpId : needScheduleKpIds) {
            // 查找知识点名称（跨模块查询）
            String kpName = resolveKnowledgePointName(kpId);

            ReviewRecord record = new ReviewRecord();
            record.setStudentId(studentId);
            record.setKnowledgePointId(kpId);
            record.setKnowledgePointName(kpName);
            record.setReviewType(ReviewType.ERROR_REVIEW);
            record.setStatus(ReviewStatus.PENDING);
            record.setReviewCycle(1);
            record.setScheduledDate(LocalDate.now().plusDays(1));
            record.setNextReviewDate(calculateNextReviewDate(1));
            created.add(reviewRecordRepository.save(record));
        }

        log.info("Supplemented {} reviews for student={}", created.size(), studentId);
        return created;
    }

    // ══════════════════════════════════════════════════════════════
    //  7. 重新排程
    // ══════════════════════════════════════════════════════════════

    /**
     * 重新排程 — 调整复习计划的日期（推迟或提前）。
     *
     * @param reviewId   复习记录 ID
     * @param daysOffset 天数偏移（正数推迟，负数提前）
     * @param reason     调整原因
     * @return 更新后的复习记录
     */
    @Transactional
    public ReviewRecord reSchedule(UUID reviewId, int daysOffset, String reason) {
        ReviewRecord record = getReviewById(reviewId);

        if (record.getStatus() == ReviewStatus.COMPLETED) {
            throw new IllegalStateException("已完成的复习记录不可重新排程: " + reviewId);
        }

        LocalDate originalDate = record.getScheduledDate();
        LocalDate newDate = originalDate.plusDays(daysOffset);
        record.setScheduledDate(newDate);

        // 同时调整 nextReviewDate
        if (record.getNextReviewDate() != null) {
            record.setNextReviewDate(record.getNextReviewDate().plusDays(daysOffset));
        }

        // 添加备注
        String rescheduleNote = String.format("[重新排程] 原日期: %s, 新日期: %s, 原因: %s",
                originalDate, newDate, reason != null ? reason : "未指定");
        String existingNotes = record.getNotes();
        record.setNotes(existingNotes != null
                ? existingNotes + "\n" + rescheduleNote
                : rescheduleNote);

        reviewRecordRepository.save(record);
        log.info("Review rescheduled: id={}, from={}, to={}, reason={}",
                reviewId, originalDate, newDate, reason);
        return record;
    }

    /**
     * 批量重新排程 — 将某学生所有 PENDING 状态的记录统一调整。
     *
     * @param studentId      学生 ID
     * @param daysOffset     天数偏移
     * @param reason         调整原因
     * @return 受影响的记录数
     */
    @Transactional
    public int batchReSchedule(UUID studentId, int daysOffset, String reason) {
        List<ReviewRecord> pendingRecords = reviewRecordRepository
                .findByStudentIdAndStatus(studentId, ReviewStatus.PENDING);
        int count = 0;
        for (ReviewRecord record : pendingRecords) {
            record.setScheduledDate(record.getScheduledDate().plusDays(daysOffset));
            if (record.getNextReviewDate() != null) {
                record.setNextReviewDate(record.getNextReviewDate().plusDays(daysOffset));
            }
            reviewRecordRepository.save(record);
            count++;
        }
        log.info("Batch rescheduled {} reviews for student={}, offset={}", count, studentId, daysOffset);
        return count;
    }


    // ══════════════════════════════════════════════════════════════
    //  8. 统计查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取错题与复习的全维度统计。
     *
     * @param studentId 学生 ID
     * @return 统计 DTO
     */
    @Transactional(readOnly = true)
    public ReviewStatisticsDto getStatistics(UUID studentId) {
        log.info("Getting review statistics for student: {}", studentId);
        ReviewStatisticsDto dto = new ReviewStatisticsDto();
        LocalDate today = LocalDate.now();

        // ── 错题统计 ──
        long totalErrors = errorRecordRepository.countByStudentId(studentId);
        long unreviewedErrors = errorRecordRepository.countByStudentIdAndIsReviewed(studentId, false);
        long reviewedErrors = totalErrors - unreviewedErrors;

        dto.setTotalErrors(totalErrors);
        dto.setUnreviewedErrors(unreviewedErrors);
        dto.setReviewedErrors(reviewedErrors);
        dto.setReviewRate(totalErrors > 0
                ? BigDecimal.valueOf(reviewedErrors * 100.0 / totalErrors)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0);

        // 按知识点分布
        List<Object[]> kpData = errorRecordRepository.findTopErrorKnowledgePointsByFrequency(studentId);
        List<ReviewStatisticsDto.KpErrorCount> kpList = new ArrayList<>();
        for (Object[] row : kpData) {
            UUID kpId = (UUID) row[0];
            long count = ((Number) row[1]).longValue();
            ReviewStatisticsDto.KpErrorCount kp = new ReviewStatisticsDto.KpErrorCount();
            kp.setKnowledgePointId(kpId.toString());
            kp.setKnowledgePointName(resolveKnowledgePointName(kpId));
            kp.setCount(count);
            kpList.add(kp);
        }
        dto.setErrorByKnowledgePoint(kpList);

        // TOP 5
        dto.setTopErrorKnowledgePoints(kpList.stream().limit(5).collect(Collectors.toList()));

        // 按错因类型分布
        List<Object[]> typeData = errorRecordRepository.findErrorTypeDistribution(studentId);
        Map<ErrorType, Long> typeMap = new HashMap<>();
        for (Object[] row : typeData) {
            typeMap.put((ErrorType) row[0], ((Number) row[1]).longValue());
        }
        dto.setErrorByType(typeMap);

        // ── 复习统计 ──
        long totalReviews = reviewRecordRepository.countByStudentId(studentId);
        long completedReviews = reviewRecordRepository.countByStudentIdAndStatus(
                studentId, ReviewStatus.COMPLETED);
        long pendingReviews = reviewRecordRepository.countByStudentIdAndStatus(
                studentId, ReviewStatus.PENDING);
        long overdueReviews = reviewRecordRepository.countByStudentIdAndStatus(
                studentId, ReviewStatus.OVERDUE);

        dto.setTotalReviews(totalReviews);
        dto.setCompletedReviews(completedReviews);
        dto.setPendingReviews(pendingReviews);
        dto.setOverdueReviews(overdueReviews);
        dto.setCompletionRate(totalReviews > 0
                ? BigDecimal.valueOf(completedReviews * 100.0 / totalReviews)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0);

        // 按复习类型统计
        List<ReviewRecord> allReviews = reviewRecordRepository.findByStudentId(studentId);
        Map<ReviewType, Long> typeCount = allReviews.stream()
                .filter(r -> r.getReviewType() != null)
                .collect(Collectors.groupingBy(ReviewRecord::getReviewType, Collectors.counting()));
        dto.setReviewByType(typeCount);

        // 按状态统计
        Map<ReviewStatus, Long> statusCount = allReviews.stream()
                .filter(r -> r.getStatus() != null)
                .collect(Collectors.groupingBy(ReviewRecord::getStatus, Collectors.counting()));
        dto.setReviewByStatus(statusCount);

        // 平均成效评分
        double avgScore = allReviews.stream()
                .filter(r -> r.getEffectivenessScore() != null)
                .collect(Collectors.averagingInt(ReviewRecord::getEffectivenessScore));
        dto.setAverageEffectivenessScore(BigDecimal.valueOf(avgScore)
                .setScale(1, RoundingMode.HALF_UP).doubleValue());

        // ── 艾宾浩斯排程 ──
        // 最大完成周期
        Integer maxCycle = allReviews.stream()
                .filter(r -> r.getStatus() == ReviewStatus.COMPLETED && r.getReviewCycle() != null)
                .map(ReviewRecord::getReviewCycle)
                .max(Integer::compareTo)
                .orElse(0);
        dto.setMaxCompletedCycle(maxCycle);

        // 各周期统计
        Map<Integer, Long> cycleCount = allReviews.stream()
                .filter(r -> r.getReviewCycle() != null)
                .collect(Collectors.groupingBy(ReviewRecord::getReviewCycle, Collectors.counting()));
        dto.setReviewByCycle(cycleCount);

        // 今日/本周待复习
        long todayReviews = reviewRecordRepository
                .findByStudentIdAndScheduledDate(studentId, today)
                .stream()
                .filter(r -> r.getStatus() == ReviewStatus.PENDING
                        || r.getStatus() == ReviewStatus.OVERDUE)
                .count();
        dto.setTodayReviews(todayReviews);

        LocalDate weekEnd = today.plusDays(7);
        long weekReviews = allReviews.stream()
                .filter(r -> r.getScheduledDate() != null
                        && !r.getScheduledDate().isBefore(today)
                        && !r.getScheduledDate().isAfter(weekEnd)
                        && (r.getStatus() == ReviewStatus.PENDING
                        || r.getStatus() == ReviewStatus.OVERDUE))
                .count();
        dto.setWeekReviews(weekReviews);

        // 最近 7 天每日完成数
        List<ReviewStatisticsDto.DailyReviewCount> dailyTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDate finalDate = date;
            List<ReviewRecord> dayRecords = allReviews.stream()
                    .filter(r -> r.getCompletedDate() != null
                            && r.getCompletedDate().equals(finalDate))
                    .collect(Collectors.toList());
            long totalDay = dayRecords.size();
            long completedDay = dayRecords.stream()
                    .filter(r -> r.getStatus() == ReviewStatus.COMPLETED)
                    .count();
            ReviewStatisticsDto.DailyReviewCount dc = new ReviewStatisticsDto.DailyReviewCount();
            dc.setDate(date.toString());
            dc.setTotalCount(totalDay);
            dc.setCompletedCount(completedDay);
            dailyTrend.add(dc);
        }
        dto.setDailyTrend(dailyTrend);

        return dto;
    }


    // ══════════════════════════════════════════════════════════════
    //  9. 初始排程（为新错题）
    // ══════════════════════════════════════════════════════════════

    /**
     * 为新错题创建初始排程 — 安排在 1 天后首次复习（周期 1）。
     *
     * @param studentId        学生 ID
     * @param knowledgePointId 知识点 ID
     * @return 创建的复习记录
     */
    @Transactional
    public ReviewRecord scheduleInitialReview(UUID studentId, UUID knowledgePointId) {
        // 检查是否已有同知识点的 PENDING 排程
        List<ReviewRecord> existing = reviewRecordRepository
                .findByStudentIdAndKnowledgePointIdAndReviewCycle(
                        studentId, knowledgePointId, 1);
        boolean hasPending = existing.stream()
                .anyMatch(r -> r.getStatus() == ReviewStatus.PENDING
                        || r.getStatus() == ReviewStatus.OVERDUE);
        if (hasPending) {
            log.warn("Initial review already exists for student={}, kp={}", studentId, knowledgePointId);
            return existing.get(0);
        }

        String kpName = resolveKnowledgePointName(knowledgePointId);
        ReviewRecord record = new ReviewRecord();
        record.setStudentId(studentId);
        record.setKnowledgePointId(knowledgePointId);
        record.setKnowledgePointName(kpName);
        record.setReviewType(ReviewType.ERROR_REVIEW);
        record.setStatus(ReviewStatus.PENDING);
        record.setReviewCycle(1);
        record.setScheduledDate(LocalDate.now().plusDays(1));
        record.setNextReviewDate(calculateNextReviewDate(1));

        ReviewRecord saved = reviewRecordRepository.save(record);
        log.info("Initial review scheduled: student={}, kp={}, date={}",
                studentId, knowledgePointId, saved.getScheduledDate());
        return saved;
    }

    /**
     * 批量创建初始排程 — 为指定知识点列表生成复习计划。
     *
     * @param studentId         学生 ID
     * @param knowledgePointIds 知识点 ID 列表
     * @return 创建的复习记录数
     */
    @Transactional
    public int batchScheduleInitialReviews(UUID studentId, List<UUID> knowledgePointIds) {
        int count = 0;
        for (UUID kpId : knowledgePointIds) {
            try {
                scheduleInitialReview(studentId, kpId);
                count++;
            } catch (Exception e) {
                log.warn("Failed to schedule initial review for kp={}: {}", kpId, e.getMessage());
            }
        }
        log.info("Batch scheduled {} initial reviews for student={}", count, studentId);
        return count;
    }

    // ══════════════════════════════════════════════════════════════
    //  10. 辅助方法
    // ══════════════════════════════════════════════════════════════

    /**
     * 跨模块查询知识点名称。
     * <p>
     * 使用 EntityManager 执行原生查询，从 course 模块的
     * knowledge_points 表中获取知识点名称。避免直接依赖其他模块的 Entity。
     * </p>
     *
     * @param knowledgePointId 知识点 ID
     * @return 知识点名称，查不到则返回空字符串
     */
    private String resolveKnowledgePointName(UUID knowledgePointId) {
        if (knowledgePointId == null) {
            return "";
        }
        try {
            String sql = "SELECT name FROM knowledge_points WHERE id = :id";
            List<String> result = entityManager.createNativeQuery(sql)
                    .setParameter("id", knowledgePointId.toString())
                    .getResultList();
            return result.isEmpty() ? "" : result.get(0);
        } catch (Exception e) {
            log.debug("Failed to resolve KP name for id={}: {}", knowledgePointId, e.getMessage());
            return "";
        }
    }

    /**
     * 使用 EntityManager 执行跨模块聚合查询。
     * <p>
     * 通用查询方法，用于需要跨模块关联查询的场景。
     * </p>
     *
     * @param sql    原生 SQL
     * @param params 参数映射
     * @return 查询结果列表
     */
    private List<Object[]> executeNativeQuery(String sql, Map<String, Object> params) {
        var query = entityManager.createNativeQuery(sql);
        if (params != null) {
            params.forEach(query::setParameter);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();
        return result;
    }

    /**
     * 获取今日复习完成率。
     *
     * @param studentId 学生 ID
     * @return 完成率（百分比，0-100）
     */
    @Transactional(readOnly = true)
    public double getTodayCompletionRate(UUID studentId) {
        LocalDate today = LocalDate.now();
        List<ReviewRecord> todayRecords = reviewRecordRepository
                .findByStudentIdAndScheduledDate(studentId, today);
        long total = todayRecords.size();
        if (total <= 0) {
            return 100.0;
        }
        long completed = todayRecords.stream()
                .filter(r -> r.getStatus() == ReviewStatus.COMPLETED)
                .count();
        return BigDecimal.valueOf(completed * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 检查并更新逾期状态 — 将 scheduledDate 早于今天且仍为 PENDING 的记录标记为 OVERDUE。
     *
     * @param studentId 学生 ID
     * @return 更新的记录数
     */
    @Transactional
    public int checkAndUpdateOverdueStatus(UUID studentId) {
        LocalDate today = LocalDate.now();
        List<ReviewRecord> pendingRecords = reviewRecordRepository
                .findByStudentIdAndStatus(studentId, ReviewStatus.PENDING);
        int count = 0;
        for (ReviewRecord record : pendingRecords) {
            if (record.getScheduledDate() != null && record.getScheduledDate().isBefore(today)) {
                record.setStatus(ReviewStatus.OVERDUE);
                reviewRecordRepository.save(record);
                count++;
            }
        }
        if (count > 0) {
            log.info("Marked {} records as OVERDUE for student={}", count, studentId);
        }
        return count;
    }
}
