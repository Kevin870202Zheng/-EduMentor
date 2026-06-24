package com.edumentor.notification.dto;

import com.edumentor.notification.entity.Notification;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通知 DTO — 用于 API 响应。
 *
 * @author EduMentor Team
 */
@Data
public class NotificationDto {

    private UUID id;
    private UUID userId;
    private String notifType;
    private String title;
    private String content;
    private String priority;
    private Boolean isRead;
    private LocalDateTime createdAt;

    /**
     * 从实体构建 DTO。
     *
     * @param entity 通知实体
     * @return 通知 DTO
     */
    public static NotificationDto fromEntity(Notification entity) {
        NotificationDto dto = new NotificationDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setNotifType(entity.getNotifType());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setPriority(entity.getPriority());
        dto.setIsRead(entity.getIsRead());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
