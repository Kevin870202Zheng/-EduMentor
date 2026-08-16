package com.edumentor.classroom.controller;

import com.edumentor.classroom.dto.*;
import com.edumentor.classroom.entity.Classroom;
import com.edumentor.classroom.entity.ClassroomProgress;
import com.edumentor.classroom.entity.SceneQuizRecord;
import com.edumentor.classroom.service.ClassroomGenerator;
import com.edumentor.classroom.service.ClassroomService;
import com.edumentor.classroom.service.PracticeService;
import com.edumentor.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.edumentor.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 课堂控制器 — 课堂生成、播放控制、Quiz提交 REST API。
 * <p>
 * API 路径前缀: /api/v2/classrooms
 * 所有端点需要认证（JWT）。
 * </p>
 */
@RestController
@RequestMapping("/api/v2/classrooms")
@Tag(name = "沉浸式课堂", description = "AI课堂的生成、播放控制、Quiz提交、课后练习")
public class ClassroomController {

    private static final Logger log = LoggerFactory.getLogger(ClassroomController.class);

    private final ClassroomService classroomService;
    private final ClassroomGenerator classroomGenerator;
    private final PracticeService practiceService;

    public ClassroomController(ClassroomService classroomService,
                               ClassroomGenerator classroomGenerator,
                               PracticeService practiceService) {
        this.classroomService = classroomService;
        this.classroomGenerator = classroomGenerator;
        this.practiceService = practiceService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  课堂生成
    // ═══════════════════════════════════════════════════════════════

    /**
     * 基于知识点生成课堂（异步任务）。
     */
    @PostMapping("/generate")
    @Operation(summary = "生成课堂", description = "根据知识点ID异步生成AI课堂，返回jobId用于轮询")
    public ApiResponse<ClassroomGenerateResponse> generateClassroom(
            @RequestBody ClassroomGenerateRequest request) {
        if (request.getKnowledgePointIds() == null || request.getKnowledgePointIds().isEmpty()) {
            return ApiResponse.error(400, "请指定至少一个知识点ID");
        }

        List<UUID> kpIds = request.getKnowledgePointIds().stream()
                .map(UUID::fromString)
                .toList();

        String jobId = classroomGenerator.generateFull(
                UUID.fromString(request.getCourseCode()),
                kpIds,
                request.getDifficulty() != null ? request.getDifficulty() : 3);

        log.info("Classroom generation started: jobId={}, kps={}", jobId, kpIds.size());

        return ApiResponse.success(ClassroomGenerateResponse.builder()
                .jobId(jobId)
                .status("processing")
                .message("课堂生成任务已提交，请轮询状态")
                .build());
    }

    /**
     * 查询生成任务状态。
     */
    @GetMapping("/generate/{jobId}/status")
    @Operation(summary = "查询生成状态", description = "轮询课堂生成任务的当前状态")
    public ApiResponse<ClassroomGenerateResponse> getGenerateStatus(@PathVariable String jobId) {
        ClassroomGenerator.GenerateJob job = classroomGenerator.getJobStatus(jobId);
        if (job == null) {
            return ApiResponse.error(404, "生成任务不存在: " + jobId);
        }
        return ApiResponse.success(ClassroomGenerateResponse.builder()
                .jobId(job.getJobId())
                .status(job.getStatus())
                .message(job.getError())
                .build());
    }

    /**
     * 基于勾选知识点/章节生成课堂（场景一，设计文档 §4.4）。
     * <p>aggregated：同步生成一个聚合课堂；batch：异步批量生成（每知识点一课）。</p>
     */
    @PostMapping("/generate-from-selection")
    @Operation(summary = "勾选生成课堂", description = "根据勾选的知识点/章节生成课堂，支持聚合模式（一个课堂）与批量模式（每知识点一课）")
    public ApiResponse<Map<String, Object>> generateFromSelection(@RequestBody GenerateFromSelectionRequest request) {
        if (request.getCourseId() == null) {
            return ApiResponse.error(400, "请提供课程ID");
        }
        if (request.getKnowledgePointIds() == null || request.getKnowledgePointIds().isEmpty()) {
            return ApiResponse.error(400, "请至少勾选一个知识点或章节");
        }
        int difficulty = request.getDifficulty() != null ? request.getDifficulty() : 3;

        if ("batch".equalsIgnoreCase(request.getMode())) {
            String jobId = classroomGenerator.generateFull(
                    request.getCourseId(), request.getKnowledgePointIds(), difficulty);
            log.info("Batch classroom generation started: jobId={}, kps={}", jobId, request.getKnowledgePointIds().size());
            return ApiResponse.success(Map.of("mode", "batch", "jobId", jobId, "status", "processing"));
        }

        // 聚合模式：同步生成一个课堂
        Classroom classroom = classroomGenerator.generateFromSelection(
                request.getCourseId(),
                request.getKnowledgePointIds(),
                request.getTitle(),
                difficulty,
                request.getCourseName());
        Map<String, Object> result = classroom.toDto();
        result.put("mode", "aggregated");
        return ApiResponse.success(result, "课堂生成成功");
    }

    // ═══════════════════════════════════════════════════════════════
    //  课堂查询
    // ═══════════════════════════════════════════════════════════════

    /**
     * 获取课堂详情（含场景和教学动作）。
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取课堂详情", description = "返回课堂完整信息，包含所有场景和教学动作")
    public ApiResponse<ClassroomDetailDto> getClassroom(@PathVariable String id) {
        ClassroomDetailDto dto = classroomService.getClassroomDetail(UUID.fromString(id));
        return ApiResponse.success(dto);
    }

    /**
     * 获取课程下的课堂列表。
     */
    @GetMapping
    @Operation(summary = "课堂列表", description = "按课程ID或知识点ID过滤课堂列表")
    public ApiResponse<List<Map<String, Object>>> listClassrooms(
            @RequestParam(required = false) String courseId) {
        if (courseId == null) {
            return ApiResponse.error(400, "请提供课程ID参数(courseId)");
        }
        List<Map<String, Object>> classrooms = classroomService.getClassroomsByCourse(UUID.fromString(courseId));
        return ApiResponse.success(classrooms);
    }

    /**
     * 获取学生的课堂历史记录。
     */
    @GetMapping("/history")
    @Operation(summary = "课堂历史", description = "获取当前学生的课堂学习历史记录")
    public ApiResponse<List<Map<String, Object>>> getHistory(
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        List<Map<String, Object>> history = classroomService.getStudentHistory(studentId);
        return ApiResponse.success(history);
    }

    /**
     * 为知识点获取或生成课堂。
     */
    @PostMapping("/resolve")
    @Operation(summary = "获取或生成课堂", description = "查找已发布的课堂，如果不存在则自动生成")
    public ApiResponse<Map<String, Object>> resolveClassroom(
            @RequestParam String courseId,
            @RequestParam String knowledgePointId,
            @RequestParam(defaultValue = "3") int difficulty) {
        Classroom classroom = classroomService.getOrCreateClassroom(
                UUID.fromString(courseId),
                UUID.fromString(knowledgePointId),
                difficulty);
        return ApiResponse.success(classroom.toDto());
    }

    // ═══════════════════════════════════════════════════════════════
    //  播放控制
    // ═══════════════════════════════════════════════════════════════

    /**
     * 开始学习课堂。
     */
    @PostMapping("/{id}/start")
    @Operation(summary = "开始课堂", description = "创建或恢复课堂学习进度，返回当前播放位置")
    public ApiResponse<ClassroomProgressDto> startClassroom(
            @PathVariable String id,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        ClassroomProgress progress = classroomService.startClassroom(studentId, UUID.fromString(id));
        return ApiResponse.success(toProgressDto(progress));
    }

    /**
     * 更新播放进度。
     */
    @PostMapping("/{id}/progress")
    @Operation(summary = "更新进度", description = "更新当前播放位置（场景ID + 动作序号）")
    public ApiResponse<ClassroomProgressDto> updateProgress(
            @PathVariable String id,
            @RequestParam String sceneId,
            @RequestParam(defaultValue = "0") int actionOrder,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        ClassroomProgress progress = classroomService.updateProgress(
                studentId, UUID.fromString(id), UUID.fromString(sceneId), actionOrder);
        return ApiResponse.success(toProgressDto(progress));
    }

    /**
     * 获取播放进度。
     */
    @GetMapping("/{id}/progress")
    @Operation(summary = "获取进度", description = "获取当前课堂的播放进度")
    public ApiResponse<ClassroomProgressDto> getProgress(
            @PathVariable String id,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        ClassroomProgress progress = classroomService.getProgress(studentId, UUID.fromString(id));
        if (progress == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(toProgressDto(progress));
    }

    /**
     * 标记课堂完成。
     */
    @PostMapping("/{id}/complete")
    @Operation(summary = "完成课堂", description = "标记课堂学习完成")
    public ApiResponse<ClassroomProgressDto> completeClassroom(
            @PathVariable String id,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        ClassroomProgress progress = classroomService.completeClassroom(studentId, UUID.fromString(id));
        return ApiResponse.success(toProgressDto(progress));
    }

    /**
     * 暂停课堂。
     */
    @PostMapping("/{id}/pause")
    @Operation(summary = "暂停课堂", description = "暂停课堂播放，保存当前进度")
    public ApiResponse<ClassroomProgressDto> pauseClassroom(
            @PathVariable String id,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        ClassroomProgress progress = classroomService.pauseClassroom(studentId, UUID.fromString(id));
        return ApiResponse.success(toProgressDto(progress));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Quiz 提交
    // ═══════════════════════════════════════════════════════════════

    /**
     * 提交课堂 Quiz 答案。
     */
    @PostMapping("/scenes/{sceneId}/quiz/submit")
    @Operation(summary = "提交Quiz答案", description = "提交课堂内Quiz答案，触发BKT更新")
    public ApiResponse<QuizSubmitResponse> submitQuiz(
            @PathVariable String sceneId,
            @RequestBody QuizSubmitRequest request,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        if (studentId == null) {
            log.error("submitQuiz failed: unable to resolve studentId from principal={}", principal);
            return ApiResponse.error(401, "未认证，请重新登录后再提交答案");
        }
        request.setSceneId(sceneId);
        QuizSubmitResponse response = classroomService.submitQuiz(studentId, request);
        return ApiResponse.success(response);
    }

    /**
     * 获取 Quiz 作答记录。
     */
    @GetMapping("/scenes/{sceneId}/quiz/result")
    @Operation(summary = "获取Quiz结果", description = "获取学生在某个场景中的Quiz作答记录")
    public ApiResponse<List<SceneQuizRecord>> getQuizResult(
            @PathVariable String sceneId,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        List<SceneQuizRecord> records = classroomService.getQuizRecords(studentId, UUID.fromString(sceneId));
        return ApiResponse.success(records);
    }

    // ═══════════════════════════════════════════════════════════════
    //  课后练习
    // ═══════════════════════════════════════════════════════════════

    /**
     * 基于课堂Quiz结果生成课后练习。
     */
    @PostMapping("/{id}/generate-practice")
    @Operation(summary = "生成课后练习", description = "基于课堂Quiz结果生成变体练习题")
    public ApiResponse<List<PracticeQuestionDto>> generatePractice(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") int questionCount,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        List<PracticeQuestionDto> questions = practiceService.generatePractice(
                studentId, UUID.fromString(id), questionCount);
        return ApiResponse.success(questions);
    }

    /**
     * 获取课后练习题列表。
     */
    @GetMapping("/{id}/practice-questions")
    @Operation(summary = "获取课后练习", description = "获取已生成的课后练习题列表")
    public ApiResponse<List<PracticeQuestionDto>> getPracticeQuestions(
            @PathVariable String id,
            @AuthenticationPrincipal Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        List<PracticeQuestionDto> questions = practiceService.generatePractice(
                studentId, UUID.fromString(id), 5);
        return ApiResponse.success(questions);
    }

    // ═══════════════════════════════════════════════════════════════
    //  内部方法
    // ═══════════════════════════════════════════════════════════════

    private UUID getUserIdFromPrincipal(Principal principal) {
        // 方式1: @AuthenticationPrincipal 注入的 Principal
        if (principal != null) {
            try {
                UUID id = UUID.fromString(principal.getName());
                log.debug("getUserId: from @AuthenticationPrincipal: {}", id);
                return id;
            } catch (IllegalArgumentException e) {
                log.debug("getUserId: principal.name is not UUID: {}", principal.getName());
            }
        }
        // 方式2: 从 SecurityContextHolder 获取
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Object p = auth.getPrincipal();
            if (p instanceof User user) {
                log.debug("getUserId: from SecurityContext User: {}", user.getId());
                return user.getId();
            }
            if (p instanceof Principal pr) {
                try { return UUID.fromString(pr.getName()); }
                catch (IllegalArgumentException e) { /* ignore */ }
            }
            if (p instanceof String name) {
                try { return UUID.fromString(name); }
                catch (IllegalArgumentException e) { /* ignore */ }
            }
        }
        log.error("getUserIdFromPrincipal: FAILED - no valid authentication found, principal={}", principal);
        return null;
    }

    private ClassroomProgressDto toProgressDto(ClassroomProgress p) {
        if (p == null) return null;
        return ClassroomProgressDto.builder()
                .id(p.getId().toString())
                .studentId(p.getStudentId().toString())
                .classroomId(p.getClassroomId().toString())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .currentSceneId(p.getCurrentSceneId() != null ? p.getCurrentSceneId().toString() : null)
                .currentActionOrder(p.getCurrentActionOrder())
                .scenesCompleted(p.getScenesCompleted())
                .totalScenes(p.getTotalScenes())
                .quizCorrectCount(p.getQuizCorrectCount())
                .quizTotalCount(p.getQuizTotalCount())
                .totalWatchSeconds(p.getTotalWatchSeconds())
                .startedAt(p.getStartedAt() != null ? p.getStartedAt().toString() : null)
                .completedAt(p.getCompletedAt() != null ? p.getCompletedAt().toString() : null)
                .lastAccessedAt(p.getLastAccessedAt() != null ? p.getLastAccessedAt().toString() : null)
                .build();
    }
}
