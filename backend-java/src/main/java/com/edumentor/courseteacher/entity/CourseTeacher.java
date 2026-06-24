package com.edumentor.courseteacher.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 课程教师分配 — 授课教师、辅导教师、助教等多角色支持。
 */
@Getter
@Setter
@Entity
@Table(name = "course_teachers", indexes = {
    @Index(name = "idx_ct_course", columnList = "course_id"),
    @Index(name = "idx_ct_teacher", columnList = "teacher_id")
})
public class CourseTeacher extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(nullable = false, length = 16)
    private String role = "lecturer";

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", getId());
        dto.put("courseId", courseId);
        dto.put("teacherId", teacherId);
        dto.put("role", role);
        return dto;
    }
}
