package com.edumentor.classroom.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 中华传统故事库（设计文档 §6）。
 * 本期预置数据；后续演进为公共知识库，由教师创建维护（createdBy + status 预留）。
 */
@Getter
@Setter
@Entity
@Table(name = "story_library", indexes = {
    @Index(name = "idx_story_theme", columnList = "theme_id"),
    @Index(name = "idx_story_status", columnList = "status")
})
public class StoryLibrary extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 朝代/出处年代 */
    @Column(length = 64)
    private String dynasty;

    /** 出处（如《世说新语》） */
    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    /** 关键词（逗号分隔） */
    @Column(columnDefinition = "text")
    private String keywords;

    /** 建议关联法律主题（subject_themes.id） */
    @Column(name = "theme_id")
    private UUID themeId;

    @Column(nullable = false, length = 16)
    private String status = "published";

    @Column(name = "created_by")
    private UUID createdBy;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("title", title);
        dto.put("content", content);
        dto.put("dynasty", dynasty);
        dto.put("sourceRef", sourceRef);
        dto.put("keywords", keywords);
        dto.put("themeId", themeId);
        dto.put("status", status);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
