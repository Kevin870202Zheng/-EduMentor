package com.edumentor.review.entity;

import com.edumentor.entity.BaseEntity;
import com.edumentor.review.entity.enums.ReviewStatus;
import com.edumentor.entity.enums.ReviewType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "review_records", indexes = {
    @Index(name = "idx_rr_student", columnList = "student_id"),
    @Index(name = "idx_rr_scheduled", columnList = "scheduled_date"),
    @Index(name = "idx_rr_next_review", columnList = "student_id, next_review_date"),
    @Index(name = "idx_rr_status", columnList = "student_id, status"),
    @Index(name = "idx_rr_kp_id", columnList = "knowledge_point_id"),
    @Index(name = "idx_rr_student_cycle", columnList = "student_id, review_cycle")
})
public class ReviewRecord extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "knowledge_point_id", nullable = false)
    private UUID knowledgePointId;

    @Column(name = "knowledge_point_name", length = 255)
    private String knowledgePointName;

    @Column(name = "error_record_id")
    private UUID errorRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", length = 32)
    private ReviewType reviewType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "review_cycle")
    private Integer reviewCycle;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "spent_minutes")
    private Integer spentMinutes;

    @Column(name = "effectiveness_score")
    private Integer effectivenessScore;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(precision = 5, scale = 2)
    private BigDecimal accuracy;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("knowledgePointName", knowledgePointName);
        dto.put("errorRecordId", errorRecordId);
        dto.put("reviewType", reviewType != null ? reviewType.name() : null);
        dto.put("status", status != null ? status.name() : null);
        dto.put("reviewCycle", reviewCycle);
        dto.put("scheduledDate", scheduledDate);
        dto.put("completedDate", completedDate);
        dto.put("spentMinutes", spentMinutes);
        dto.put("effectivenessScore", effectivenessScore);
        dto.put("notes", notes);
        dto.put("nextReviewDate", nextReviewDate);
        dto.put("accuracy", accuracy);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
