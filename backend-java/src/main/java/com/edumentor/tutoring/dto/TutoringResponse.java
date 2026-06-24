package com.edumentor.tutoring.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 智能答疑响应 DTO。
 *
 * @author EduMentor Team
 */
@Data
public class TutoringResponse {

    /** AI 回答内容 */
    private String answer;

    /** 会话 ID */
    private String sessionId;

    /** 用户问题 */
    private String question;

    /** Token 用量 */
    private TokenUsage tokenUsage;

    /** 使用的模型 */
    private String model;

    /** RAG 来源列表 */
    private List<SourceInfo> sources;

    /** 辅导级别（L1~L5） */
    private String tutoringLevel;

    @Data
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    @Data
    public static class SourceInfo {
        private String title;
        private String content;
        private double score;
        private String sourceType;
    }
}
