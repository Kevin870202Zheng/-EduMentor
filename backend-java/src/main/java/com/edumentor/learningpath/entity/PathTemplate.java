package com.edumentor.learningpath.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 路径模板实体 — 预设学习路径模板定义。
 * <p>
 * 支持四类模板：EXAM（课程考试）/ LITIGATION（纠纷解决）/
 * INTEREST（兴趣拓展）/ TEACHING（师范生备课）。
 * templateType=STATIC 时节点存于 path_template_nodes（静态快照）；
 * templateType=RULE_BY_STAGE 时按学段/主题动态计算节点（不落节点表）。
 * </p>
 *
 * @author EduMentor Team
 */
@Getter
@Setter
@Entity
@Table(name = "path_templates", indexes = {
    @Index(name = "idx_pt_course", columnList = "course_id")
})
public class PathTemplate extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** 模板代码：EXAM / LITIGATION / INTEREST / TEACHING */
    @Column(nullable = false, length = 32)
    private String code;

    /** 模板名称：课程考试 / 纠纷解决 / 兴趣拓展 / 师范生备课 */
    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** 卡片图标（emoji） */
    @Column(length = 32)
    private String icon;

    /** 总课时（分钟）；INTEREST 为 null=不设限 */
    @Column(name = "total_minutes")
    private Integer totalMinutes;

    /** 静态模板节点数（冗余统计） */
    @Column(name = "node_count", nullable = false)
    private Integer nodeCount = 0;

    /** 是否展示在推荐卡片区 */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible = true;

    /** STATIC / RULE_BY_STAGE */
    @Column(name = "template_type", nullable = false, length = 16)
    private String templateType = "STATIC";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", getId());
        dto.put("courseId", courseId);
        dto.put("code", code);
        dto.put("name", name);
        dto.put("description", description);
        dto.put("icon", icon);
        dto.put("totalMinutes", totalMinutes);
        dto.put("nodeCount", nodeCount);
        dto.put("isVisible", isVisible);
        dto.put("templateType", templateType);
        dto.put("sortOrder", sortOrder);
        return dto;
    }
}
