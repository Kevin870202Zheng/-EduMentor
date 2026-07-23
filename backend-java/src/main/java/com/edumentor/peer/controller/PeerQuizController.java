package com.edumentor.peer.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.enrollment.entity.StudentCourse;
import com.edumentor.enrollment.repository.StudentCourseRepository;
import com.edumentor.peer.dto.*;
import com.edumentor.peer.service.PeerQuizService;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学生互出题考核 REST API。
 */
@RestController
@RequestMapping("/api/v1/peer-quizzes")
public class PeerQuizController {

    private final PeerQuizService peerQuizService;
    private final StudentCourseRepository studentCourseRepository;
    private final UserRepository userRepository;

    public PeerQuizController(PeerQuizService peerQuizService,
                              StudentCourseRepository studentCourseRepository,
                              UserRepository userRepository) {
        this.peerQuizService = peerQuizService;
        this.studentCourseRepository = studentCourseRepository;
        this.userRepository = userRepository;
    }

    /**
     * 创建考核任务。
     */
    @PostMapping
    public ApiResponse<PeerQuizDto> createQuiz(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid PeerQuizCreateRequestWrapper request) {
        PeerQuizDto dto = peerQuizService.createQuiz(
                user.getId(), request.quiz(), request.questions());
        return ApiResponse.success(dto);
    }

    /**
     * 获取我的待考核列表。
     */
    @GetMapping("/pending")
    public ApiResponse<List<PeerQuizDto>> getPendingQuizzes(@AuthenticationPrincipal User user) {
        return ApiResponse.success(peerQuizService.getPendingQuizzes(user.getId()));
    }

    /**
     * 获取我的已考核列表。
     */
    @GetMapping("/completed")
    public ApiResponse<List<PeerQuizDto>> getCompletedQuizzes(@AuthenticationPrincipal User user) {
        return ApiResponse.success(peerQuizService.getCompletedQuizzes(user.getId()));
    }

    /**
     * 获取我创建的考核列表。
     */
    @GetMapping("/created")
    public ApiResponse<List<PeerQuizDto>> getMyCreatedQuizzes(@AuthenticationPrincipal User user) {
        return ApiResponse.success(peerQuizService.getMyCreatedQuizzes(user.getId()));
    }

    /**
     * 获取考核详情。
     */
    @GetMapping("/{quizId}")
    public ApiResponse<PeerQuizDetailDto> getQuizDetail(@PathVariable UUID quizId) {
        return ApiResponse.success(peerQuizService.getQuizDetail(quizId));
    }

    /**
     * 提交考核结果（参与者完成全部题目后调用）。
     */
    @PostMapping("/{quizId}/submit")
    public ApiResponse<?> submitQuiz(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal User user) {
        peerQuizService.submitQuiz(quizId, user.getId());
        return ApiResponse.success(null, "考核提交成功");
    }

    /**
     * 关闭考核（出题者调用）。
     */
    @PutMapping("/{quizId}/close")
    public ApiResponse<?> closeQuiz(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal User user) {
        peerQuizService.closeQuiz(quizId, user.getId());
        return ApiResponse.success(null, "考核已关闭");
    }

    /**
     * 获取考核结果（出题者视角）。
     */
    @GetMapping("/{quizId}/results")
    public ApiResponse<PeerQuizResultDto> getQuizResults(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal User user) {
        return ApiResponse.success(peerQuizService.getQuizResults(quizId, user.getId()));
    }

    /**
     * 获取单题答题统计（出题者视角）。
     */
    @GetMapping("/{quizId}/questions/{questionId}/results")
    public ApiResponse<PeerQuizQuestionResultDto> getQuestionResults(
            @PathVariable UUID quizId,
            @PathVariable UUID questionId,
            @AuthenticationPrincipal User user) {
        return ApiResponse.success(
                peerQuizService.getQuestionResults(quizId, questionId, user.getId()));
    }

    /**
     * 获取同课程的学生列表（用于选择被考核者）。
     */
    @GetMapping("/students")
    public ApiResponse<List<StudentInfo>> getCourseStudents(
            @RequestParam UUID courseId) {
        List<StudentCourse> enrollments = studentCourseRepository.findByCourseId(courseId);
        List<StudentInfo> students = enrollments.stream()
                .filter(e -> "active".equals(e.getStatus()))
                .map(e -> {
                    String name = userRepository.findById(e.getStudentId())
                            .map(User::getDisplayName).orElse("未知");
                    return new StudentInfo(e.getStudentId(), name);
                })
                .collect(Collectors.toList());
        return ApiResponse.success(students);
    }

    /**
     * 同课程学生信息。
     */
    public record StudentInfo(UUID studentId, String displayName) {}
}
