package com.edumentor.record.entity;

import com.edumentor.entity.BaseEntity;
import com.edumentor.entity.enums.QuestionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(columnDefinition = "jsonb")
    private String options;

    @Column(name = "correct_answer", nullable = false, columnDefinition = "text")
    private String correctAnswer;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(nullable = false)
    private Integer difficulty = 3;

    @Column(columnDefinition = "jsonb")
    private String tags;

    @Column(name = "created_by")
    private UUID createdBy;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("courseId", courseId);
        dto.put("questionType", questionType != null ? questionType.name() : null);
        dto.put("content", content);
        dto.put("options", options);
        dto.put("explanation", explanation);
        dto.put("difficulty", difficulty);
        dto.put("tags", tags);
        dto.put("createdBy", createdBy);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
