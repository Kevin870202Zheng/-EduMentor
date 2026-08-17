package com.edumentor.arbitration.entity;

import com.edumentor.arbitration.entity.enums.ArbitrationRole;
import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 仲裁消息（仲裁庭聊天记录）。
 * 每个会话独立存储，role 区分发言方；round_seq 标识仲裁轮次。
 */
@Getter
@Setter
@Entity
@Table(name = "arbitration_messages", indexes = {
    @Index(name = "idx_arm_session", columnList = "session_id, round_seq")
})
public class ArbitrationMessage extends BaseEntity {

    /** 所属仲裁会话（arbitration_sessions.id） */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** 发言角色 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArbitrationRole role;

    /** 消息内容 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 仲裁轮次（同轮内按 created_at 排序） */
    @Column(name = "round_seq", nullable = false)
    private Integer roundSeq = 0;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("sessionId", sessionId);
        dto.put("role", role != null ? role.name() : null);
        dto.put("content", content);
        dto.put("roundSeq", roundSeq);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
