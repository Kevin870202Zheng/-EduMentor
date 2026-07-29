package com.edumentor.classroom.entity;

import com.edumentor.classroom.entity.enums.SceneType;
import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 教学场景实体。
 * 每个课堂包含 3-8 个教学场景，每个场景包含多个教学动作 (SceneAction)。
 * content_json 存储场景的具体教学内容（结构化 JSON）。
 */
@Getter
@Setter
@Entity
@Table(name = "scenes", indexes = {
    @Index(name = "idx_scenes_classroom", columnList = "classroom_id"),
    @Index(name = "idx_scenes_classroom_order", columnList = "classroom_id, order_index")
})
public class Scene extends BaseEntity {

    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scene_type", nullable = false, length = 16)
    private SceneType sceneType;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @Column(name = "estimated_duration_seconds")
    private Integer estimatedDurationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "jsonb", nullable = false)
    private String contentJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("classroomId", classroomId);
        dto.put("title", title);
        dto.put("description", description);
        dto.put("sceneType", sceneType != null ? sceneType.name() : null);
        dto.put("orderIndex", orderIndex);
        dto.put("estimatedDurationSeconds", estimatedDurationSeconds);
        dto.put("contentJson", contentJson);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
