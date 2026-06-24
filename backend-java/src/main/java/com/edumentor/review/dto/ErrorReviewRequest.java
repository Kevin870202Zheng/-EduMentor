package com.edumentor.review.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 错题标记已复习请求 DTO。
 *
 * @author EduMentor Team
 */
@Data
public class ErrorReviewRequest {

    @NotNull(message = "错题记录 ID 不能为空")
    private UUID errorId;

    /** 复习后的正确率（可选） */
    private Double reviewAccuracy;

    /** 复习笔记 */
    private String notes;
}
