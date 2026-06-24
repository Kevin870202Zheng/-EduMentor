package com.edumentor.qa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/**
 * 智能答疑提问请求 DTO。
 *
 * <p>包含用户的问题内容、可选的会话上下文和知识点关联。
 * {@code sessionId} 为空时表示新建会话，服务端会自动生成。</p>
 */
@Data
public class ChatRequest {

    /** 问题内容（必填） */
    @NotBlank(message = "问题内容不能为空")
    private String question;

    /** 会话 ID（为空时自动创建新会话） */
    private String sessionId;

    /** 关联知识点 ID（可选，用于 RAG 检索限定范围） */
    private UUID knowledgePointId;

    /** 消息类型（可选，如 QUESTION / HINT_REQUEST 等，默认 TEXT） */
    private String messageType;
}
