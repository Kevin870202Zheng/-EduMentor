package com.edumentor.question.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.question.dto.QuestionAnalysisRequest;
import com.edumentor.question.dto.QuestionAnalysisResult;
import com.edumentor.question.service.QuestionAnalysisService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目分析控制器 — 提供大模型驱动的题目智能分析。
 * 分析结果不持久化，每次请求实时生成。
 */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(QuestionAnalysisController.class);

    private final QuestionAnalysisService questionAnalysisService;

    public QuestionAnalysisController(QuestionAnalysisService questionAnalysisService) {
        this.questionAnalysisService = questionAnalysisService;
    }

    /**
     * 分析题目 — 使用大模型对题目进行深度分析。
     * <p>
     * 答题前使用：仅传入 questionId，分析题目本身，帮助学生理解。
     * 答题后使用：传入 questionId + studentAnswer，结合学生答案做针对性分析。
     *
     * @param request 分析请求
     * @return 结构化分析结果
     */
    @PostMapping("/analyze")
    public ApiResponse<QuestionAnalysisResult> analyzeQuestion(
            @Valid @RequestBody QuestionAnalysisRequest request) {
        log.info("收到题目分析请求: questionId={}, usage={}",
                request.questionId(), request.getEffectiveUsage());
        QuestionAnalysisResult result = questionAnalysisService.analyze(request);
        return ApiResponse.success(result, "分析完成");
    }
}
