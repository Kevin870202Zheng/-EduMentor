package com.edumentor.qa.dto;

import com.edumentor.qa.entity.ChatHistory;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 对话历史 DTO — 用于前端展示对话记录。
 *
 * <p>按时间正序排列形成完整对话流。通过 {@link #fromEntity(ChatHistory)}
 * 工厂方法从实体转换，避免直接暴露持久化模型。</p>
 */
@Data
public class ChatHistoryDto {

    private UUID id;
    private UUID userId;
    private String sessionId;
    private String role;
    private String messageType;
    private String content;
    private Integer tokenCount;
    private LocalDateTime createdAt;

    /**
     * 将 {@link ChatHistory} 实体转换为 DTO。
     *
     * @param entity 对话历史实体
     * @return 转换后的 DTO 实例
     */
    public static ChatHistoryDto fromEntity(ChatHistory entity) {
        ChatHistoryDto dto = new ChatHistoryDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setSessionId(entity.getSessionId());
        dto.setRole(entity.getRole() != null ? entity.getRole().name() : null);
        dto.setMessageType(entity.getMessageType() != null ? entity.getMessageType().name() : null);
        dto.setContent(entity.getContent());
        dto.setTokenCount(entity.getTokenCount());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
