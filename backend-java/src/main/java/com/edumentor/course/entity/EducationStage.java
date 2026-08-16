package com.edumentor.course.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 学段定义实体（PRD v4.0 §5 / §15）。
 * <p>
 * 对应表 {@code education_stages}，存储小学/初中/高中/大学四学段定义，
 * 每个学段包含其覆盖的认知深度范围（minDepth ~ maxDepth）。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Getter
@Setter
@Entity
@Table(name = "education_stages", indexes = {
    @Index(name = "idx_stages_code", columnList = "code", unique = true),
    @Index(name = "idx_stages_sort_order", columnList = "sort_order")
})
public class EducationStage extends BaseEntity {

    /** 学段代码：PRIMARY / JUNIOR / SENIOR / UNIVERSITY */
    @Column(nullable = false, unique = true, length = 16)
    private String code;

    /** 学段名称：小学 / 初中 / 高中 / 大学 */
    @Column(nullable = false, length = 32)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** 最小认知深度 */
    @Column(name = "min_depth", nullable = false)
    private Integer minDepth = 1;

    /** 最大认知深度 */
    @Column(name = "max_depth", nullable = false)
    private Integer maxDepth = 5;

    /** 学段排序（小学1 → 大学4） */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("code", code);
        dto.put("name", name);
        dto.put("description", description);
        dto.put("minDepth", minDepth);
        dto.put("maxDepth", maxDepth);
        dto.put("sortOrder", sortOrder);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
