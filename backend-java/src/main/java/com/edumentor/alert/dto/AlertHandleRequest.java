package com.edumentor.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 预警处理请求 DTO — 教师处理预警时提交的请求体。
 * <p>
 * 支持单条处理和批量处理两种模式：
 * <ul>
 *   <li>单条处理：通过 {@code alertId} 指定单条预警</li>
 *   <li>批量处理：通过 {@code alertIds} 列表批量处理多条预警</li>
 * </ul>
 * </p>
 *
 * @author EduMentor Team
 */
public class AlertHandleRequest {

    /** 要处理的预警 ID（单条处理时使用） */
    private UUID alertId;

    /** 批量处理时的预警 ID 列表 */
    private List<UUID> alertIds;

    /** 处理人 ID（教师或管理员） */
    @NotNull(message = "处理人 ID 不能为空")
    private UUID resolvedBy;

    /** 处理方式：RESOLVE（解决）/ DISMISS（忽略）/ ESCALATE（升级） */
    @NotBlank(message = "处理方式不能为空")
    private String action;

    /** 处理备注/建议 */
    private String note;

    // ─── Getter / Setter ───

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public List<UUID> getAlertIds() {
        return alertIds;
    }

    public void setAlertIds(List<UUID> alertIds) {
        this.alertIds = alertIds;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(UUID resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
