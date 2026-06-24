package com.edumentor.tutoring.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话列表 DTO。
 *
 * @author EduMentor Team
 */
@Data
public class SessionDto {

    /** 会话 ID */
    private String sessionId;

    /** 会话标题（第一条用户消息的前50字符） */
    private String title;

    /** 消息数量 */
    private long messageCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /** 额外信息 */
    private Map<String, Object> metadata;
}
