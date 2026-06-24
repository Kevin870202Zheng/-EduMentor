package com.edumentor.notification.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.notification.dto.CreateNotificationRequest;
import com.edumentor.notification.dto.NotificationDto;
import com.edumentor.notification.entity.Notification;
import com.edumentor.notification.repository.NotificationRepository;
import com.edumentor.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通知推送服务 — WebSocket + 站内信推送。
 *
 * <p>支持的推送渠道：
 * <ol>
 *   <li>WebSocket — 通过 {@link WebSocketSessionManager} 实时推送</li>
 *   <li>站内通知 — 持久化到 {@code notifications} 表供离线查看</li>
 *   <li>外部推送 — 邮件/短信（预留扩展点）</li>
 * </ol>
 * </p>
 *
 * <p>事件类型：
 * <ul>
 *   <li>{@code alert:new} — 新预警触发</li>
 *   <li>{@code mastery:update} — 掌握度变化</li>
 *   <li>{@code progress:sync} — 学习进度同步</li>
 *   <li>{@code notification:push} — 系统通知</li>
 * </ul>
 * </p>
 *
 * @author EduMentor Team
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** 通知类型常量 */
    public static final String TYPE_ALERT_NEW = "alert:new";
    public static final String TYPE_MASTERY_UPDATE = "mastery:update";
    public static final String TYPE_PROGRESS_SYNC = "progress:sync";
    public static final String TYPE_NOTIFICATION = "notification:push";

    /** 优先级常量 */
    public static final String PRIORITY_LOW = "low";
    public static final String PRIORITY_NORMAL = "normal";
    public static final String PRIORITY_HIGH = "high";
    public static final String PRIORITY_URGENT = "urgent";

    private final NotificationRepository notificationRepository;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               WebSocketSessionManager sessionManager,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    // ════════════════════════════════════════════
    //  推送方法
    // ════════════════════════════════════════════

    /**
     * 向指定用户推送实时消息（WebSocket）。
     *
     * @param userId 目标用户 ID
     * @param event  事件名称
     * @param data   消息数据
     */
    public void pushToUser(UUID userId, String event, Object data) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "event", event,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            ));
            boolean sent = sessionManager.sendToUser(userId, message);
            if (sent) {
                log.debug("WebSocket推送成功: userId={}, event={}", userId, event);
            } else {
                log.debug("用户离线，跳过WebSocket推送: userId={}, event={}", userId, event);
            }
        } catch (JsonProcessingException e) {
            log.warn("WebSocket推送序列化失败: {}", e.getMessage());
        }
    }

    /**
     * 向所有在线用户广播消息。
     *
     * @param event 事件名称
     * @param data  消息数据
     */
    public void broadcast(String event, Object data) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "event", event,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            ));
            sessionManager.broadcast(message);
            log.debug("WebSocket广播完成: event={}", event);
        } catch (JsonProcessingException e) {
            log.warn("WebSocket广播序列化失败: {}", e.getMessage());
        }
    }

    /**
     * 推送新预警通知。
     *
     * @param userId    目标用户（学生）ID
     * @param alertData 预警数据
     */
    public void pushAlert(UUID userId, Map<String, Object> alertData) {
        String title = (String) alertData.getOrDefault("title", "新预警");
        String description = (String) alertData.getOrDefault("description", "");

        saveNotification(userId, TYPE_ALERT_NEW, title, description, PRIORITY_HIGH, alertData);
        pushToUser(userId, TYPE_ALERT_NEW, alertData);
        log.info("预警通知已推送: userId={}, title={}", userId, title);
    }

    /**
     * 推送掌握度更新（仅 WebSocket，不持久化）。
     *
     * @param userId      目标用户 ID
     * @param masteryData 掌握度数据
     */
    public void pushMasteryUpdate(UUID userId, Map<String, Object> masteryData) {
        pushToUser(userId, TYPE_MASTERY_UPDATE, masteryData);
    }

    /**
     * 向教师推送教学建议。
     *
     * @param teacherId  目标教师 ID
     * @param suggestion 教学建议数据
     */
    public void pushTeachingSuggestion(UUID teacherId, Map<String, Object> suggestion) {
        String title = (String) suggestion.getOrDefault("title", "教学建议");
        String content = (String) suggestion.getOrDefault("content", "");

        saveNotification(teacherId, TYPE_NOTIFICATION, title, content, PRIORITY_NORMAL, suggestion);
        pushToUser(teacherId, TYPE_NOTIFICATION, suggestion);
        log.info("教学建议已推送: teacherId={}, title={}", teacherId, title);
    }

    // ════════════════════════════════════════════
    //  通知查询
    // ════════════════════════════════════════════

    /**
     * 获取用户通知列表。
     *
     * @param userId     用户 ID
     * @param unreadOnly 是否只返回未读
     * @param limit      限制条数
     * @return 通知 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getUserNotifications(UUID userId, boolean unreadOnly, int limit) {
        List<Notification> notifications;
        if (unreadOnly) {
            notifications = notificationRepository
                    .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 100)));
        } else {
            notifications = notificationRepository
                    .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 100)));
        }
        return notifications.stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户未读通知数量。
     *
     * @param userId 用户 ID
     * @return 未读通知数
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // ════════════════════════════════════════════
    //  通知管理
    // ════════════════════════════════════════════

    /**
     * 创建并保存通知。
     *
     * @param request 创建通知请求
     * @return 创建的通知 DTO
     */
    @Transactional
    public NotificationDto createNotification(CreateNotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setNotifType(request.getNotifType());
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setPriority(request.getPriority() != null ? request.getPriority() : PRIORITY_NORMAL);

        if (request.getMetadata() != null) {
            notification.setMetadata(request.getMetadata());
        }

        notification = notificationRepository.save(notification);
        log.info("通知已创建: userId={}, type={}, title={}", request.getUserId(), request.getNotifType(), request.getTitle());
        return NotificationDto.fromEntity(notification);
    }

    /**
     * 标记通知为已读。
     *
     * @param notificationId 通知 ID
     * @return 更新后的通知 DTO
     * @throws ResourceNotFoundException 如果通知不存在
     */
    @Transactional
    public NotificationDto markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("通知", notificationId));
        notification.setIsRead(true);
        notification = notificationRepository.save(notification);
        return NotificationDto.fromEntity(notification);
    }

    /**
     * 标记用户的所有通知为已读。
     *
     * @param userId 用户 ID
     * @return 标记已读的数量
     */
    @Transactional
    public int markAllAsRead(UUID userId) {
        int count = notificationRepository.markAllAsReadByUserId(userId);
        if (count > 0) {
            log.info("全部通知已标记已读: userId={}, count={}", userId, count);
        }
        return count;
    }

    /**
     * 删除通知。
     *
     * @param notificationId 通知 ID
     */
    @Transactional
    public void deleteNotification(UUID notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new ResourceNotFoundException("通知", notificationId);
        }
        notificationRepository.deleteById(notificationId);
        log.debug("通知已删除: id={}", notificationId);
    }

    // ════════════════════════════════════════════
    //  内部方法
    // ════════════════════════════════════════════

    /**
     * 保存通知到数据库（供离线查看）。
     *
     * @param userId   用户 ID
     * @param type     通知类型
     * @param title    标题
     * @param content  内容
     * @param priority 优先级
     * @param metadata 附加元数据
     */
    private void saveNotification(UUID userId, String type, String title, String content,
                                   String priority, Map<String, Object> metadata) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setNotifType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setPriority(priority);
            if (metadata != null && !metadata.isEmpty()) {
                notification.setMetadata(objectMapper.writeValueAsString(metadata));
            }
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("保存通知失败: userId={}, type={}, error={}", userId, type, e.getMessage());
        }
    }
}
