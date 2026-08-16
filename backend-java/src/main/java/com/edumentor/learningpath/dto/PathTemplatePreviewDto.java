package com.edumentor.learningpath.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 路径模板预览 DTO — 展示模板节点内容。
 * <p>
 * 静态模板直接返回节点平铺列表；师范生备课（RULE_BY_STAGE）模板
 * 按学段/主题动态计算并按"课"分组（lessons）。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class PathTemplatePreviewDto {

    private UUID templateId;
    private String code;
    private String name;
    private String description;
    private String icon;
    private String templateType;
    private Integer totalMinutes;
    private Integer nodeCount;
    private Integer lessonCount;

    /** 平铺节点（含 orderIndex 与 lessonIndex） */
    private List<PathTemplateNodeDto> nodes;

    /** 按"课"分组（静态模板每课一个节点；师范生备课按主题聚类） */
    private List<LessonGroup> lessons;

    @Data
    public static class LessonGroup {
        private Integer lessonIndex;
        private String title;
        private Integer estimatedMinutes;
        private List<PathTemplateNodeDto> nodes;
    }
}
