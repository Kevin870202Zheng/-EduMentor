package com.edumentor.classroom.entity;

import com.edumentor.classroom.entity.enums.ProgressStatus;
import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 学生学习课堂的进度记录。
 * 支持断点续播：记录当前播放到的场景和动作位置。
 * 每个学生在一个课堂上只有一条进度记录。
 */
@Getter
@Setter
@Entity
@Table(name = "classroom_progress", uniqueConstraints = {
    @UniqueConstraint(name = "uq_cp_student_classroom", columnNames = {"student_id", "classroom_id"})
}, indexes = {
    @Index(name = "idx_cp_student", columnList = "student_id"),
    @Index(name = "idx_cp_classroom", columnList = "classroom_id"),
    @Index(name = "idx_cp_student_status", columnList = "student_id, status")
})
public class ClassroomProgress extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProgressStatus status = ProgressStatus.not_started;

    @Column(name = "current_scene_id")
    private UUID currentSceneId;

    @Column(name = "current_action_order", nullable = false)
    private Integer currentActionOrder = 0;

    @Column(name = "scenes_completed", nullable = false)
    private Integer scenesCompleted = 0;

    @Column(name = "total_scenes", nullable = false)
    private Integer totalScenes = 0;

    @Column(name = "quiz_correct_count", nullable = false)
    private Integer quizCorrectCount = 0;

    @Column(name = "quiz_total_count", nullable = false)
    private Integer quizTotalCount = 0;

    @Column(name = "total_watch_seconds", nullable = false)
    private Integer totalWatchSeconds = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("classroomId", classroomId);
        dto.put("status", status != null ? status.name() : null);
        dto.put("currentSceneId", currentSceneId);
        dto.put("currentActionOrder", currentActionOrder);
        dto.put("scenesCompleted", scenesCompleted);
        dto.put("totalScenes", totalScenes);
        dto.put("quizCorrectCount", quizCorrectCount);
        dto.put("quizTotalCount", quizTotalCount);
        dto.put("totalWatchSeconds", totalWatchSeconds);
        dto.put("startedAt", startedAt);
        dto.put("completedAt", completedAt);
        dto.put("lastAccessedAt", lastAccessedAt);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
