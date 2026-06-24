package com.edumentor.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 创建通知请求 DTO。
 *
 * @author EduMentor Team
 */
@Data
public class CreateNotificationRequest {

    @NotNull(message = "用户 ID 不能为空")
    private UUID userId;

    @NotBlank(message = "通知类型不能为空")
    private String notifType;

    @NotBlank(message = "通知标题不能为空")
    private String title;

    private String content;

    /** 优先级，可选值：low, normal, high, urgent */
    private String priority = "normal";

    /** 附加元数据（JSON 字符串） */
    private String metadata;
}
