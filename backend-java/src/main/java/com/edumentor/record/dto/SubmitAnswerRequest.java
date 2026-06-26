package com.edumentor.record.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 提交答题请求 DTO。
 */
@Data
public class SubmitAnswerRequest {
    @NotNull(message = "题目 ID 不能为空")
    private UUID questionId;

    @NotNull(message = "学生答案不能为空")
    private String studentAnswer;

    /** 答题用时（秒） */
    private Integer timeSpentSeconds;
}
