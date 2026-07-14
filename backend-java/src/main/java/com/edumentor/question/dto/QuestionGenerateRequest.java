package com.edumentor.question.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * AI 出题请求。
 *
 * @param courseId          课程 ID
 * @param knowledgePointId  知识点 ID
 * @param counts            各题型数量，key 为 QuestionType 枚举名，value 为数量
 */
public record QuestionGenerateRequest(
        @NotNull UUID courseId,
        @NotNull UUID knowledgePointId,
        Map<String, Integer> counts
) {}
