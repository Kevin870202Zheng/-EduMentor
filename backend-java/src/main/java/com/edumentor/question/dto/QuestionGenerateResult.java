package com.edumentor.question.dto;

import java.util.List;

/**
 * AI 出题结果。
 *
 * @param generated 成功生成的题目数量
 * @param questions 生成的题目列表（含 DTO 完整信息）
 */
public record QuestionGenerateResult(
        int generated,
        List<QuestionDto> questions
) {}
