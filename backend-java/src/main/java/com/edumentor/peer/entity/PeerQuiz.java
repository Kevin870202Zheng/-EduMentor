package com.edumentor.peer.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 学生互出题考核任务 — 一次考核的元信息。
 */
@Getter
@Setter
@Entity
@Table(name = "peer_quizzes", indexes = {
    @Index(name = "idx_pq_creator", columnList = "creator_id"),
    @Index(name = "idx_pq_course", columnList = "course_id"),
    @Index(name = "idx_pq_status", columnList = "status")
})
public class PeerQuiz extends BaseEntity {

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "knowledge_point_id")
    private UUID knowledgePointId;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime deadline;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("creatorId", creatorId);
        dto.put("courseId", courseId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("title", title);
        dto.put("deadline", deadline);
        dto.put("status", status);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
