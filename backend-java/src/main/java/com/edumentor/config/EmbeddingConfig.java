package com.edumentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 配置 — 独立于 LLM 供应商的向量生成配置。
 *
 * <p>
 * 支持 OpenAI 兼容协议（包括火山引擎方舟 Ark）和 Ollama 两种 embedding 供应商。
 * 火山引擎方舟的 API 格式会自动检测（base-url 含 volces.com 时自动切换为多模态 API）。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingConfig {
    private String provider = "openai";
    private String model = "text-embedding-v3";
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    private String apiKey;
    private int dimension = 1024;
}
