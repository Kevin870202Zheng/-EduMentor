package com.edumentor.classroom.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 课堂内 Quiz 作答记录。
 * 存储学生在课堂场景中回答题目的数据。
 * 与 BKT 引擎联动，记录提交后触发 BKT 状态更新。
 */
@Getter
@Setter
@Entity
@Table(name = "scene_quiz_records", indexes = {
    @Index(name = "idx_sqr_student", columnList = "student_id"),
    @Index(name = "idx_sqr_scene", columnList = "scene_id"),
    @Index(name = "idx_sqr_kp", columnList = "knowledge_point_id"),
    @Index(name = "idx_sqr_student_scene", columnList = "student_id, scene_id")
})
public class SceneQuizRecord extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "scene_id", nullable = false)
    private UUID sceneId;

    @Column(name = "knowledge_point_id")
    private UUID knowledgePointId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiz_data", columnDefinition = "jsonb", nullable = false)
    private String quizData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "student_answer", columnDefinition = "jsonb")
    private String studentAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "ai_feedback", columnDefinition = "text")
    private String aiFeedback;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("sceneId", sceneId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("quizData", quizData);
        dto.put("studentAnswer", studentAnswer);
        dto.put("isCorrect", isCorrect);
        dto.put("aiFeedback", aiFeedback);
        dto.put("attemptCount", attemptCount);
        dto.put("answeredAt", answeredAt);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
