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
 * OpenAI 兼容 API 供应商适配器。
 * <p>
 * 支持 OpenAI、Azure OpenAI 以及所有兼容 OpenAI API 格式的供应商
 * （如 OneAPI、LiteLLM 等代理）。
 * 使用 Spring WebClient (WebFlux) 实现非阻塞 HTTP 调用。
 * </p>
 *
 * <h3>API 文档</h3>
 * <ul>
 *   <li>Chat Completion: POST /v1/chat/completions</li>
 *   <li>Streaming: SSE (text/event-stream)</li>
 * </ul>
 *
 * @author EduMentor Team
 */
@Component
public class OpenAIProvider implements LLMProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public OpenAIProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * 获取当前供应商的默认 API Base URL。子类可覆盖以返回不同值。
     */
    protected String getDefaultApiBase() {
        return LLMProvider.OPENAI.getDefaultApiBase();
    }

    @Override
    public LLMProvider getProvider() {
        return LLMProvider.OPENAI;
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

        doChatCompletionStream(messages, config, temperature, maxTokens, chunkConsumer);
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

        doChatCompletionStream(fullMessages, config, temperature, maxTokens, chunkConsumer);
    }

    @Override
    public LLMResponse generateStructured(String systemPrompt, String userMessage,
                                          ProviderConfig config, String outputSchema,
                                          double temperature, int maxTokens) {
        // OpenAI 支持 response_format = json_object（需要系统提示中说明）
        String enhancedPrompt = systemPrompt + "\n\n你必须严格按照以下 JSON Schema 输出格式，只输出合法的 JSON 对象：\n" + outputSchema;
        return generate(enhancedPrompt, userMessage, config, Math.min(temperature, 0.3), maxTokens);
    }

    @Override
    public boolean healthCheck(ProviderConfig config) {
        try {
            String apiBase = getApiBase(config);
            WebClient client = webClientBuilder.baseUrl(apiBase).build();
            String response = client.get()
                    .uri("/models")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            return response != null && response.contains("data");
        } catch (Exception e) {
            log.warn("OpenAI health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 执行非流式 Chat Completion API 调用。
     */
    protected String doChatCompletion(List<ChatMessage> messages, ProviderConfig config,
                                    double temperature, int maxTokens, boolean stream) {
        try {
            String requestBody = buildChatRequest(messages, config, temperature, maxTokens, false);
            String apiBase = getApiBase(config);

            log.debug("OpenAI request: POST {}/chat/completions, model={}, messages={}",
                    apiBase, config.getModel(), messages.size());

            WebClient client = webClientBuilder.baseUrl(apiBase).build();
            return client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), response ->
                            response.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("OpenAI API error ({}): {}", response.statusCode(), errorBody);
                                LlmException.ErrorCategory category = mapHttpError(response.statusCode().value());
                                return reactor.core.publisher.Mono.error(
                                        new LlmException("OpenAI API error: " + errorBody,
                                                category, LLMProvider.OPENAI, response.statusCode().value()));
                            })
                    )
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(config.getExtraParams().containsKey("timeout")
                            ? Long.parseLong(config.getExtraParams().get("timeout"))
                            : 600));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI call failed: {}", e.getMessage(), e);
            throw new LlmException("OpenAI call failed: " + e.getMessage(),
                    LlmException.ErrorCategory.NETWORK, LLMProvider.OPENAI, e);
        }
    }

    /**
     * 执行流式 Chat Completion API 调用。
     */
    protected void doChatCompletionStream(List<ChatMessage> messages, ProviderConfig config,
                                        double temperature, int maxTokens,
                                        Consumer<LLMResponse> chunkConsumer) {
        try {
            String requestBody = buildChatRequest(messages, config, temperature, maxTokens, true);
            String apiBase = getApiBase(config);

            log.debug("OpenAI stream request: POST {}/chat/completions, model={}",
                    apiBase, config.getModel());

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
                                log.error("OpenAI stream error ({}): {}", response.statusCode(), errorBody);
                                return reactor.core.publisher.Mono.error(
                                        new LlmException("OpenAI stream error: " + errorBody,
                                                LlmException.ErrorCategory.API_ERROR,
                                                LLMProvider.OPENAI, response.statusCode().value()));
                            })
                    )
                    .bodyToFlux(String.class);

            StringBuffer contentBuffer = new StringBuffer();
            TokenUsage finalTokenUsage = null;

            // bodyToFlux(String.class) 按 TCP 数据帧分割，可能一次返回多行。
            // 需要在循环内按 \n 拆分成行后逐行处理。
            for (String chunk : eventStream.toIterable()) {
                String[] lines = chunk.split("\n", -1);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    if (trimmed.startsWith("data: ")) {
                        String data = trimmed.substring(6).trim();

                        // SSE 结束标记
                        if ("[DONE]".equals(data)) {
                            break;
                        }

                        try {
                            JsonNode jsonNode = objectMapper.readTree(data);
                            JsonNode choices = jsonNode.get("choices");
                            if (choices != null && choices.size() > 0) {
                                JsonNode delta = choices.get(0).get("delta");
                                if (delta != null) {
                                    String content = delta.has("content") ? delta.get("content").asText() : "";
                                    // DeepSeek 推理模型在 content 为空或很短时使用 reasoning_content
                                    if ((content.isBlank() || content.length() < 10) && delta.has("reasoning_content")) {
                                        content = delta.get("reasoning_content").asText();
                                    }
                                    if (!content.isEmpty()) {
                                        contentBuffer.append(content);
                                        chunkConsumer.accept(
                                                LLMResponse.streamChunk(content, LLMProvider.OPENAI, config.getModel()));
                                    }
                                }

                                // 检查 finish_reason
                                JsonNode finishReason = choices.get(0).get("finish_reason");
                                if (finishReason != null && !finishReason.isNull()
                                        && !"null".equals(finishReason.asText())) {
                                    // 尝试解析 Token 用量（OpenAI 最后一个 chunk 通常包含 usage）
                                    JsonNode usage = jsonNode.get("usage");
                                    if (usage != null) {
                                        finalTokenUsage = parseTokenUsage(usage);
                                    }
                                    chunkConsumer.accept(LLMResponse.streamEnd(
                                            LLMProvider.OPENAI, config.getModel(),
                                            finalTokenUsage, finishReason.asText()));
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse SSE chunk: {}", e.getMessage());
                        }
                    }
                }
            }

            // 如果流结束但没有收到 finish_reason，发送结束信号
            // Note: streamEnd already sent inside loop when finish_reason present

        } catch (LlmException e) {
            chunkConsumer.accept(LLMResponse.error(e.getMessage(), LLMProvider.OPENAI, config.getModel()));
        } catch (Exception e) {
            log.error("OpenAI stream call failed: {}", e.getMessage(), e);
            chunkConsumer.accept(LLMResponse.error("OpenAI stream failed: " + e.getMessage(),
                    LLMProvider.OPENAI, config.getModel()));
        }
    }

    /**
     * 构建 Chat Completion 请求体 JSON。
     */
    private String buildChatRequest(List<ChatMessage> messages, ProviderConfig config,
                                    double temperature, int maxTokens, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", config.getModel() != null && !config.getModel().isBlank()
                    ? config.getModel() : LLMProvider.OPENAI.getDefaultModel());
            root.put("temperature", temperature);
            root.put("max_tokens", maxTokens);
            root.put("stream", stream);

            // 构建 messages 数组
            ArrayNode messagesArray = root.putArray("messages");
            for (ChatMessage msg : messages) {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", msg.getRole());
                msgNode.put("content", msg.getContent());
            }

            // 添加额外参数
            if (config.getExtraParams() != null) {
                for (var entry : config.getExtraParams().entrySet()) {
                    if (!"timeout".equals(entry.getKey())) {
                        root.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException("Failed to build OpenAI request: " + e.getMessage(),
                    LlmException.ErrorCategory.CONFIG_ERROR, LLMProvider.OPENAI, e);
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
                throw new LlmException("OpenAI returned empty choices",
                        LlmException.ErrorCategory.PARSE_ERROR, LLMProvider.OPENAI);
            }

            String content = choices.get(0).get("message").get("content").asText();
            JsonNode msgNode = choices.get(0).get("message");
            log.debug("OpenAI response message: content={}, has_reasoning={}", 
                content.length() > 50 ? content.substring(0, 50) + "..." : content,
                msgNode.has("reasoning_content"));
            // DeepSeek 等推理模型在复杂任务时会使用 reasoning_content 而非 content
            if ((content.isBlank() || content.length() < 10) && msgNode.has("reasoning_content")) {
                String rc = msgNode.get("reasoning_content").asText();
                // 从 reasoning_content 中提取最后的 JSON 块（跳过思考过程）
                int lastBrace = rc.lastIndexOf('}');
                int jsonStart = lastBrace >= 0 ? rc.lastIndexOf('{', lastBrace) : -1;
                content = jsonStart >= 0 ? rc.substring(jsonStart, lastBrace + 1) : rc;
                log.debug("Fallback to reasoning_content: JSON extracted={}, total_len={}", 
                    jsonStart >= 0, rc.length());
            }
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
                    .provider(LLMProvider.OPENAI)
                    .model(config.getModel())
                    .durationMs(durationMs)
                    .finishReason(finishReason)
                    .build();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse OpenAI response: " + e.getMessage(),
                    LlmException.ErrorCategory.PARSE_ERROR, LLMProvider.OPENAI, e);
        }
    }

    /**
     * 解析 Token 用量 JSON 节点。
     */
    private TokenUsage parseTokenUsage(JsonNode usage) {
        int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
        int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
        int totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : (promptTokens + completionTokens);

        return new TokenUsage(promptTokens, completionTokens, totalTokens, 0.0);
    }

    /**
     * 获取 API 基地址，优先使用配置值，否则使用默认值。
     */
    protected String getApiBase(ProviderConfig config) {
        String base = config.getApiBase();
        if (base == null || base.isBlank()) {
            base = getDefaultApiBase();
        }
        // 去掉末尾的 /chat/completions（如果配置中包含了完整路径）
        if (base.endsWith("/chat/completions")) {
            base = base.replace("/chat/completions", "");
        }
        return base;
    }

    /**
     * 映射 HTTP 状态码到错误类别。
     */
    private LlmException.ErrorCategory mapHttpError(int statusCode) {
        return switch (statusCode) {
            case 401 -> LlmException.ErrorCategory.AUTHENTICATION;
            case 429 -> LlmException.ErrorCategory.RATE_LIMIT;
            case 500, 502, 503 -> LlmException.ErrorCategory.SERVICE_UNAVAILABLE;
            default -> LlmException.ErrorCategory.API_ERROR;
        };
    }
}
