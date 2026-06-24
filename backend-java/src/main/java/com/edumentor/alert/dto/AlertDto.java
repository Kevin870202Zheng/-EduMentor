package com.edumentor.alert.dto;

import com.edumentor.alert.entity.AlertRecord;
import com.edumentor.entity.enums.AlertSeverity;
import com.edumentor.entity.enums.AlertType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 预警记录 DTO — 用于 API 响应的预警信息数据传输对象。
 * <p>
 * 从 {@link AlertRecord} 实体转换而来，不包含内部审计字段，
 * 始终包含预警关键信息，便于前端展示。
 * </p>
 *
 * @author EduMentor Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertDto {

    private UUID id;
    private UUID studentId;
    private UUID teacherId;
    private AlertType alertType;
    private AlertSeverity severity;
    private String title;
    private String description;
    private String triggerData;
    private boolean isRead;
    private boolean isResolved;
    private String handleNote;
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─── 扩展字段（非实体直接映射） ───

    /** 学生姓名（聚合查询时使用） */
    private String studentName;

    /** 处理人姓名（聚合查询时使用） */
    private String resolverName;

    /**
     * 从 AlertRecord 实体转换为 DTO。
     *
     * @param record 预警记录实体
     * @return 预警 DTO
     */
    public static AlertDto fromEntity(AlertRecord record) {
        if (record == null) {
            return null;
        }
        AlertDto dto = new AlertDto();
        dto.setId(record.getId());
        dto.setStudentId(record.getStudentId());
        dto.setTeacherId(record.getTeacherId());
        dto.setAlertType(record.getAlertType());
        dto.setSeverity(record.getSeverity());
        dto.setTitle(record.getTitle());
        dto.setDescription(record.getDescription());
        dto.setTriggerData(record.getTriggerData());
        dto.setRead(record.getIsRead());
        dto.setResolved(record.getIsResolved());
        dto.setHandleNote(record.getHandleNote());
        dto.setResolvedBy(record.getResolvedBy());
        dto.setResolvedAt(record.getResolvedAt());
        dto.setExpiresAt(record.getExpiresAt());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        return dto;
    }

    // ─── Getter / Setter ───

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(UUID teacherId) {
        this.teacherId = teacherId;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTriggerData() {
        return triggerData;
    }

    public void setTriggerData(String triggerData) {
        this.triggerData = triggerData;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public boolean isResolved() {
        return isResolved;
    }

    public void setResolved(boolean resolved) {
        isResolved = resolved;
    }

    public String getHandleNote() {
        return handleNote;
    }

    public void setHandleNote(String handleNote) {
        this.handleNote = handleNote;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(UUID resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getResolverName() {
        return resolverName;
    }

    public void setResolverName(String resolverName) {
        this.resolverName = resolverName;
    }
}
