package com.edumentor.student.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "student_profiles")
public class StudentProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(length = 32)
    private String grade;

    @Column(length = 64)
    private String school;

    @Column(name = "target_school", length = 64)
    private String targetSchool;

    @Column(name = "exam_date")
    private java.time.LocalDate examDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weak_areas", columnDefinition = "jsonb")
    private String weakAreas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", columnDefinition = "jsonb")
    private String strengths;

    @Column(name = "learning_style", length = 32)
    private String learningStyle;

    @Column(name = "daily_study_minutes")
    private Integer dailyStudyMinutes;

    @Column(name = "learning_efficiency", precision = 5, scale = 2)
    private BigDecimal learningEfficiency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bkt_state", columnDefinition = "jsonb")
    private String bktState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("userId", userId);
        dto.put("grade", grade);
        dto.put("school", school);
        dto.put("targetSchool", targetSchool);
        dto.put("examDate", examDate);
        dto.put("weakAreas", weakAreas);
        dto.put("strengths", strengths);
        dto.put("learningStyle", learningStyle);
        dto.put("dailyStudyMinutes", dailyStudyMinutes);
        dto.put("learningEfficiency", learningEfficiency);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
