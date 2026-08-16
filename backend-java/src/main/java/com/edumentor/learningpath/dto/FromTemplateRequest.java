package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 从模板生成路径请求 DTO。
 * <p>
 * TEACHING（师范生备课）模板必须携带 stage（目标学段），
 * themeIds 可选（主题过滤）。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class FromTemplateRequest {

    @NotNull(message = "学生 ID 不能为空")
    private UUID studentId;

    @NotNull(message = "课程 ID 不能为空")
    private UUID courseId;

    @NotNull(message = "模板 ID 不能为空")
    private UUID templateId;

    /** 目标学段（PRIMARY/JUNIOR/SENIOR/UNIVERSITY），TEACHING 模板必填 */
    private String stage;

    /** 主题过滤（可选） */
    private List<UUID> themeIds;

    /** 是否跳过已掌握知识点（默认跳过） */
    private Boolean skipMastered = true;
}
