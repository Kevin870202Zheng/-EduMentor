package com.edumentor.course.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(columnDefinition = "jsonb")
    private String tags;

    @Column(name = "order_index")
    private Integer orderIndex = 0;

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
        dto.put("orderIndex", orderIndex);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
