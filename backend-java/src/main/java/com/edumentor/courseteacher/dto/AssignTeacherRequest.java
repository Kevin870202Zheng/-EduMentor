package com.edumentor.courseteacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignTeacherRequest {
    @NotNull(message = "课程 ID 不能为空")
    private UUID courseId;

    @NotNull(message = "教师 ID 不能为空")
    private UUID teacherId;

    @NotBlank(message = "角色不能为空")
    private String role;
}
