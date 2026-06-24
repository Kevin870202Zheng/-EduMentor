package com.edumentor.enrollment.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 学生选课关联 — 学生与课程的多对多关系。
 */
@Getter
@Setter
@Entity
@Table(name = "student_courses", indexes = {
    @Index(name = "idx_sc_student", columnList = "student_id"),
    @Index(name = "idx_sc_course", columnList = "course_id"),
    @Index(name = "idx_sc_student_status", columnList = "student_id, status")
})
public class StudentCourse extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "course_code", nullable = false, length = 32)
    private String courseCode;

    @Column(nullable = false, length = 16)
    private String status = "active";

    @Column(name = "enrolled_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime enrolledAt = LocalDateTime.now();

    @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime completedAt;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("courseId", courseId);
        dto.put("courseCode", courseCode);
        dto.put("status", status);
        dto.put("enrolledAt", enrolledAt);
        dto.put("completedAt", completedAt);
        return dto;
    }
}
