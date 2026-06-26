package com.edumentor.review.controller;

import com.edumentor.review.dto.*;
import com.edumentor.review.entity.ErrorRecord;
import com.edumentor.review.entity.ReviewRecord;
import com.edumentor.entity.enums.ErrorType;
import com.edumentor.review.entity.enums.ReviewStatus;
import com.edumentor.review.service.ReviewService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 错题复盘控制器 — 提供错题分析、复习计划、艾宾浩斯排程等 REST API。
 * <p>
 * 所有接口路径以 {@code /api/reviews} 开头。
 * 提供约 20 个端点，涵盖错题 CRUD、复习 CRUD、分析、排程、统计等功能。
 * </p>
 *
 * @author EduMentor Team
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ══════════════════════════════════════════════════════════════
    //  错题管理 Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /api/reviews/errors — 记录错题。
     */
    @PostMapping("/errors")
    public ResponseEntity<ErrorRecordDto> recordError(
            @Valid @RequestBody RecordErrorRequest request) {
        log.info("Record error: student={}, question={}", request.getStudentId(), request.getQuestionId());
        ErrorRecord record = reviewService.recordError(
                request.getStudentId(), request.getQuestionId(),
                request.getKnowledgePointId(), request.getKnowledgePointName(),
                request.getQuestionContent(), request.getStudentAnswer(),
                request.getCorrectAnswer(), request.getErrorType(),
                request.getDifficulty());
        return ResponseEntity.status(HttpStatus.CREATED).body(ErrorRecordDto.fromEntity(record));
    }

    /**
     * GET /api/reviews/errors/{id} — 查询错题详情。
     */
    @GetMapping("/errors/{id}")
    public ResponseEntity<ErrorRecordDto> getErrorById(@PathVariable UUID id) {
        ErrorRecord record = reviewService.getErrorById(id);
        return ResponseEntity.ok(ErrorRecordDto.fromEntity(record));
    }

    /**
     * GET /api/reviews/errors — 分页查询错题列表。
     */
    @GetMapping("/errors")
    public ResponseEntity<List<ErrorRecordDto>> getErrorRecords(
            @RequestParam UUID studentId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ErrorRecord> records = reviewService.getErrorRecords(studentId, courseId, page, size);
        List<ErrorRecordDto> dtos = records.stream()
                .map(ErrorRecordDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/reviews/errors/unreviewed — 查询未复习的错题。
     */
    @GetMapping("/errors/unreviewed")
    public ResponseEntity<List<ErrorRecordDto>> getUnreviewedErrors(
            @RequestParam UUID studentId) {
        List<ErrorRecord> records = reviewService.getUnreviewedErrors(studentId);
        List<ErrorRecordDto> dtos = records.stream()
                .map(ErrorRecordDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * PUT /api/reviews/errors/{id}/analysis — 更新错题分析。
     */
    @PutMapping("/errors/{id}/analysis")
    public ResponseEntity<ErrorRecordDto> updateErrorAnalysis(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAnalysisRequest request) {
        ErrorRecord record = reviewService.updateErrorAnalysis(
                id, request.getErrorType(), request.getErrorAnalysis(), request.getReviewSuggestion());
        return ResponseEntity.ok(ErrorRecordDto.fromEntity(record));
    }

    /**
     * PUT /api/reviews/errors/{id}/review — 标记错题为已复习。
     */
    @PutMapping("/errors/{id}/review")
    public ResponseEntity<ErrorRecordDto> markErrorReviewed(
            @PathVariable UUID id,
            @RequestBody ErrorReviewRequest request) {
        BigDecimal accuracy = request.getReviewAccuracy() != null
                ? BigDecimal.valueOf(request.getReviewAccuracy()) : null;
        ErrorRecord record = reviewService.markErrorReviewed(id, accuracy, request.getNotes());
        return ResponseEntity.ok(ErrorRecordDto.fromEntity(record));
    }

    /**
     * DELETE /api/reviews/errors/{id} — 删除错题记录。
     */
    @DeleteMapping("/errors/{id}")
    public ResponseEntity<Void> deleteError(@PathVariable UUID id) {
        reviewService.deleteError(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/reviews/errors/count — 统计错题总数。
     */
    @GetMapping("/errors/count")
    public ResponseEntity<Long> countErrors(@RequestParam UUID studentId) {
        long count = reviewService.countErrors(studentId);
        return ResponseEntity.ok(count);
    }


    // ══════════════════════════════════════════════════════════════
    //  复习记录管理 Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /api/reviews — 创建复习记录。
     */
    @PostMapping
    public ResponseEntity<ReviewRecordDto> createReview(
            @Valid @RequestBody ReviewCreateRequest request) {
        log.info("Create review: student={}, kp={}", request.getStudentId(), request.getKnowledgePointId());
        ReviewRecord record = reviewService.createReview(
                request.getStudentId(), request.getKnowledgePointId(),
                request.getKnowledgePointName(), request.getErrorRecordId(),
                request.getReviewType(), request.getReviewCycle(),
                request.getDaysUntilReview());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewRecordDto.fromEntity(record));
    }

    /**
     * GET /api/reviews/{id} — 查询复习记录详情。
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReviewRecordDto> getReviewById(@PathVariable UUID id) {
        ReviewRecord record = reviewService.getReviewById(id);
        return ResponseEntity.ok(ReviewRecordDto.fromEntity(record));
    }

    /**
     * GET /api/reviews — 分页查询复习记录。
     */
    @GetMapping
    public ResponseEntity<List<ReviewRecordDto>> getReviewRecords(
            @RequestParam UUID studentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ReviewRecord> records = reviewService.getReviewRecords(studentId, page, size);
        List<ReviewRecordDto> dtos = records.stream()
                .map(ReviewRecordDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/reviews/status/{status} — 按状态查询复习记录。
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ReviewRecordDto>> getReviewsByStatus(
            @RequestParam UUID studentId,
            @PathVariable ReviewStatus status) {
        List<ReviewRecord> records = reviewService.getReviewsByStatus(studentId, status);
        List<ReviewRecordDto> dtos = records.stream()
                .map(ReviewRecordDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * DELETE /api/reviews/{id} — 删除复习记录。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }


    // ══════════════════════════════════════════════════════════════
    //  复习进度 Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * PUT /api/reviews/{id}/progress — 更新复习进度（完成/跳过）。
     */
    @PutMapping("/{id}/progress")
    public ResponseEntity<ReviewRecordDto> updateProgress(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewProgressRequest request) {
        log.info("Update review progress: id={}, status={}", id, request.getStatus());
        ReviewStatus status = ReviewStatus.valueOf(request.getStatus().toUpperCase());
        BigDecimal accuracy = request.getAccuracy() != null
                ? BigDecimal.valueOf(request.getAccuracy()) : null;
        ReviewRecord record = reviewService.updateProgress(
                id, status, request.getSpentMinutes(),
                request.getEffectivenessScore(), request.getNotes(), accuracy);
        return ResponseEntity.ok(ReviewRecordDto.fromEntity(record));
    }

    /**
     * PUT /api/reviews/{id}/reschedule — 重新排程复习记录。
     */
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<ReviewRecordDto> rescheduleReview(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewRescheduleRequest request) {
        log.info("Reschedule review: id={}, offset={}", id, request.getDaysOffset());
        ReviewRecord record = reviewService.reSchedule(id, request.getDaysOffset(), request.getReason());
        return ResponseEntity.ok(ReviewRecordDto.fromEntity(record));
    }

    /**
     * PUT /api/reviews/reschedule/batch — 批量重新排程。
     */
    @PutMapping("/reschedule/batch")
    public ResponseEntity<Integer> batchReschedule(
            @RequestParam UUID studentId,
            @RequestParam int daysOffset,
            @RequestParam(defaultValue = "批量调整") String reason) {
        int count = reviewService.batchReSchedule(studentId, daysOffset, reason);
        return ResponseEntity.ok(count);
    }


    // ══════════════════════════════════════════════════════════════
    //  复习计划 Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /api/reviews/plan/generate — 生成复习计划。
     */
    @PostMapping("/plan/generate")
    public ResponseEntity<ReviewPlanDto> generateReviewPlan(
            @Valid @RequestBody ReviewPlanRequest request) {
        log.info("Generate review plan: student={}", request.getStudentId());
        // 检查并更新逾期状态
        reviewService.checkAndUpdateOverdueStatus(request.getStudentId());

        ReviewPlanDto plan = reviewService.generateReviewPlan(request.getStudentId());
        return ResponseEntity.ok(plan);
    }

    /**
     * GET /api/reviews/plan — 直接获取今日复习计划。
     */
    @GetMapping("/plan")
    public ResponseEntity<ReviewPlanDto> getReviewPlan(
            @RequestParam UUID studentId) {
        reviewService.checkAndUpdateOverdueStatus(studentId);
        ReviewPlanDto plan = reviewService.generateReviewPlan(studentId);
        return ResponseEntity.ok(plan);
    }


    // ══════════════════════════════════════════════════════════════
    //  排程 Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /api/reviews/schedule/initial — 为新错题创建初始排程。
     */
    @PostMapping("/schedule/initial")
    public ResponseEntity<ReviewRecordDto> scheduleInitialReview(
            @RequestParam UUID studentId,
            @RequestParam UUID knowledgePointId) {
        ReviewRecord record = reviewService.scheduleInitialReview(studentId, knowledgePointId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewRecordDto.fromEntity(record));
    }

    /**
     * POST /api/reviews/schedule/batch — 批量创建初始排程。
     */
    @PostMapping("/schedule/batch")
    public ResponseEntity<Integer> batchScheduleInitial(
            @RequestParam UUID studentId,
            @RequestBody List<UUID> knowledgePointIds) {
        int count = reviewService.batchScheduleInitialReviews(studentId, knowledgePointIds);
        return ResponseEntity.ok(count);
    }

    /**
     * POST /api/reviews/schedule/supplement — 补充排程未安排的知识点。
     */
    @PostMapping("/schedule/supplement")
    public ResponseEntity<List<ReviewRecordDto>> supplementSchedule(
            @Valid @RequestBody ReviewPlanRequest request) {
        List<ReviewRecord> records = reviewService.supplementReviewSchedule(
                request.getStudentId(), request.getMaxDailyReviews(),
                request.isPrioritizeHighFrequency());
        List<ReviewRecordDto> dtos = records.stream()
                .map(ReviewRecordDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }


    // ══════════════════════════════════════════════════════════════
    //  分析 & 统计 Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /api/reviews/analysis — 错题分析。
     */
    @GetMapping("/analysis")
    public ResponseEntity<ErrorAnalysisDto> analyzeErrors(
            @RequestParam UUID studentId) {
        log.info("Analyze errors for student: {}", studentId);
        ErrorAnalysisDto analysis = reviewService.analyzeErrors(studentId);
        return ResponseEntity.ok(analysis);
    }

    /**
     * GET /api/reviews/statistics — 全维度统计。
     */
    @GetMapping("/statistics")
    public ResponseEntity<ReviewStatisticsDto> getStatistics(
            @RequestParam UUID studentId) {
        log.info("Get statistics for student: {}", studentId);
        ReviewStatisticsDto stats = reviewService.getStatistics(studentId);
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/reviews/today-completion — 今日完成率。
     */
    @GetMapping("/today-completion")
    public ResponseEntity<Double> getTodayCompletionRate(
            @RequestParam UUID studentId) {
        double rate = reviewService.getTodayCompletionRate(studentId);
        return ResponseEntity.ok(rate);
    }

    /**
     * POST /api/reviews/check-overdue — 检查并更新逾期状态。
     */
    @PostMapping("/check-overdue")
    public ResponseEntity<Integer> checkOverdue(
            @RequestParam UUID studentId) {
        int count = reviewService.checkAndUpdateOverdueStatus(studentId);
        return ResponseEntity.ok(count);
    }


    // ══════════════════════════════════════════════════════════════
    //  内部请求 DTO 类
    // ══════════════════════════════════════════════════════════════

    /**
     * 记录错题请求。
     */
    public static class RecordErrorRequest {

        private UUID studentId;
        private UUID questionId;
        private UUID knowledgePointId;
        private String knowledgePointName;
        private String questionContent;
        private String studentAnswer;
        private String correctAnswer;
        private ErrorType errorType;
        private Integer difficulty;

        public UUID getStudentId() { return studentId; }
        public void setStudentId(UUID studentId) { this.studentId = studentId; }

        public UUID getQuestionId() { return questionId; }
        public void setQuestionId(UUID questionId) { this.questionId = questionId; }

        public UUID getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(UUID knowledgePointId) { this.knowledgePointId = knowledgePointId; }

        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }

        public String getQuestionContent() { return questionContent; }
        public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }

        public String getStudentAnswer() { return studentAnswer; }
        public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }

        public String getCorrectAnswer() { return correctAnswer; }
        public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

        public ErrorType getErrorType() { return errorType; }
        public void setErrorType(ErrorType errorType) { this.errorType = errorType; }

        public Integer getDifficulty() { return difficulty; }
        public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    }

    /**
     * 更新分析请求。
     */
    public static class UpdateAnalysisRequest {

        private ErrorType errorType;
        private String errorAnalysis;
        private String reviewSuggestion;

        public ErrorType getErrorType() { return errorType; }
        public void setErrorType(ErrorType errorType) { this.errorType = errorType; }

        public String getErrorAnalysis() { return errorAnalysis; }
        public void setErrorAnalysis(String errorAnalysis) { this.errorAnalysis = errorAnalysis; }

        public String getReviewSuggestion() { return reviewSuggestion; }
        public void setReviewSuggestion(String reviewSuggestion) { this.reviewSuggestion = reviewSuggestion; }
    }
}
