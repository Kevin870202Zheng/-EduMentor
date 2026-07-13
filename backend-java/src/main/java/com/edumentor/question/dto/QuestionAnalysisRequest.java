package com.edumentor.question.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 题目分析请求。
 *
 * @param questionId    题目 ID（必填）
 * @param studentAnswer 学生答案（答题后分析时传入，可为空）
 * @param usage         分析用途：pre_answer（答题前）/ post_answer（答题后，默认）
 */
public record QuestionAnalysisRequest(
        @NotNull UUID questionId,
        String studentAnswer,
        String usage
) {
    public String getEffectiveUsage() {
        return usage != null ? usage : (studentAnswer != null ? "post_answer" : "pre_answer");
    }
}
