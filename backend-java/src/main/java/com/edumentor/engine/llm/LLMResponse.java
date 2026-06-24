package com.edumentor.engine.llm;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用响应 — 统一封装所有供应商的响应数据。
 * <p>
 * 无论底层使用哪个供应商，LLMService 都返回此统一模型，
 * 包含生成的文本、Token 用量、元数据等信息。
 * </p>
 *
 * @author EduMentor Team
 */
public class LLMResponse {

    /** 生成的文本内容 */
    private final String content;

    /** 流式响应是否完成（仅流式模式使用） */
    private final boolean finished;

    /** Token 用量统计 */
    private final TokenUsage tokenUsage;

    /** 使用的供应商 */
    private final LLMProvider provider;

    /** 使用的模型名称 */
    private final String model;

    /** 请求耗时，单位毫秒 */
    private final long durationMs;

    /** 完成原因（"stop", "length", "content_filter" 等） */
    private final String finishReason;

    /** 额外元数据（供应商特有的返回信息） */
    private final Map<String, Object> metadata;

    /** 响应时间戳 */
    private final OffsetDateTime timestamp;

    private LLMResponse(Builder builder) {
        this.content = builder.content;
        this.finished = builder.finished;
        this.tokenUsage = builder.tokenUsage;
        this.provider = builder.provider;
        this.model = builder.model;
        this.durationMs = builder.durationMs;
        this.finishReason = builder.finishReason;
        this.metadata = builder.metadata != null
                ? Collections.unmodifiableMap(builder.metadata)
                : Collections.emptyMap();
        this.timestamp = builder.timestamp != null ? builder.timestamp : OffsetDateTime.now();
    }

    // ──── Getters ────

    public String getContent() { return content; }
    public boolean isFinished() { return finished; }
    public TokenUsage getTokenUsage() { return tokenUsage; }
    public LLMProvider getProvider() { return provider; }
    public String getModel() { return model; }
    public long getDurationMs() { return durationMs; }
    public String getFinishReason() { return finishReason; }
    public Map<String, Object> getMetadata() { return metadata; }
    public OffsetDateTime getTimestamp() { return timestamp; }

    /**
     * 创建一个流式响应的中间块。
     *
     * @param content  当前块的内容
     * @param provider 供应商
     * @param model    模型
     * @return 流式块响应（finished = false）
     */
    public static LLMResponse streamChunk(String content, LLMProvider provider, String model) {
        return new Builder()
                .content(content)
                .finished(false)
                .provider(provider)
                .model(model)
                .build();
    }

    /**
     * 创建一个流式响应的结束块。
     *
     * @param provider   供应商
     * @param model      模型
     * @param tokenUsage Token 用量（可选）
     * @param finishReason 完成原因
     * @return 结束块响应（finished = true）
     */
    public static LLMResponse streamEnd(LLMProvider provider, String model,
                                        TokenUsage tokenUsage, String finishReason) {
        return new Builder()
                .content("")
                .finished(true)
                .tokenUsage(tokenUsage)
                .provider(provider)
                .model(model)
                .finishReason(finishReason)
                .build();
    }

    /**
     * 快速创建成功的非流式响应。
     *
     * @param content    生成的文本
     * @param tokenUsage Token 用量
     * @param provider   供应商
     * @param model      模型
     * @param durationMs 耗时（毫秒）
     * @return LLMResponse
     */
    public static LLMResponse success(String content, TokenUsage tokenUsage,
                                      LLMProvider provider, String model, long durationMs) {
        return new Builder()
                .content(content)
                .finished(true)
                .tokenUsage(tokenUsage)
                .provider(provider)
                .model(model)
                .durationMs(durationMs)
                .finishReason("stop")
                .build();
    }

    /**
     * 快速创建错误响应。
     *
     * @param errorMessage 错误信息
     * @param provider 供应商
     * @param model    模型
     * @return LLMResponse
     */
    public static LLMResponse error(String errorMessage, LLMProvider provider, String model) {
        return new Builder()
                .content("[错误: " + errorMessage + "]")
                .finished(true)
                .provider(provider)
                .model(model)
                .finishReason("error")
                .build();
    }

    /**
     * 判断响应是否包含错误。
     *
     * @return true 如果是错误响应
     */
    public boolean isError() {
        return "error".equals(finishReason);
    }

    // ──── Builder ────

    public static class Builder {
        private String content = "";
        private boolean finished = true;
        private TokenUsage tokenUsage;
        private LLMProvider provider;
        private String model;
        private long durationMs;
        private String finishReason = "stop";
        private Map<String, Object> metadata;
        private OffsetDateTime timestamp;

        public Builder content(String content) { this.content = content; return this; }
        public Builder finished(boolean finished) { this.finished = finished; return this; }
        public Builder tokenUsage(TokenUsage tokenUsage) { this.tokenUsage = tokenUsage; return this; }
        public Builder provider(LLMProvider provider) { this.provider = provider; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder finishReason(String finishReason) { this.finishReason = finishReason; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder timestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; return this; }

        public LLMResponse build() {
            return new LLMResponse(this);
        }
    }

    @Override
    public String toString() {
        return "LLMResponse{" +
                "content='" + (content.length() > 100 ? content.substring(0, 100) + "..." : content) + '\'' +
                ", finished=" + finished +
                ", tokenUsage=" + tokenUsage +
                ", provider=" + provider +
                ", model='" + model + '\'' +
                ", durationMs=" + durationMs +
                ", finishReason='" + finishReason + '\'' +
                '}';
    }
}
