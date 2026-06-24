package com.edumentor.review.dto;

import com.edumentor.review.entity.ReviewRecord;
import com.edumentor.review.entity.enums.ReviewStatus;
import com.edumentor.entity.enums.ReviewType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 复习记录 DTO — 用于 API 响应的复习信息数据传输对象。
 * <p>
 * 从 {@link ReviewRecord} 实体转换而来，包含复习计划详情和执行情况。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class ReviewRecordDto {

    private UUID id;
    private UUID studentId;
    private UUID knowledgePointId;
    private String knowledgePointName;
    private UUID errorRecordId;
    private ReviewType reviewType;
    private ReviewStatus status;
    private Integer reviewCycle;
    private LocalDate scheduledDate;
    private LocalDate completedDate;
    private Integer spentMinutes;
    private Integer effectivenessScore;
    private String notes;
    private LocalDate nextReviewDate;
    private BigDecimal accuracy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从 ReviewRecord 实体转换为 DTO。
     *
     * @param record 复习记录实体
     * @return DTO 对象，若入参为 null 则返回 null
     */
    public static ReviewRecordDto fromEntity(ReviewRecord record) {
        if (record == null) {
            return null;
        }
        ReviewRecordDto dto = new ReviewRecordDto();
        dto.setId(record.getId());
        dto.setStudentId(record.getStudentId());
        dto.setKnowledgePointId(record.getKnowledgePointId());
        dto.setKnowledgePointName(record.getKnowledgePointName());
        dto.setErrorRecordId(record.getErrorRecordId());
        dto.setReviewType(record.getReviewType());
        dto.setStatus(record.getStatus());
        dto.setReviewCycle(record.getReviewCycle());
        dto.setScheduledDate(record.getScheduledDate());
        dto.setCompletedDate(record.getCompletedDate());
        dto.setSpentMinutes(record.getSpentMinutes());
        dto.setEffectivenessScore(record.getEffectivenessScore());
        dto.setNotes(record.getNotes());
        dto.setNextReviewDate(record.getNextReviewDate());
        dto.setAccuracy(record.getAccuracy());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        return dto;
    }
}
