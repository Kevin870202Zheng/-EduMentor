package com.edumentor.question.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.question.dto.QuestionCreateRequest;
import com.edumentor.question.dto.QuestionDto;
import com.edumentor.question.dto.QuestionUpdateRequest;
import com.edumentor.question.service.QuestionService;
import com.edumentor.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 题目管理 REST API — 教师端习题 CRUD。
 */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<QuestionDto> createQuestion(
            @Valid @RequestBody QuestionCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        QuestionDto dto = questionService.createQuestion(request, currentUser.getId());
        return ApiResponse.success(dto, "题目创建成功");
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionDto> getQuestion(@PathVariable UUID id) {
        return ApiResponse.success(questionService.getQuestion(id));
    }

    @GetMapping
    public ApiResponse<List<QuestionDto>> listQuestions(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID knowledgePointId) {
        if (courseId != null) {
            return ApiResponse.success(questionService.listQuestionsByCourse(courseId));
        }
        if (knowledgePointId != null) {
            return ApiResponse.success(questionService.listQuestionsByKnowledgePoint(knowledgePointId));
        }
        return ApiResponse.error(400, "请提供 courseId 或 knowledgePointId 参数");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<QuestionDto> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionUpdateRequest request) {
        return ApiResponse.success(questionService.updateQuestion(id, request), "题目更新成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> deleteQuestion(@PathVariable UUID id) {
        questionService.deleteQuestion(id);
        return ApiResponse.success(null, "题目已删除");
    }
}
