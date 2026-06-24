package com.edumentor.review.dto;

import com.edumentor.review.entity.ErrorRecord;
import com.edumentor.entity.enums.ErrorType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 错题记录 DTO — 用于 API 响应的错题信息数据传输对象。
 * <p>
 * 从 {@link ErrorRecord} 实体转换而来，包含错题的详细信息和分析结果。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class ErrorRecordDto {

    private UUID id;
    private UUID studentId;
    private UUID questionId;
    private UUID knowledgePointId;
    private String knowledgePointName;
    private String questionContent;
    private String studentAnswer;
    private String correctAnswer;
    private ErrorType errorType;
    private String errorAnalysis;
    private String reviewSuggestion;
    private Integer difficulty;
    private Boolean isReviewed;
    private BigDecimal reviewAccuracy;
    private Integer errorCount;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从 ErrorRecord 实体转换为 DTO。
     *
     * @param record 错题记录实体
     * @return DTO 对象，若入参为 null 则返回 null
     */
    public static ErrorRecordDto fromEntity(ErrorRecord record) {
        if (record == null) {
            return null;
        }
        ErrorRecordDto dto = new ErrorRecordDto();
        dto.setId(record.getId());
        dto.setStudentId(record.getStudentId());
        dto.setQuestionId(record.getQuestionId());
        dto.setKnowledgePointId(record.getKnowledgePointId());
        dto.setKnowledgePointName(record.getKnowledgePointName());
        dto.setQuestionContent(record.getQuestionContent());
        dto.setStudentAnswer(record.getStudentAnswer());
        dto.setCorrectAnswer(record.getCorrectAnswer());
        dto.setErrorType(record.getErrorType());
        dto.setErrorAnalysis(record.getErrorAnalysis());
        dto.setReviewSuggestion(record.getReviewSuggestion());
        dto.setDifficulty(record.getDifficulty());
        dto.setIsReviewed(record.getIsReviewed());
        dto.setReviewAccuracy(record.getReviewAccuracy());
        dto.setErrorCount(record.getErrorCount());
        dto.setMetadata(record.getMetadata());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        return dto;
    }
}
