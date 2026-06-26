package com.edumentor.engine.embedding;

import com.edumentor.config.EmbeddingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Embedding 向量生成服务 — 调用供应商的 Embedding API 将文本转为向量。
 *
 * <p>
 * 支持三种模式（通过 {@code embedding.provider} 配置）：
 * <ul>
 *   <li><b>openai</b> — 标准 OpenAI 兼容 API（{@code POST /v1/embeddings}）</li>
 *   <li><b>openai + volces.com</b> — 火山引擎方舟多模态 API（自动检测 {@code volces.com}）</li>
 *   <li><b>ollama</b> — Ollama 本地 API（{@code POST /api/embeddings}）</li>
 * </ul>
 * </p>
 *
 * @author EduMentor Team
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingConfig config;
    private final WebClient webClient;

    public EmbeddingService(EmbeddingConfig config) {
        this.config = config;
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
     */
    public List<float[]> embedBatch(List<String> texts) {
        String provider = config.getProvider();

        if ("ollama".equalsIgnoreCase(provider)) {
            return embedOllama(texts);
        }
        // "openai" 模式 — 兼容标准 OpenAI 和火山引擎方舟
        return embedOpenAICompatible(texts);
    }

    /**
     * 判断 Embedding 功能是否可用。
     */
    public boolean isAvailable() {
        return config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    /**
     * 调用 OpenAI 兼容 Embedding API（支持火山引擎多模态格式）。
     * <p>
     * 当 {@code baseUrl} 包含 {@code volces.com} 时自动适配火山引擎方舟的多模态 API。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<float[]> embedOpenAICompatible(List<String> texts) {
        String apiKey = config.getApiKey();
        String baseUrl = config.getBaseUrl();
        String model = config.getModel();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Embedding API Key 未配置，无法生成向量");
            return texts.stream().map(t -> new float[0]).toList();
        }

        boolean isVolcengine = baseUrl != null && baseUrl.contains("volces.com");

        if (isVolcengine) {
            // 火山引擎多模态 API — 逐条处理
            return texts.stream().map(text -> embedVolcengineSingle(text, apiKey, baseUrl, model)).toList();
        }

        try {
            // 标准 OpenAI 兼容 API（批量）
            String endpoint = baseUrl.replaceAll("/+$", "") + "/embeddings";
            Object requestBody = Map.of(
                    "model", model,
                    "input", texts
            );

            log.debug("Embedding request: POST {}, model={}, texts={}", endpoint, model, texts.size());

            Map<String, Object> response = webClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null || !response.containsKey("data")) {
                log.warn("Embedding API 返回异常: {}", response);
                return texts.stream().map(t -> new float[0]).toList();
            }

            // OpenAI 标准返回格式: {"data": [{"embedding": [...], ...}]}
            Object dataObj = response.get("data");
            if (dataObj instanceof List) {
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
                return dataList.stream()
                        .map(item -> {
                            List<Double> embedding = (List<Double>) item.get("embedding");
                            if (embedding == null) return new float[0];
                            float[] result = new float[embedding.size()];
                            for (int i = 0; i < embedding.size(); i++) result[i] = embedding.get(i).floatValue();
                            return result;
                        })
                        .toList();
            }

            return texts.stream().map(t -> new float[0]).toList();
        } catch (Exception e) {
            log.error("Embedding API 调用失败: {}", e.getMessage());
            return texts.stream().map(t -> new float[0]).toList();
        }
    }

    /**
     * 调用火山引擎方舟多模态 Embedding API（逐条处理）。
     */
    @SuppressWarnings("unchecked")
    private float[] embedVolcengineSingle(String text, String apiKey, String baseUrl, String model) {
        try {
            String endpoint = baseUrl.replaceAll("/+$", "") + "/embeddings/multimodal";
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", List.of(Map.of("type", "text", "text", text))
            );

            Map<String, Object> response = webClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null) {
                log.warn("火山引擎 Embedding 返回空");
                return new float[0];
            }

            // 火山引擎返回格式: {"data": {"embedding": [...]}}
            Object dataObj = response.get("data");
            if (dataObj instanceof Map) {
                List<Double> embedding = (List<Double>) ((Map<String, Object>) dataObj).get("embedding");
                if (embedding == null) return new float[0];
                float[] result = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) result[i] = embedding.get(i).floatValue();
                return result;
            }

            return new float[0];
        } catch (Exception e) {
            log.error("火山引擎 Embedding 调用失败: {}", e.getMessage());
            return new float[0];
        }
    }

    /**
     * 调用 Ollama Embedding API。
     */
    @SuppressWarnings("unchecked")
    private List<float[]> embedOllama(List<String> texts) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }
        final String url = baseUrl;

        return texts.stream().map(text -> {
            try {
                Map<String, Object> body = Map.of(
                        "model", config.getModel() != null ? config.getModel() : "nomic-embed-text",
                        "prompt", text
                );

                Map<String, Object> response = webClient.post()
                        .uri(url + "/api/embeddings")
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
                for (int i = 0; i < embedding.size(); i++) result[i] = embedding.get(i).floatValue();
                return result;
            } catch (Exception e) {
                log.error("Ollama Embedding 调用失败: {}", e.getMessage());
                return new float[0];
            }
        }).toList();
    }
}
