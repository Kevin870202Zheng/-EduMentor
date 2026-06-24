package com.edumentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    private String provider = "MOCK";
    private String model = "gpt-4o-mini";
    private double temperature = 0.7;
    private int maxTokens = 2048;
    private int maxRetries = 3;
    private int timeout = 30;
    private OpenAiConfig openai = new OpenAiConfig();
    private OllamaConfig ollama = new OllamaConfig();
    private ZhipuConfig zhipu = new ZhipuConfig();
    private WenxinConfig wenxin = new WenxinConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private StreamingConfig streaming = new StreamingConfig();

    @Data
    public static class OpenAiConfig {
        private String apiKey;
        private String apiBase = "https://api.openai.com";
    }

    @Data
    public static class OllamaConfig {
        private String baseUrl = "http://localhost:11434";
    }

    @Data
    public static class ZhipuConfig {
        private String apiKey;
    }

    @Data
    public static class WenxinConfig {
        private String apiKey;
        private String secretKey;
    }

    @Data
    public static class RateLimitConfig {
        private int maxRequestsPerMinute = 60;
        private int maxTokensPerMinute = 100000;
    }

    @Data
    public static class StreamingConfig {
        private boolean enabled = true;
        private int bufferSize = 10;
    }
}
