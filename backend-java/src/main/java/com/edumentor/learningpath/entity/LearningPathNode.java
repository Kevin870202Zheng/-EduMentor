package com.edumentor.learningpath.entity;

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
@Table(name = "learning_path_nodes", indexes = {
    @Index(name = "idx_lpn_path", columnList = "learning_path_id"),
    @Index(name = "idx_lpn_path_order", columnList = "learning_path_id, order_index"),
    @Index(name = "idx_lpn_kp", columnList = "knowledge_point_id")
})
public class LearningPathNode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_path_id", nullable = false)
    private LearningPath learningPath;

    @Column(name = "knowledge_point_id", nullable = false)
    private UUID knowledgePointId;

    @Column(name = "knowledge_point_name", length = 255)
    private String knowledgePointName;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PathNodeStatus status = PathNodeStatus.PENDING;

    @Column(name = "is_recommended")
    private Boolean isRecommended = true;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Column(name = "mastery_threshold")
    private Double masteryThreshold;

    /** AI 选择理由（source=AI 时记录，供学生查看/异议） */
    @Column(name = "ai_reason", columnDefinition = "text")
    private String aiReason;

    public void markInProgress() {
        this.status = PathNodeStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        this.status = PathNodeStatus.COMPLETED;
    }

    public void skip() {
        this.status = PathNodeStatus.SKIPPED;
    }

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("learningPathId", learningPath != null ? learningPath.getId() : null);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("knowledgePointName", knowledgePointName);
        dto.put("orderIndex", orderIndex);
        dto.put("status", status != null ? status.name() : null);
        dto.put("isRecommended", isRecommended);
        dto.put("estimatedMinutes", estimatedMinutes);
        dto.put("actualMinutes", actualMinutes);
        dto.put("masteryThreshold", masteryThreshold);
        dto.put("aiReason", aiReason);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
