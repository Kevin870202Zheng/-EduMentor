package com.edumentor.classroom.entity;

import com.edumentor.classroom.entity.enums.CollabRoleType;
import com.edumentor.classroom.entity.enums.CollabTaskStatus;
import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 学段协作课堂 — 协作任务（设计文档 §5.3）。
 * 每个项目四个角色各一个任务（UNIQUE project_id + role_type），
 * content 为 JSON 文本，不同角色结构不同：
 * <ul>
 *   <li>STORY_PICKER: {"storyId":"...","reason":"..."}</li>
 *   <li>CHARACTER_DESIGNER: {"characters":"..."}</li>
 *   <li>SCRIPT_WRITER: {"script":"..."}</li>
 *   <li>LEGAL_MAPPER: {"knowledgePointIds":["..."],"mapping":"..."}</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "collab_project_tasks", indexes = {
    @Index(name = "idx_collab_task_project", columnList = "project_id"),
    @Index(name = "idx_collab_task_assigned", columnList = "assigned_user_id"),
    @Index(name = "idx_collab_task_status", columnList = "status")
})
public class CollabProjectTask extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 24)
    private CollabRoleType roleType;

    /** 任务要求的学段（PRIMARY/JUNIOR/SENIOR/UNIVERSITY） */
    @Column(name = "required_stage", nullable = false, length = 16)
    private String requiredStage;

    /** 被邀学生（users.id） */
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    /** 回复内容（JSON 文本） */
    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CollabTaskStatus status = CollabTaskStatus.PENDING;

    @Column(name = "submitted_at", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime reviewedAt;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("projectId", projectId);
        dto.put("roleType", roleType != null ? roleType.name() : null);
        dto.put("requiredStage", requiredStage);
        dto.put("assignedUserId", assignedUserId);
        dto.put("content", content);
        dto.put("status", status != null ? status.name() : null);
        dto.put("submittedAt", submittedAt);
        dto.put("reviewedAt", reviewedAt);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
