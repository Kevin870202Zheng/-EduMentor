package com.edumentor.qa.entity;

import com.edumentor.entity.BaseEntity;
import com.edumentor.entity.enums.ChatRole;
import com.edumentor.entity.enums.MessageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chat_history", indexes = {
    @Index(name = "idx_ch_user_session", columnList = "user_id, session_id"),
    @Index(name = "idx_ch_session_created", columnList = "session_id, created_at")
})
public class ChatHistory extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "knowledge_point_id")
    private UUID knowledgePointId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 32)
    private MessageType messageType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("userId", userId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("sessionId", sessionId);
        dto.put("role", role != null ? role.name() : null);
        dto.put("messageType", messageType != null ? messageType.name() : null);
        dto.put("content", content);
        dto.put("tokenCount", tokenCount);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
