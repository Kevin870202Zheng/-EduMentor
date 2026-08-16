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
 * 跨学段主题实体（PRD v4.0 §6 / §15）。
 * <p>
 * 对应表 {@code subject_themes}。主题是跨学段的知识组织单元，
 * 一个主题（如「宪法精神」）贯穿小学、初中、高中、大学四个学段。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Getter
@Setter
@Entity
@Table(name = "subject_themes", indexes = {
    @Index(name = "idx_themes_code", columnList = "code", unique = true),
    @Index(name = "idx_themes_subject", columnList = "subject")
})
public class SubjectTheme extends BaseEntity {

    /** 学科（如：法律） */
    @Column(nullable = false, length = 64)
    private String subject;

    /** 主题代码：LAW_RULE / CONSTITUTION / CIVIL_RIGHTS / ... */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    /** 主题名称：法律与规则 / 宪法精神 / ... */
    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** 主题图标（emoji） */
    @Column(length = 32)
    private String icon;

    /** 展示排序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("subject", subject);
        dto.put("code", code);
        dto.put("name", name);
        dto.put("description", description);
        dto.put("icon", icon);
        dto.put("sortOrder", sortOrder);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
