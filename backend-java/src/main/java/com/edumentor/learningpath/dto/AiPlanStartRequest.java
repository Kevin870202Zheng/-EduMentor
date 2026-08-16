package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * AI 规划会话开启请求 DTO。
 * <p>
 * courseId 可选：限定候选知识库（缺省检索全部课程知识库）。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class AiPlanStartRequest {

    @NotNull(message = "学生 ID 不能为空")
    private UUID studentId;

    /** 可选：限定候选池课程 */
    private UUID courseId;

    /** 学习目标描述，如"我想重点学行政法，为考研做准备" */
    @NotBlank(message = "学习目标不能为空")
    private String goal;
}
