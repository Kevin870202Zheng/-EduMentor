package com.edumentor.engine.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * DeepSeek 供应商适配器 — 使用与 OpenAI 兼容的 API 协议。
 *
 * <p>
 * DeepSeek 的 Chat Completions API 与 OpenAI 完全兼容（{@code POST /v1/chat/completions}），
 * 因此本适配器继承 {@link OpenAIProvider} 的所有逻辑，仅覆盖供应商标识和默认 API 端点。
 * API Endpoint 默认为 {@code https://api.deepseek.com}。
 * </p>
 *
 * @author EduMentor Team
 */
@Component
public class DeepSeekProvider extends OpenAIProvider {

    public DeepSeekProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        super(objectMapper, webClientBuilder);
    }

    @Override
    public LLMProvider getProvider() {
        return LLMProvider.DEEPSEEK;
    }

    @Override
    protected String getDefaultApiBase() {
        return LLMProvider.DEEPSEEK.getDefaultApiBase();
    }
}
