package com.edumentor.learningpath.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "learning_paths", indexes = {
    @Index(name = "idx_lp_student", columnList = "student_id"),
    @Index(name = "idx_lp_status", columnList = "status"),
    @Index(name = "idx_lp_student_status", columnList = "student_id, status")
})
public class LearningPath extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PathStatus status = PathStatus.DRAFT;

    @Column(nullable = false)
    private Integer progress = 0;

    @Column(name = "total_nodes")
    private Integer totalNodes = 0;

    @Column(name = "completed_nodes")
    private Integer completedNodes = 0;

    @Column(name = "daily_minutes")
    private Integer dailyMinutes;

    @OneToMany(mappedBy = "learningPath", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<LearningPathNode> nodes = new ArrayList<>();

    @Column(name = "created_by")
    private UUID createdBy;

    public void recalculateProgress() {
        if (nodes == null || nodes.isEmpty()) {
            progress = 0;
            totalNodes = 0;
            completedNodes = 0;
            return;
        }
        totalNodes = nodes.size();
        completedNodes = (int) nodes.stream()
            .filter(n -> n.getStatus() == PathNodeStatus.COMPLETED)
            .count();
        progress = (totalNodes > 0) ? (completedNodes * 100 / totalNodes) : 0;
    }

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("courseId", courseId);
        dto.put("name", name);
        dto.put("description", description);
        dto.put("status", status != null ? status.name() : null);
        dto.put("progress", progress);
        dto.put("totalNodes", totalNodes);
        dto.put("completedNodes", completedNodes);
        dto.put("dailyMinutes", dailyMinutes);
        dto.put("createdBy", createdBy);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        if (nodes != null) {
            dto.put("nodes", nodes.stream().map(LearningPathNode::toDto).toList());
        }
        return dto;
    }
}
