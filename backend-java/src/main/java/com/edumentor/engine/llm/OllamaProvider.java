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
 * Ollama 本地模型供应商适配器。
 * <p>
 * Ollama 提供与 OpenAI 类似的 Chat API，但端点路径和请求格式略有不同。
 * 支持流式和非流式调用。
 * </p>
 *
 * <h3>API 文档</h3>
 * <ul>
 *   <li>Chat: POST /api/chat</li>
 *   <li>Generate: POST /api/generate</li>
 *   <li>Streaming: SSE (ndjson)</li>
 * </ul>
 *
 * @author EduMentor Team
 */
@Component
public class OllamaProvider implements LLMProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public OllamaProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public LLMProvider getProvider() {
        return LLMProvider.OLLAMA;
    }

    @Override
    public LLMResponse generate(String systemPrompt, String userMessage,
                                ProviderConfig config, double temperature, int maxTokens) {
        long startTime = System.currentTimeMillis();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.systemMessage(systemPrompt));
        messages.add(ChatMessage.userMessage(userMessage));
        String jsonResponse = doChat(messages, config, temperature, maxTokens, false);
        return parseChatResponse(jsonResponse, config, System.currentTimeMillis() - startTime);
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
        String jsonResponse = doChat(fullMessages, config, temperature, maxTokens, false);
        return parseChatResponse(jsonResponse, config, System.currentTimeMillis() - startTime);
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
        try {
            String apiBase = getApiBase(config);
            WebClient client = webClientBuilder.baseUrl(apiBase).build();
            String response = client.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            return response != null;
        } catch (Exception e) {
            log.warn("Ollama health check failed: {}", e.getMessage());
            return false;
        }
    }

    private String doChat(List<ChatMessage> messages, ProviderConfig config,
                          double temperature, int maxTokens, boolean stream) {
        try {
            String requestBody = buildChatRequest(messages, config, temperature, maxTokens, stream);
            String apiBase = getApiBase(config);
            log.debug("Ollama request: POST {}/api/chat, model={}", apiBase, config.getModel());

            WebClient client = webClientBuilder.baseUrl(apiBase).build();
            return client.post()
                    .uri("/api/chat")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), response ->
                            response.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("Ollama API error ({}): {}", response.statusCode(), errorBody);
                                return reactor.core.publisher.Mono.error(
                                        new LlmException("Ollama API error: " + errorBody,
                                                LlmException.ErrorCategory.API_ERROR,
                                                LLMProvider.OLLAMA, response.statusCode().value()));
                            })
                    )
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));
        } catch (LlmException e) { throw e;
        } catch (Exception e) {
            log.error("Ollama call failed: {}", e.getMessage(), e);
            throw new LlmException("Ollama call failed: " + e.getMessage(),
                    LlmException.ErrorCategory.NETWORK, LLMProvider.OLLAMA, e);
        }
    }

    private void doChatStream(List<ChatMessage> messages, ProviderConfig config,
                              double temperature, int maxTokens,
                              Consumer<LLMResponse> chunkConsumer) {
        try {
            String requestBody = buildChatRequest(messages, config, temperature, maxTokens, true);
            String apiBase = getApiBase(config);
            log.debug("Ollama stream request: POST {}/api/chat", apiBase);

            Flux<String> eventStream = webClientBuilder.baseUrl(apiBase).build().post()
                    .uri("/api/chat")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), response ->
                            response.bodyToMono(String.class).flatMap(errorBody ->
                                    reactor.core.publisher.Mono.error(new LlmException(
                                            "Ollama stream error: " + errorBody,
                                            LlmException.ErrorCategory.API_ERROR,
                                            LLMProvider.OLLAMA, response.statusCode().value()))))
                    .bodyToFlux(String.class);

            for (String line : eventStream.toIterable()) {
                if (line.isBlank()) continue;
                JsonNode jsonNode = objectMapper.readTree(line);
                JsonNode msgNode = jsonNode.get("message");
                if (msgNode != null && msgNode.has("content")) {
                    chunkConsumer.accept(LLMResponse.streamChunk(
                            msgNode.get("content").asText(), LLMProvider.OLLAMA, config.getModel()));
                }
                if (jsonNode.has("done") && jsonNode.get("done").asBoolean()) {
                    int pt = jsonNode.has("prompt_eval_count") ? jsonNode.get("prompt_eval_count").asInt() : 0;
                    int ct = jsonNode.has("eval_count") ? jsonNode.get("eval_count").asInt() : 0;
                    chunkConsumer.accept(LLMResponse.streamEnd(
                            LLMProvider.OLLAMA, config.getModel(), new TokenUsage(pt, ct, config.getModel()), "stop"));
                }
            }
        } catch (LlmException e) {
            chunkConsumer.accept(LLMResponse.error(e.getMessage(), LLMProvider.OLLAMA, config.getModel()));
        } catch (Exception e) {
            chunkConsumer.accept(LLMResponse.error("Ollama stream failed", LLMProvider.OLLAMA, config.getModel()));
        }
    }

    private String buildChatRequest(List<ChatMessage> messages, ProviderConfig config,
                                    double temperature, int maxTokens, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", config.getModel() != null && !config.getModel().isBlank()
                    ? config.getModel() : LLMProvider.OLLAMA.getDefaultModel());
            root.put("stream", stream);
            ObjectNode opts = root.putObject("options");
            opts.put("temperature", temperature);
            opts.put("num_predict", maxTokens);
            ArrayNode arr = root.putArray("messages");
            for (ChatMessage msg : messages) {
                arr.addObject().put("role", msg.getRole()).put("content", msg.getContent());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException("Failed to build Ollama request",
                    LlmException.ErrorCategory.CONFIG_ERROR, LLMProvider.OLLAMA, e);
        }
    }

    private LLMResponse parseChatResponse(String jsonResponse, ProviderConfig config, long durationMs) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            String content = root.has("message") && root.get("message").has("content")
                    ? root.get("message").get("content").asText() : "";
            int pt = root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt() : 0;
            int ct = root.has("eval_count") ? root.get("eval_count").asInt() : 0;
            return new LLMResponse.Builder()
                    .content(content).finished(true)
                    .tokenUsage(new TokenUsage(pt, ct, config.getModel()))
                    .provider(LLMProvider.OLLAMA).model(config.getModel())
                    .durationMs(durationMs).finishReason("stop").build();
        } catch (Exception e) {
            throw new LlmException("Failed to parse Ollama response",
                    LlmException.ErrorCategory.PARSE_ERROR, LLMProvider.OLLAMA, e);
        }
    }

    private String getApiBase(ProviderConfig config) {
        String base = config.getApiBase();
        if (base == null || base.isBlank()) base = LLMProvider.OLLAMA.getDefaultApiBase();
        if (base.endsWith("/api/chat")) base = base.replace("/api/chat", "");
        return base;
    }
}
