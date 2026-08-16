package com.edumentor.classroom.entity;

import com.edumentor.classroom.entity.enums.CollabProjectStatus;
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
 * 学段协作课堂项目（设计文档 §5.3）。
 * 仅教师可发起；四学段学生作为被邀参与者完成任务，全部完成后教师审阅并触发 AI 生成课堂。
 */
@Getter
@Setter
@Entity
@Table(name = "collab_classroom_projects", indexes = {
    @Index(name = "idx_collab_creator", columnList = "creator_id"),
    @Index(name = "idx_collab_status", columnList = "status"),
    @Index(name = "idx_collab_course", columnList = "course_id")
})
public class CollabClassroomProject extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** 法律知识来源课程（courses.id） */
    @Column(name = "course_id")
    private UUID courseId;

    /** 发起者（教师 users.id） */
    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    /** 选定的故事（story_library.id，由小学学生提交） */
    @Column(name = "story_id")
    private UUID storyId;

    @Column(nullable = false)
    private Integer difficulty = 3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CollabProjectStatus status = CollabProjectStatus.DRAFT;

    /** 生成后的课堂（classrooms.id） */
    @Column(name = "classroom_id")
    private UUID classroomId;

    /** 聚合知识点等扩展信息 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("title", title);
        dto.put("description", description);
        dto.put("courseId", courseId);
        dto.put("creatorId", creatorId);
        dto.put("storyId", storyId);
        dto.put("difficulty", difficulty);
        dto.put("status", status != null ? status.name() : null);
        dto.put("classroomId", classroomId);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
