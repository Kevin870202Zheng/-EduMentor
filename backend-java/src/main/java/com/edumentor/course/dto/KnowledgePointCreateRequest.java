package com.edumentor.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 创建知识点请求 DTO。
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Data
public class KnowledgePointCreateRequest {

    @NotNull(message = "所属课程不能为空")
    private UUID courseId;

    @NotBlank(message = "知识点名称不能为空")
    private String name;

    @NotBlank(message = "知识点描述不能为空")
    private String description;

    @NotBlank(message = "知识点内容不能为空")
    private String content;

    @Min(value = 1, message = "难度等级最小为 1")
    @Max(value = 5, message = "难度等级最大为 5")
    @NotNull(message = "难度等级不能为空")
    private Integer difficulty;

    @Min(value = 1, message = "重要程度最小为 1")
    @Max(value = 5, message = "重要程度最大为 5")
    @NotNull(message = "重要程度不能为空")
    private Integer importance;

    @NotBlank(message = "学科不能为空")
    private String subject;

    private String tags;

    private UUID parentKpId;

    private String type;

    private Integer orderIndex;
}
