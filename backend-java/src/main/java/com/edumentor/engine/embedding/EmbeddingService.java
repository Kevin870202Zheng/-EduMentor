package com.edumentor.engine.embedding;

import com.edumentor.config.LlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Embedding 向量生成服务 — 调用 LLM 供应商的 Embedding API 将文本转为向量。
 *
 * <p>
 * 支持两种供应商：
 * <ul>
 *   <li><b>OpenAI</b> — {@code POST /v1/embeddings}，模型 {@code text-embedding-3-small}（1536 维）</li>
 *   <li><b>Ollama</b> — {@code POST /api/embeddings}，模型 {@code nomic-embed-text}（768 维）</li>
 * </ul>
 * </p>
 *
 * @author EduMentor Team
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final LlmConfig llmConfig;
    private final WebClient webClient;

    public EmbeddingService(LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
        this.webClient = WebClient.builder().build();
    }

    /**
     * 将单段文本转为向量。
     *
     * @param text 输入文本
     * @return float[] 向量，失败或未配置时返回 0 长度数组
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) return new float[0];
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[0] : results.getFirst();
    }

    /**
     * 批量将文本转为向量。
     *
     * @param texts 输入文本列表
     * @return float[] 向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        String provider = llmConfig.getProvider();

        if ("OPENAI".equalsIgnoreCase(provider)) {
            return embedOpenAI(texts);
        } else if ("OLLAMA".equalsIgnoreCase(provider)) {
            return embedOllama(texts);
        } else if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            log.info("DeepSeek 不支持 Embedding，使用关键词降级检索");
            return texts.stream().map(t -> new float[0]).toList();
        } else {
            log.warn("当前供应商 {} 不支持 Embedding，返回空向量", provider);
            return texts.stream().map(t -> new float[0]).toList();
        }
    }

    /**
     * 判断 Embedding 功能是否可用。
     */
    public boolean isAvailable() {
        String provider = llmConfig.getProvider();
        return "OPENAI".equalsIgnoreCase(provider) || "OLLAMA".equalsIgnoreCase(provider);
    }

    /**
     * 调用 OpenAI Embedding API。
     */
    @SuppressWarnings("unchecked")
    private List<float[]> embedOpenAI(List<String> texts) {
        try {
            String apiKey = llmConfig.getOpenai().getApiKey();
            String apiBase = llmConfig.getOpenai().getApiBase();
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("OpenAI API Key 未配置，无法生成 Embedding");
                return texts.stream().map(t -> new float[0]).toList();
            }

            Map<String, Object> body = Map.of(
                    "model", "text-embedding-3-small",
                    "input", texts
            );

            Map<String, Object> response = webClient.post()
                    .uri(apiBase + "/v1/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null || !response.containsKey("data")) {
                log.warn("OpenAI Embedding 返回异常: {}", response);
                return texts.stream().map(t -> new float[0]).toList();
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            return data.stream()
                    .map(item -> {
                        List<Double> embedding = (List<Double>) item.get("embedding");
                        if (embedding == null) return new float[0];
                        float[] result = new float[embedding.size()];
                        for (int i = 0; i < embedding.size(); i++) {
                            result[i] = embedding.get(i).floatValue();
                        }
                        return result;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("OpenAI Embedding 调用失败: {}", e.getMessage());
            return texts.stream().map(t -> new float[0]).toList();
        }
    }

    /**
     * 调用 Ollama Embedding API。
     */
    @SuppressWarnings("unchecked")
    private List<float[]> embedOllama(List<String> texts) {
        String rawBaseUrl = llmConfig.getOllama().getBaseUrl();
        final String baseUrl = (rawBaseUrl != null && !rawBaseUrl.isBlank())
                ? rawBaseUrl : "http://localhost:11434";

        return texts.stream().map(text -> {
            try {
                Map<String, Object> body = Map.of(
                        "model", "nomic-embed-text",
                        "prompt", text
                );

                Map<String, Object> response = webClient.post()
                        .uri(baseUrl + "/api/embeddings")
                        .header("Content-Type", "application/json")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(30));

                if (response == null || !response.containsKey("embedding")) {
                    log.warn("Ollama Embedding 返回异常: {}", response);
                    return new float[0];
                }

                List<Double> embedding = (List<Double>) response.get("embedding");
                if (embedding == null) return new float[0];
                float[] result = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    result[i] = embedding.get(i).floatValue();
                }
                return result;
            } catch (Exception e) {
                log.error("Ollama Embedding 调用失败: {}", e.getMessage());
                return new float[0];
            }
        }).toList();
    }
}
