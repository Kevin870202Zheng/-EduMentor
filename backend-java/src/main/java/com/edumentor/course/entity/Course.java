package com.edumentor.course.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "courses", indexes = {
    @Index(name = "idx_courses_created_by", columnList = "created_by"),
    @Index(name = "idx_courses_subject", columnList = "subject")
})
public class Course extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 64)
    private String subject;

    @Column(name = "grade_level", length = 32)
    private String gradeLevel;

    /** 所属学段：PRIMARY / JUNIOR / SENIOR / UNIVERSITY（PRD v4.0 §15） */
    @Column(length = 16)
    private String stage;

    @Column(name = "cover_url", length = 512)
    private String coverUrl;

    @Column(name = "course_code", nullable = false, unique = true, length = 32)
    private String courseCode;

    @Column(name = "is_published")
    private Boolean isPublished = false;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("courseCode", courseCode);
        dto.put("name", name);
        dto.put("description", description);
        dto.put("subject", subject);
        dto.put("gradeLevel", gradeLevel);
        dto.put("stage", stage);
        dto.put("coverUrl", coverUrl);
        dto.put("isPublished", isPublished);
        dto.put("createdBy", createdBy);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
