package com.edumentor.qa.dto;

import lombok.Data;

import java.util.List;

/**
 * 智能答疑回答响应 DTO。
 *
 * <p>包含 AI 生成的回答内容、会话信息、Token 用量统计以及
 * RAG 检索来源列表。当使用流式响应时，{@code answer} 字段
 * 在结束时包含完整回答。</p>
 */
@Data
public class ChatResponse {

    /** AI 生成的回答内容 */
    private String answer;

    /** 会话 ID（用于后续多轮对话） */
    private String sessionId;

    /** Token 用量统计 */
    private TokenUsage tokenUsage;

    /** RAG 检索来源列表 */
    private List<SourceInfo> sources;

    /**
     * Token 用量统计。
     */
    @Data
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    /**
     * RAG 检索来源信息。
     */
    @Data
    public static class SourceInfo {
        private String title;
        private String content;
        private double score;
        private String sourceType;
    }
}
