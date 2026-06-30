package com.edumentor.course.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 课程原始资料 — 教师上传后经 AI 提取为结构化知识点和习题。
 */
@Getter
@Setter
@Entity
@Table(name = "course_materials", indexes = {
    @Index(name = "idx_cm_course_id", columnList = "course_id"),
    @Index(name = "idx_cm_course_code", columnList = "course_code"),
    @Index(name = "idx_cm_status", columnList = "status")
})
public class CourseMaterial extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "course_code", nullable = false, length = 32)
    private String courseCode;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "file_type", length = 20)
    private String fileType;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_result", columnDefinition = "JSONB")
    private String extractionResult;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", getId());
        dto.put("courseId", courseId);
        dto.put("courseCode", courseCode);
        dto.put("title", title);
        dto.put("fileType", fileType);
        dto.put("status", status);
        dto.put("textLength", rawText != null ? rawText.length() : 0);
        dto.put("hasExtractionResult", extractionResult != null);
        dto.put("errorMessage", errorMessage);
        dto.put("createdBy", createdBy);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
