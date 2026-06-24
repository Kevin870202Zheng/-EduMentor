package com.edumentor.engine.llm;

/**
 * LLM 服务异常 — 封装 LLM 调用过程中发生的所有异常。
 * <p>
 * 包括网络错误、API 错误、速率限制、Token 超限、解析错误等。
 * 继承自 RuntimeException，便于在 Service 层传播。
 * </p>
 *
 * @author EduMentor Team
 */
public class LlmException extends RuntimeException {

    /** 错误类别 */
    private final ErrorCategory category;

    /** 涉及的供应商 */
    private final LLMProvider provider;

    /** HTTP 状态码（如果有） */
    private final int httpStatus;

    public LlmException(String message, ErrorCategory category, LLMProvider provider) {
        super(message);
        this.category = category;
        this.provider = provider;
        this.httpStatus = 0;
    }

    public LlmException(String message, ErrorCategory category, LLMProvider provider, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.provider = provider;
        this.httpStatus = 0;
    }

    public LlmException(String message, ErrorCategory category, LLMProvider provider, int httpStatus) {
        super(message);
        this.category = category;
        this.provider = provider;
        this.httpStatus = httpStatus;
    }

    public LlmException(String message, ErrorCategory category, LLMProvider provider, int httpStatus, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.provider = provider;
        this.httpStatus = httpStatus;
    }

    public ErrorCategory getCategory() { return category; }
    public LLMProvider getProvider() { return provider; }
    public int getHttpStatus() { return httpStatus; }

    /**
     * LLM 错误类别枚举。
     */
    public enum ErrorCategory {
        /** 网络连接错误（超时、DNS 解析失败等） */
        NETWORK,
        /** API 认证错误（API Key 无效等） */
        AUTHENTICATION,
        /** 速率限制触发 */
        RATE_LIMIT,
        /** Token 超过模型上下文窗口限制 */
        TOKEN_LIMIT,
        /** API 返回错误响应 */
        API_ERROR,
        /** 响应解析错误（JSON 解析失败等） */
        PARSE_ERROR,
        /** 供应商服务不可用 */
        SERVICE_UNAVAILABLE,
        /** 配置错误（缺少 API Key 等） */
        CONFIG_ERROR,
        /** 未知错误 */
        UNKNOWN
    }

    @Override
    public String toString() {
        return String.format("LlmException[%s](provider=%s, http=%d): %s",
                category, provider, httpStatus, getMessage());
    }
}
