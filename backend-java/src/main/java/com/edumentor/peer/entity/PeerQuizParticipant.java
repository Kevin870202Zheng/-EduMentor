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
 * 考核参与学生 — 哪些学生参与考核，以及完成状态和成绩。
 */
@Getter
@Setter
@Entity
@Table(name = "peer_quiz_participants", indexes = {
    @Index(name = "idx_pqp_quiz", columnList = "quiz_id"),
    @Index(name = "idx_pqp_student", columnList = "student_id"),
    @Index(name = "idx_pqp_status", columnList = "status")
})
public class PeerQuizParticipant extends BaseEntity {

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column
    private Integer score;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime completedAt;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("quizId", quizId);
        dto.put("studentId", studentId);
        dto.put("status", status);
        dto.put("score", score);
        dto.put("totalQuestions", totalQuestions);
        dto.put("completedAt", completedAt);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
