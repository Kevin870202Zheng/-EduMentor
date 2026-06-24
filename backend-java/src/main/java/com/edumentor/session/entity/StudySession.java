package com.edumentor.session.entity;

import com.edumentor.entity.BaseEntity;
import com.edumentor.entity.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "study_sessions", indexes = {
    @Index(name = "idx_ss_student", columnList = "student_id"),
    @Index(name = "idx_ss_active", columnList = "student_id, status"),
    @Index(name = "idx_ss_start_time", columnList = "student_id, start_time DESC")
})
public class StudySession extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "knowledge_point_id")
    private UUID knowledgePointId;

    @Column(name = "learning_path_node_id")
    private UUID learningPathNodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "start_time", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime startTime;

    @Column(name = "end_time", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime endTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "questions_answered")
    private Integer questionsAnswered = 0;

    @Column(name = "correct_count")
    private Integer correctCount = 0;

    @Column(name = "focus_score")
    private Double focusScore;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "interrupt_reason", columnDefinition = "text")
    private String interruptReason;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("learningPathNodeId", learningPathNodeId);
        dto.put("status", status != null ? status.name() : null);
        dto.put("startTime", startTime);
        dto.put("endTime", endTime);
        dto.put("durationSeconds", durationSeconds);
        dto.put("questionsAnswered", questionsAnswered);
        dto.put("correctCount", correctCount);
        dto.put("focusScore", focusScore);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
