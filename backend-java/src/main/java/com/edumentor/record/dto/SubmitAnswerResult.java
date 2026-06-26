package com.edumentor.record.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/**
 * 提交答题结果响应 DTO。
 */
@Data
@AllArgsConstructor
public class SubmitAnswerResult {
    private UUID recordId;
    private UUID questionId;
    private UUID knowledgePointId;
    private UUID courseId;
    private boolean isCorrect;
    private String correctAnswer;
    private String explanation;
    private String studentAnswer;
}
