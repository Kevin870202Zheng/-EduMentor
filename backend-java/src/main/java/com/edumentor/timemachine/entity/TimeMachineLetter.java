package com.edumentor.timemachine.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 来自过去的信（成长时光机）— 跨学段自我对话。
 * <p>
 * 学生可创建「寄给未来/来自过去」的信件：AI 基于历史薄弱点生成提问，
 * 学生以现在的视角回信，沉淀为成长记录。
 * </p>
 */
@Getter
@Setter
@Entity
@Table(name = "time_machine_letters", indexes = {
    @Index(name = "idx_tml_student", columnList = "student_id, created_at DESC")
})
public class TimeMachineLetter extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** 写信人所在学段（过去的自己）：PRIMARY/JUNIOR/SENIOR/UNIVERSITY */
    @Column(length = 16)
    private String stage;

    @Column(name = "course_id")
    private UUID courseId;

    /** PAST_TO_NOW（过去→现在）| NOW_TO_FUTURE（现在→未来） */
    @Column(length = 16, nullable = false)
    private String direction = "PAST_TO_NOW";

    /** 来自过去的提问 */
    @Column(nullable = false, columnDefinition = "text")
    private String question;

    /** 现在的回答 */
    @Column(columnDefinition = "text")
    private String answer;

    /** 提问是否由 AI 生成 */
    @Column(name = "ai_generated", nullable = false)
    private Boolean aiGenerated = true;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("stage", stage);
        dto.put("courseId", courseId);
        dto.put("direction", direction);
        dto.put("question", question);
        dto.put("answer", answer);
        dto.put("aiGenerated", aiGenerated);
        dto.put("answeredAt", answeredAt);
        dto.put("answered", answeredAt != null);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
