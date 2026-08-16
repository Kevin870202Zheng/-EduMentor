package com.edumentor.course.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "knowledge_points", indexes = {
    @Index(name = "idx_kp_course_id", columnList = "course_id"),
    @Index(name = "idx_kp_parent_kp_id", columnList = "parent_kp_id"),
    @Index(name = "idx_kp_subject", columnList = "subject"),
    @Index(name = "idx_kp_course_order", columnList = "course_id, order_index")
})
public class KnowledgePoint extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "parent_kp_id")
    private UUID parentKpId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private Integer difficulty = 3;

    @Column(nullable = false)
    private Integer importance = 3;

    @Column(length = 64)
    private String subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String tags;

    @Column(name = "type", nullable = false, length = 16)
    private String type = "LEAF";

    @Column(name = "sequence_path", length = 32)
    private String sequencePath;

    @Column(name = "order_index")
    private Integer orderIndex = 0;

    /** 所属学段：PRIMARY / JUNIOR / SENIOR / UNIVERSITY（PRD v4.0 §15） */
    @Column(length = 16)
    private String stage;

    /** 认知深度等级 1-5（与 difficulty 正交：depth=认知层次，difficulty=学习难度） */
    @Column(name = "depth_level")
    private Integer depthLevel = 1;

    /** 所属跨学段主题（subject_themes.id） */
    @Column(name = "theme_id")
    private UUID themeId;

    /** 学段内排序 */
    @Column(name = "stage_order")
    private Integer stageOrder = 0;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("courseId", courseId);
        dto.put("parentKpId", parentKpId);
        dto.put("name", name);
        dto.put("description", description);
        dto.put("content", content);
        dto.put("difficulty", difficulty);
        dto.put("importance", importance);
        dto.put("subject", subject);
        dto.put("tags", tags);
        dto.put("type", type);
        dto.put("sequencePath", sequencePath);
        dto.put("orderIndex", orderIndex);
        dto.put("stage", stage);
        dto.put("depthLevel", depthLevel);
        dto.put("themeId", themeId);
        dto.put("stageOrder", stageOrder);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
