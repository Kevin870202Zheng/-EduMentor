package com.edumentor.peer.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 考核题目关联 — 考核中包含哪些题目（复用 questions 表）。
 */
@Getter
@Setter
@Entity
@Table(name = "peer_quiz_questions", indexes = {
    @Index(name = "idx_pqq_quiz", columnList = "quiz_id")
})
public class PeerQuizQuestion extends BaseEntity {

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("quizId", quizId);
        dto.put("questionId", questionId);
        dto.put("orderIndex", orderIndex);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
