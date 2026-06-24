package com.edumentor.notification.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 通知实体 — 持久化存储用户通知（站内信）。
 *
 * <p>通知的推送渠道包含 WebSocket 实时推送和数据库持久化存储。
 * 用户离线时可通过 REST API 查询未读通知。</p>
 *
 * @author EduMentor Team
 */
@Getter
@Setter
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_user_read", columnList = "user_id, is_read"),
    @Index(name = "idx_notif_created", columnList = "user_id, created_at"),
    @Index(name = "idx_notif_type", columnList = "notif_type")
})
public class Notification extends BaseEntity {

    /** 目标用户 ID */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 通知类型 */
    @Column(name = "notif_type", nullable = false, length = 32)
    private String notifType;

    /** 通知标题 */
    @Column(nullable = false, length = 256)
    private String title;

    /** 通知内容 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 优先级 */
    @Column(nullable = false, length = 16)
    private String priority = "normal";

    /** 是否已读 */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    /** 附加元数据（JSON 格式） */
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("userId", userId);
        dto.put("notifType", notifType);
        dto.put("title", title);
        dto.put("content", content);
        dto.put("priority", priority);
        dto.put("isRead", isRead);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
