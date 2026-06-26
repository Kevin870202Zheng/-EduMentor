package com.edumentor.record.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.record.dto.SubmitAnswerRequest;
import com.edumentor.record.dto.SubmitAnswerResult;
import com.edumentor.record.service.AnswerService;
import com.edumentor.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 答题提交 REST API — 学生作答并获取即时反馈。
 */
@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ApiResponse<SubmitAnswerResult> submitAnswer(
            @Valid @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal User currentUser) {
        SubmitAnswerResult result = answerService.submitAnswer(currentUser.getId(), request);
        return ApiResponse.success(result, result.isCorrect() ? "回答正确！" : "回答错误");
    }
}
