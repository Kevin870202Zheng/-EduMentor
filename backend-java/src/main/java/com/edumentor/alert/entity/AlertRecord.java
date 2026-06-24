package com.edumentor.alert.entity;

import com.edumentor.entity.BaseEntity;
import com.edumentor.entity.enums.AlertSeverity;
import com.edumentor.entity.enums.AlertType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "alert_records", indexes = {
    @Index(name = "idx_alert_student", columnList = "student_id"),
    @Index(name = "idx_alert_active", columnList = "is_resolved"),
    @Index(name = "idx_alert_severity", columnList = "severity"),
    @Index(name = "idx_alert_teacher", columnList = "teacher_id"),
    @Index(name = "idx_alert_type_severity", columnList = "alert_type, severity"),
    @Index(name = "idx_alert_created_at", columnList = "created_at")
})
public class AlertRecord extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertSeverity severity;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "trigger_data", columnDefinition = "jsonb")
    private String triggerData;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "is_resolved")
    private Boolean isResolved = false;

    @Column(name = "handle_note", columnDefinition = "text")
    private String handleNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime resolvedAt;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime expiresAt;

    /** JPA 要求的无参构造 */
    public AlertRecord() {
    }

    /**
     * 创建预警记录的便捷构造。
     *
     * @param studentId  学生 ID
     * @param alertType  预警类型
     * @param severity   预警级别
     * @param title      预警标题
     * @param description 预警详情
     */
    public AlertRecord(UUID studentId, AlertType alertType, AlertSeverity severity,
                       String title, String description) {
        this.studentId = studentId;
        this.alertType = alertType;
        this.severity = severity;
        this.title = title;
        this.description = description;
    }

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("teacherId", teacherId);
        dto.put("alertType", alertType != null ? alertType.name() : null);
        dto.put("severity", severity != null ? severity.name() : null);
        dto.put("title", title);
        dto.put("description", description);
        dto.put("isRead", isRead);
        dto.put("isResolved", isResolved);
        dto.put("handleNote", handleNote);
        dto.put("resolvedBy", resolvedBy);
        dto.put("resolvedAt", resolvedAt);
        dto.put("expiresAt", expiresAt);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
