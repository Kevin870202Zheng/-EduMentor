package com.edumentor.review.entity;

import com.edumentor.entity.BaseEntity;
import com.edumentor.entity.enums.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "error_records", indexes = {
    @Index(name = "idx_er_student_kp", columnList = "student_id, knowledge_point_id"),
    @Index(name = "idx_er_student_reviewed", columnList = "student_id, is_reviewed"),
    @Index(name = "idx_er_kp_id", columnList = "knowledge_point_id"),
    @Index(name = "idx_er_question_id", columnList = "question_id"),
    @Index(name = "idx_er_error_type", columnList = "error_type"),
    @Index(name = "idx_er_student_created", columnList = "student_id, created_at")
})
public class ErrorRecord extends BaseEntity {

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "knowledge_point_id", nullable = false)
    private UUID knowledgePointId;

    @Column(name = "knowledge_point_name", length = 255)
    private String knowledgePointName;

    @Column(name = "question_content", columnDefinition = "text")
    private String questionContent;

    @Column(name = "student_answer", columnDefinition = "text")
    private String studentAnswer;

    @Column(name = "correct_answer", columnDefinition = "text")
    private String correctAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 32)
    private ErrorType errorType;

    @Column(name = "error_analysis", columnDefinition = "text")
    private String errorAnalysis;

    @Column(name = "review_suggestion", columnDefinition = "text")
    private String reviewSuggestion;

    @Column(nullable = false)
    private Integer difficulty = 3;

    @Column(name = "is_reviewed")
    private Boolean isReviewed = false;

    @Column(name = "review_accuracy", precision = 5, scale = 2)
    private BigDecimal reviewAccuracy;

    @Column(name = "error_count")
    private Integer errorCount = 1;

    @Column(columnDefinition = "jsonb", insertable = false, updatable = false)
    private String metadata;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("questionId", questionId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("knowledgePointName", knowledgePointName);
        dto.put("questionContent", questionContent);
        dto.put("studentAnswer", studentAnswer);
        dto.put("correctAnswer", correctAnswer);
        dto.put("errorType", errorType != null ? errorType.name() : null);
        dto.put("errorAnalysis", errorAnalysis);
        dto.put("reviewSuggestion", reviewSuggestion);
        dto.put("difficulty", difficulty);
        dto.put("isReviewed", isReviewed);
        dto.put("reviewAccuracy", reviewAccuracy);
        dto.put("errorCount", errorCount);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
