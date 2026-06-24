package com.edumentor.record.entity;

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
@Table(name = "answer_records", indexes = {
    @Index(name = "idx_ar_student_kp", columnList = "student_id, knowledge_point_id"),
    @Index(name = "idx_ar_student_correct", columnList = "student_id, is_correct"),
    @Index(name = "idx_ar_attempted_at", columnList = "student_id, attempted_at DESC")
})
public class AnswerRecord extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "knowledge_point_id", nullable = false)
    private UUID knowledgePointId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "student_answer", columnDefinition = "text")
    private String studentAnswer;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "attempted_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private java.time.LocalDateTime attemptedAt;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("questionId", questionId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("courseId", courseId);
        dto.put("isCorrect", isCorrect);
        dto.put("studentAnswer", studentAnswer);
        dto.put("timeSpentSeconds", timeSpentSeconds);
        dto.put("attemptedAt", attemptedAt);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
