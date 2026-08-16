package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 手动勾选创建路径请求 DTO — 知识树勾选编辑器（CUSTOM 来源）。
 * <p>
 * nodeIds 为有序的知识点 ID 列表，按给定顺序生成路径节点。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class CustomPathRequest {

    @NotNull(message = "学生 ID 不能为空")
    private UUID studentId;

    @NotNull(message = "课程 ID 不能为空")
    private UUID courseId;

    @NotBlank(message = "路径名称不能为空")
    private String name;

    private String description;

    /** 有序知识点 ID 列表 */
    private List<UUID> nodeIds;

    private Integer dailyMinutes;
}
