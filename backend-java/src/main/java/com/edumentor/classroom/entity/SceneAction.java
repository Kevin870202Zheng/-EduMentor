package com.edumentor.classroom.entity;

import com.edumentor.classroom.entity.enums.ActionType;
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
 * 教学动作实体。
 * 每个场景包含一系列按顺序执行的教学动作（讲解、白板、Quiz等）。
 * params_json 存储动作的具体参数。
 */
@Getter
@Setter
@Entity
@Table(name = "scene_actions", indexes = {
    @Index(name = "idx_scene_actions_scene", columnList = "scene_id"),
    @Index(name = "idx_scene_actions_scene_order", columnList = "scene_id, order_index")
})
public class SceneAction extends BaseEntity {

    @Column(name = "scene_id", nullable = false)
    private UUID sceneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private ActionType actionType;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_json", columnDefinition = "jsonb", nullable = false)
    private String paramsJson = "{}";

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("sceneId", sceneId);
        dto.put("actionType", actionType != null ? actionType.name() : null);
        dto.put("orderIndex", orderIndex);
        dto.put("paramsJson", paramsJson);
        dto.put("durationMs", durationMs);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
