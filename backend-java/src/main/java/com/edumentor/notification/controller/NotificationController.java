package com.edumentor.notification.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.notification.dto.CreateNotificationRequest;
import com.edumentor.notification.dto.NotificationDto;
import com.edumentor.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 通知管理 REST API。
 *
 * <p>提供通知的查询、标记已读、创建、删除等接口。</p>
 *
 * @author EduMentor Team
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 获取用户通知列表。
     *
     * @param userId     用户 ID
     * @param unreadOnly 是否只返回未读（默认 false）
     * @param limit      限制条数（默认 50）
     * @return 通知列表
     */
    @GetMapping
    public ApiResponse<List<NotificationDto>> getNotifications(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        List<NotificationDto> notifications = notificationService.getUserNotifications(userId, unreadOnly, limit);
        return ApiResponse.success(notifications);
    }

    /**
     * 获取用户未读通知数量。
     *
     * @param userId 用户 ID
     * @return 未读通知数
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(@RequestParam UUID userId) {
        long count = notificationService.getUnreadCount(userId);
        return ApiResponse.success(count);
    }

    /**
     * 标记通知为已读。
     *
     * @param id 通知 ID
     * @return 更新后的通知
     */
    @PutMapping("/{id}/read")
    public ApiResponse<NotificationDto> markAsRead(@PathVariable UUID id) {
        NotificationDto dto = notificationService.markAsRead(id);
        return ApiResponse.success(dto, "已标记为已读");
    }

    /**
     * 标记用户所有通知为已读。
     *
     * @param userId 用户 ID
     * @return 操作结果
     */
    @PutMapping("/read-all")
    public ApiResponse<String> markAllAsRead(@RequestParam UUID userId) {
        int count = notificationService.markAllAsRead(userId);
        return ApiResponse.success(null, "已标记 " + count + " 条通知为已读");
    }

    /**
     * 创建通知。
     *
     * @param request 创建通知请求
     * @return 创建的通知
     */
    @PostMapping
    public ApiResponse<NotificationDto> createNotification(@Valid @RequestBody CreateNotificationRequest request) {
        NotificationDto dto = notificationService.createNotification(request);
        return ApiResponse.success(dto, "通知已创建");
    }

    /**
     * 删除通知。
     *
     * @param id 通知 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(@PathVariable UUID id) {
        notificationService.deleteNotification(id);
        return ApiResponse.success(null, "通知已删除");
    }
}
