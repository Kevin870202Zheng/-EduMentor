package com.edumentor.record.entity;

import com.edumentor.entity.BaseEntity;
import com.edumentor.entity.enums.QuestionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "questions", indexes = {
    @Index(name = "idx_q_kp_id", columnList = "knowledge_point_id"),
    @Index(name = "idx_q_course_id", columnList = "course_id")
})
public class Question extends BaseEntity {

    @Column(name = "knowledge_point_id", nullable = false)
    private UUID knowledgePointId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode options;

    @Column(name = "correct_answer", nullable = false, columnDefinition = "text")
    private String correctAnswer;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(nullable = false)
    private Integer difficulty = 3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode tags;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "is_published")
    private Boolean isPublished = false;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("courseId", courseId);
        dto.put("questionType", questionType != null ? questionType.name() : null);
        dto.put("content", content);
        dto.put("options", options);
        dto.put("correctAnswer", correctAnswer);
        dto.put("explanation", explanation);
        dto.put("difficulty", difficulty);
        dto.put("isPublished", isPublished);
        dto.put("tags", tags);
        dto.put("createdBy", createdBy);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
