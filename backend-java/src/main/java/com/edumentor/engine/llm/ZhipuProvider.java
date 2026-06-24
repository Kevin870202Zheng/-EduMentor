package com.edumentor.engine.llm;

import com.edumentor.engine.llm.LLMConfig.ProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 智谱清言 (GLM) 供应商适配器。
 * <p>
 * 智谱 API 与 OpenAI 兼容，使用相同的 /v1/chat/completions 端点格式，
 * 但需要不同的认证方式（通过请求头 Authorization: Bearer APIKey）。
 * </p>
 *
 * <h3>API 文档</h3>
 * <ul>
 *   <li>Chat Completion: POST /v4/chat/completions</li>
 *   <li>Streaming: SSE</li>
 * </ul>
 *
 * @author EduMentor Team
 */
@Component
public class ZhipuProvider implements LLMProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZhipuProvider.class);

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public ZhipuProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public LLMProvider getProvider() {
        return LLMProvider.ZHIPU;
    }

    @Override
    public LLMResponse generate(String systemPrompt, String userMessage,
                                ProviderConfig config, double temperature, int maxTokens) {
        long startTime = System.currentTimeMillis();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.systemMessage(systemPrompt));
        messages.add(ChatMessage.userMessage(userMessage));

        String jsonResponse = doChatCompletion(messages, config, temperature, maxTokens, false);
        long durationMs = System.currentTimeMillis() - startTime;

        return parseResponse(jsonResponse, config, durationMs);
    }

    @Override
    public LLMResponse chat(String systemPrompt, List<ChatMessage> messages,
                            ProviderConfig config, double temperature, int maxTokens) {
        long startTime = System.currentTimeMillis();

        List<ChatMessage> fullMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            fullMessages.add(ChatMessage.systemMessage(systemPrompt));
        }
        fullMessages.addAll(messages);

        String jsonResponse = doChatCompletion(fullMessages, config, temperature, maxTokens, false);
        long durationMs = System.currentTimeMillis() - startTime;

        return parseResponse(jsonResponse, config, durationMs);
    }

    @Override
    public void generateStream(String systemPrompt, String userMessage,
                               ProviderConfig config, double temperature, int maxTokens,
                               Consumer<LLMResponse> chunkConsumer) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.systemMessage(systemPrompt));
        messages.add(ChatMessage.userMessage(userMessage));

        doChatStream(messages, config, temperature, maxTokens, chunkConsumer);
    }

    @Override
    public void chatStream(String systemPrompt, List<ChatMessage> messages,
                           ProviderConfig config, double temperature, int maxTokens,
                           Consumer<LLMResponse> chunkConsumer) {
        List<ChatMessage> fullMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            fullMessages.add(ChatMessage.systemMessage(systemPrompt));
        }
        fullMessages.addAll(messages);

        doChatStream(fullMessages, config, temperature, maxTokens, chunkConsumer);
    }

    @Override
    public boolean healthCheck(ProviderConfig config) {
        // 智谱没有简单的健康检查端点，通过发送一个简单的请求来验证
        try {
            LLMResponse response = generate("Say 'ok' if you can hear me.", "ping",
                    config, 0.1, 10);
            return response != null && !response.isError();
        } catch (Exception e) {
            log.warn("Zhipu health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 执行非流式 Chat Completion API 调用。
     */
    private String doChatCompletion(List<ChatMessage> messages, ProviderConfig config,
                                    double temperature, int maxTokens, boolean stream) {
        try {
            String requestBody = buildChatRequest(messages, config, temperature, maxTokens, stream);
            String apiBase = getApiBase(config);

            log.debug("Zhipu request: POST {}/chat/completions, model={}", apiBase, config.getModel());

            WebClient client = webClientBuilder.baseUrl(apiBase).build();
            return client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), response ->
                            response.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("Zhipu API error ({}): {}", response.statusCode(), errorBody);
                                return reactor.core.publisher.Mono.error(
                                        new LlmException("Zhipu API error: " + errorBody,
                                                LlmException.ErrorCategory.API_ERROR,
                                                LLMProvider.ZHIPU, response.statusCode().value()));
                            })
                    )
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.error("Zhipu call failed: {}", e.getMessage(), e);
            throw new LlmException("Zhipu call failed: " + e.getMessage(),
                    LlmException.ErrorCategory.NETWORK, LLMProvider.ZHIPU, e);
        }
    }

    /**
     * 执行流式 Chat Completion API 调用。
     */
    private void doChatStream(List<ChatMessage> messages, ProviderConfig config,
                              double temperature, int maxTokens,
                              Consumer<LLMResponse> chunkConsumer) {
        try {
            String requestBody = buildChatRequest(messages, config, temperature, maxTokens, true);
            String apiBase = getApiBase(config);

            log.debug("Zhipu stream request: POST {}/chat/completions, model={}", apiBase, config.getModel());

            WebClient client = webClientBuilder.baseUrl(apiBase).build();

            Flux<String> eventStream = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), response ->
                            response.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("Zhipu stream error ({}): {}", response.statusCode(), errorBody);
                                return reactor.core.publisher.Mono.error(
                                        new LlmException("Zhipu stream error: " + errorBody,
                                                LlmException.ErrorCategory.API_ERROR,
                                                LLMProvider.ZHIPU, response.statusCode().value()));
                            })
                    )
                    .bodyToFlux(String.class);

            for (String line : eventStream.toIterable()) {
                if (line.isBlank()) continue;

                // 智谱的 SSE 格式：data: {...}
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) continue;

                    try {
                        JsonNode jsonNode = objectMapper.readTree(data);
                        JsonNode choices = jsonNode.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String content = delta.get("content").asText();
                                if (!content.isEmpty()) {
                                    chunkConsumer.accept(
                                            LLMResponse.streamChunk(content, LLMProvider.ZHIPU, config.getModel()));
                                }
                            }

                            JsonNode finishReason = choices.get(0).get("finish_reason");
                            if (finishReason != null && !finishReason.isNull()
                                    && !"null".equals(finishReason.asText())
                                    && !finishReason.asText().isEmpty()) {
                                TokenUsage tokenUsage = null;
                                JsonNode usage = jsonNode.get("usage");
                                if (usage != null) {
                                    tokenUsage = parseTokenUsage(usage);
                                }
                                chunkConsumer.accept(LLMResponse.streamEnd(
                                        LLMProvider.ZHIPU, config.getModel(), tokenUsage,
                                        finishReason.asText()));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse Zhipu SSE chunk: {}", e.getMessage());
                    }
                }
            }

        } catch (LlmException e) {
            chunkConsumer.accept(LLMResponse.error(e.getMessage(), LLMProvider.ZHIPU, config.getModel()));
        } catch (Exception e) {
            log.error("Zhipu stream call failed: {}", e.getMessage(), e);
            chunkConsumer.accept(LLMResponse.error("Zhipu stream failed: " + e.getMessage(),
                    LLMProvider.ZHIPU, config.getModel()));
        }
    }

    /**
     * 构建 Chat Completion 请求体（智谱使用与 OpenAI 兼容的格式）。
     */
    private String buildChatRequest(List<ChatMessage> messages, ProviderConfig config,
                                    double temperature, int maxTokens, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", config.getModel() != null && !config.getModel().isBlank()
                    ? config.getModel() : LLMProvider.ZHIPU.getDefaultModel());
            root.put("temperature", temperature);
            root.put("max_tokens", maxTokens);
            root.put("stream", stream);

            ArrayNode messagesArray = root.putArray("messages");
            for (ChatMessage msg : messages) {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", msg.getRole());
                msgNode.put("content", msg.getContent());
            }

            if (config.getExtraParams() != null) {
                for (var entry : config.getExtraParams().entrySet()) {
                    root.put(entry.getKey(), entry.getValue());
                }
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException("Failed to build Zhipu request: " + e.getMessage(),
                    LlmException.ErrorCategory.CONFIG_ERROR, LLMProvider.ZHIPU, e);
        }
    }

    /**
     * 解析非流式响应 JSON。
     */
    private LLMResponse parseResponse(String jsonResponse, ProviderConfig config, long durationMs) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.size() == 0) {
                throw new LlmException("Zhipu returned empty choices",
                        LlmException.ErrorCategory.PARSE_ERROR, LLMProvider.ZHIPU);
            }

            String content = choices.get(0).get("message").get("content").asText();
            String finishReason = choices.get(0).has("finish_reason")
                    ? choices.get(0).get("finish_reason").asText() : "stop";

            TokenUsage tokenUsage = null;
            JsonNode usage = root.get("usage");
            if (usage != null) {
                tokenUsage = parseTokenUsage(usage);
            }

            return new LLMResponse.Builder()
                    .content(content)
                    .finished(true)
                    .tokenUsage(tokenUsage)
                    .provider(LLMProvider.ZHIPU)
                    .model(config.getModel())
                    .durationMs(durationMs)
                    .finishReason(finishReason)
                    .build();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse Zhipu response: " + e.getMessage(),
                    LlmException.ErrorCategory.PARSE_ERROR, LLMProvider.ZHIPU, e);
        }
    }

    private TokenUsage parseTokenUsage(JsonNode usage) {
        int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
        int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
        return new TokenUsage(promptTokens, completionTokens, "zhipu");
    }

    private String getApiBase(ProviderConfig config) {
        String base = config.getApiBase();
        if (base == null || base.isBlank()) {
            base = LLMProvider.ZHIPU.getDefaultApiBase();
        }
        // 智谱使用 v4 API
        if (!base.contains("/v4")) {
            base = base.replaceAll("/+$", "") + "/v4";
        }
        return base;
    }
}
