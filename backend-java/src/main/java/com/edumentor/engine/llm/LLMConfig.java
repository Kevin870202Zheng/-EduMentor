package com.edumentor.engine.llm;

import com.edumentor.config.LlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 配置 — 为 LLM 引擎层提供统一的配置访问接口。
 * <p>
 * 包装 {@link com.edumentor.config.LlmConfig}，提供 {@link ProviderConfig} 接口
 * 供各供应商适配器使用。同时提供速率限制和模型参数的便捷访问。
 * </p>
 *
 * @author EduMentor Team
 */
@Component
public class LLMConfig {

    private static final Logger log = LoggerFactory.getLogger(LLMConfig.class);

    private final LlmConfig rawConfig;

    public LLMConfig(LlmConfig rawConfig) {
        this.rawConfig = rawConfig;
    }

    /**
     * 获取当前配置的供应商。
     *
     * @return LLMProvider 枚举值
     */
    public LLMProvider getCurrentProvider() {
        return LLMProvider.fromName(rawConfig.getProvider());
    }

    /**
     * 获取当前供应商的配置。
     *
     * @return ProviderConfig
     */
    public ProviderConfig getCurrentProviderConfig() {
        return getProviderConfig(getCurrentProvider());
    }

    /**
     * 获取指定供应商的配置。
     *
     * @param provider 供应商
     * @return ProviderConfig
     */
    public ProviderConfig getProviderConfig(LLMProvider provider) {
        return switch (provider) {
            case OPENAI -> new OpenAiProviderConfig(rawConfig.getOpenai());
            case OLLAMA -> new OllamaProviderConfig(rawConfig.getOllama());
            case ZHIPU -> new ZhipuProviderConfig(rawConfig.getZhipu());
            case WENXIN -> new WenxinProviderConfig(rawConfig.getWenxin());
            case MOCK -> new MockProviderConfig();
        };
    }

    public double getTemperature() {
        return rawConfig.getTemperature();
    }

    public int getMaxTokens() {
        return rawConfig.getMaxTokens();
    }

    public int getMaxRetries() {
        return rawConfig.getMaxRetries();
    }

    public int getTimeout() {
        return rawConfig.getTimeout();
    }

    public int getRateLimitRequestsPerMinute() {
        return rawConfig.getRateLimit().getMaxRequestsPerMinute();
    }

    public int getRateLimitTokensPerMinute() {
        return rawConfig.getRateLimit().getMaxTokensPerMinute();
    }

    public void setProvider(String provider) {
        rawConfig.setProvider(provider);
    }

    // ──── Provider-specific config getters (used by LLMService ProviderConfig resolution) ────

    public ProviderConfig getOpenai() {
        return new OpenAiProviderConfig(rawConfig.getOpenai());
    }

    public ProviderConfig getOllama() {
        return new OllamaProviderConfig(rawConfig.getOllama());
    }

    public ProviderConfig getZhipu() {
        return new ZhipuProviderConfig(rawConfig.getZhipu());
    }

    public ProviderConfig getWenxin() {
        return new WenxinProviderConfig(rawConfig.getWenxin());
    }

    // ════════════════════════════════════════════
    // ProviderConfig 接口 — 供应商配置统一抽象
    // ════════════════════════════════════════════

    /**
     * 供应商配置接口 — 所有供应商配置的统一抽象。
     */
    public interface ProviderConfig {
        String getApiBase();
        String getApiKey();
        String getModel();
        Map<String, String> getExtraParams();
    }

    // ──── OpenAi ────

    private static class OpenAiProviderConfig implements ProviderConfig {
        private final LlmConfig.OpenAiConfig cfg;

        OpenAiProviderConfig(LlmConfig.OpenAiConfig cfg) { this.cfg = cfg; }

        @Override
        public String getApiBase() { return cfg.getApiBase(); }

        @Override
        public String getApiKey() { return cfg.getApiKey(); }

        @Override
        public String getModel() { return null; } // model 在 LLMService 中单独传递

        @Override
        public Map<String, String> getExtraParams() {
            return Map.of();
        }
    }

    // ──── Ollama ────

    private static class OllamaProviderConfig implements ProviderConfig {
        private final LlmConfig.OllamaConfig cfg;

        OllamaProviderConfig(LlmConfig.OllamaConfig cfg) { this.cfg = cfg; }

        @Override
        public String getApiBase() { return cfg.getBaseUrl(); }

        @Override
        public String getApiKey() { return ""; } // Ollama 不需要 API Key

        @Override
        public String getModel() { return null; }

        @Override
        public Map<String, String> getExtraParams() { return Map.of(); }
    }

    // ──── 智谱 ────

    private static class ZhipuProviderConfig implements ProviderConfig {
        private final LlmConfig.ZhipuConfig cfg;

        ZhipuProviderConfig(LlmConfig.ZhipuConfig cfg) { this.cfg = cfg; }

        @Override
        public String getApiBase() { return ""; } // 使用默认

        @Override
        public String getApiKey() { return cfg.getApiKey(); }

        @Override
        public String getModel() { return null; }

        @Override
        public Map<String, String> getExtraParams() { return Map.of(); }
    }

    // ──── 文心 ────

    static class WenxinProviderConfig implements ProviderConfig {
        private final LlmConfig.WenxinConfig cfg;

        WenxinProviderConfig(LlmConfig.WenxinConfig cfg) { this.cfg = cfg; }

        @Override
        public String getApiBase() { return ""; } // 使用默认

        @Override
        public String getApiKey() { return cfg.getApiKey(); }

        @Override
        public String getModel() { return null; }

        public String getSecretKey() { return cfg.getSecretKey(); }

        @Override
        public Map<String, String> getExtraParams() { return Map.of(); }
    }

    // ──── Mock ────

    private static class MockProviderConfig implements ProviderConfig {
        @Override
        public String getApiBase() { return ""; }
        @Override
        public String getApiKey() { return ""; }
        @Override
        public String getModel() { return "mock-model"; }
        @Override
        public Map<String, String> getExtraParams() { return Map.of(); }
    }
}
