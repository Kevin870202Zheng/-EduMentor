package com.edumentor.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 选课请求 DTO。
 */
@Data
public class EnrollRequest {
    @NotNull(message = "学生 ID 不能为空")
    private UUID studentId;

    @NotNull(message = "课程 ID 不能为空")
    private UUID courseId;
}
