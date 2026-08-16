package com.edumentor.classroom.entity;

import com.edumentor.classroom.entity.enums.ClassroomStatus;
import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 课堂主表。
 * 一个知识点可以对应多个课堂版本（不同难度/学情适配）。
 * 由 LLM 生成管线自动创建，包含多个教学场景 (Scene)。
 */
@Getter
@Setter
@Entity
@Table(name = "classrooms", indexes = {
    @Index(name = "idx_classrooms_course", columnList = "course_id"),
    @Index(name = "idx_classrooms_kp", columnList = "knowledge_point_id"),
    @Index(name = "idx_classrooms_course_kp", columnList = "course_id, knowledge_point_id")
})
public class Classroom extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** 关联知识点（聚合/协作课堂可为空，见设计文档 §5.3） */
    @Column(name = "knowledge_point_id")
    private UUID knowledgePointId;

    /** 生成来源：knowledge（单知识点）/ multi_knowledge（多知识点聚合）/ collaborative（学段协作） */
    @Column(nullable = false, length = 16)
    private String source = "knowledge";

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Integer difficulty = 3;

    @Column(name = "total_duration_seconds")
    private Integer totalDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClassroomStatus status = ClassroomStatus.draft;

    @Column(name = "scene_count", nullable = false)
    private Integer sceneCount = 0;

    @Column(nullable = false)
    private Integer version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("courseId", courseId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("source", source);
        dto.put("title", title);
        dto.put("description", description);
        dto.put("difficulty", difficulty);
        dto.put("totalDurationSeconds", totalDurationSeconds);
        dto.put("status", status != null ? status.name() : null);
        dto.put("sceneCount", sceneCount);
        dto.put("version", version);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
