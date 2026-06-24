package com.edumentor.engine.llm;

import com.edumentor.engine.llm.LLMConfig.ProviderConfig;
import com.edumentor.engine.llm.LLMConfig.WenxinProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 文心一言 (ERNIE) 供应商适配器。
 * <p>需要先通过 API Key + Secret Key 获取 Access Token，有效期为 30 天。</p>
 *
 * @author EduMentor Team
 */
@Component
public class WenxinProvider implements LLMProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(WenxinProvider.class);
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final ConcurrentHashMap<String, AccessTokenCache> tokenCache = new ConcurrentHashMap<>();

    public WenxinProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public LLMProvider getProvider() { return LLMProvider.WENXIN; }

    @Override
    public LLMResponse generate(String systemPrompt, String userMessage, ProviderConfig config, double temperature, int maxTokens) {
        long start = System.currentTimeMillis();
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.userMessage(userMessage));
        String json = doChat(msgs, config, temperature, maxTokens, false);
        return parseResponse(json, config, System.currentTimeMillis() - start);
    }

    @Override
    public LLMResponse chat(String systemPrompt, List<ChatMessage> messages, ProviderConfig config, double temperature, int maxTokens) {
        long start = System.currentTimeMillis();
        List<ChatMessage> all = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank())
            all.add(ChatMessage.userMessage("【系统指令】" + systemPrompt));
        all.addAll(messages);
        String json = doChat(all, config, temperature, maxTokens, false);
        return parseResponse(json, config, System.currentTimeMillis() - start);
    }

    @Override
    public void generateStream(String systemPrompt, String userMessage, ProviderConfig config, double temperature, int maxTokens, Consumer<LLMResponse> chunkConsumer) {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.userMessage(userMessage));
        doChatStream(msgs, config, temperature, maxTokens, chunkConsumer);
    }

    @Override
    public void chatStream(String systemPrompt, List<ChatMessage> messages, ProviderConfig config, double temperature, int maxTokens, Consumer<LLMResponse> chunkConsumer) {
        List<ChatMessage> all = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank())
            all.add(ChatMessage.userMessage("【系统指令】" + systemPrompt));
        all.addAll(messages);
        doChatStream(all, config, temperature, maxTokens, chunkConsumer);
    }

    @Override
    public boolean healthCheck(ProviderConfig config) {
        try { return getAccessToken(config) != null; } catch (Exception e) { return false; }
    }

    private String getAccessToken(ProviderConfig config) {
        WenxinProviderConfig wc = (WenxinProviderConfig) config;
        String key = wc.getApiKey() + ":" + wc.getSecretKey();
        AccessTokenCache cached = tokenCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.token;
        return refreshToken(wc, key);
    }

    private synchronized String refreshToken(WenxinProviderConfig config, String key) {
        AccessTokenCache cached = tokenCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.token;
        try {
            String resp = webClientBuilder.baseUrl(TOKEN_URL).build().post()
                    .uri(ub -> ub.queryParam("grant_type", "client_credentials")
                            .queryParam("client_id", config.getApiKey())
                            .queryParam("client_secret", config.getSecretKey()).build())
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(30));
            JsonNode root = objectMapper.readTree(resp);
            if (!root.has("access_token"))
                throw new LlmException("Failed to get access token", LlmException.ErrorCategory.AUTHENTICATION, LLMProvider.WENXIN);
            String token = root.get("access_token").asText();
            int expiresIn = root.has("expires_in") ? root.get("expires_in").asInt() : 2592000;
            long ttl = (expiresIn - 432000) * 1000L; // refresh 5 days early
            tokenCache.put(key, new AccessTokenCache(token, ttl));
            log.info("Wenxin access token refreshed, expires in {}s", expiresIn);
            return token;
        } catch (LlmException e) { throw e; } catch (Exception e) {
            throw new LlmException("Failed to refresh access token", LlmException.ErrorCategory.NETWORK, LLMProvider.WENXIN, e);
        }
    }

    private String doChat(List<ChatMessage> messages, ProviderConfig config, double temperature, int maxTokens, boolean stream) {
        String token = getAccessToken(config);
        String url = buildUrl(config, token);
        String body = buildRequest(messages, config, temperature, maxTokens, stream);
        try {
            return webClientBuilder.build().post().uri(url).header("Content-Type", "application/json")
                    .bodyValue(body).retrieve()
                    .onStatus(s -> s.isError(), resp -> resp.bodyToMono(String.class).flatMap(err ->
                            reactor.core.publisher.Mono.error(new LlmException("Wenxin error: " + err,
                                    LlmException.ErrorCategory.API_ERROR, LLMProvider.WENXIN, resp.statusCode().value()))))
                    .bodyToMono(String.class).block(Duration.ofSeconds(60));
        } catch (LlmException e) { throw e; } catch (Exception e) {
            throw new LlmException("Wenxin call failed", LlmException.ErrorCategory.NETWORK, LLMProvider.WENXIN, e);
        }
    }

    private void doChatStream(List<ChatMessage> messages, ProviderConfig config, double temperature, int maxTokens, Consumer<LLMResponse> chunkConsumer) {
        try {
            String resp = doChat(messages, config, temperature, maxTokens, true);
            for (String line : resp.split("\n")) {
                if (line.isBlank()) continue;
                JsonNode root = objectMapper.readTree(line);
                if (root.has("error_code") && root.get("error_code").asInt() != 0) {
                    chunkConsumer.accept(LLMResponse.error(root.path("error_msg").asText("error"), LLMProvider.WENXIN, config.getModel()));
                    return;
                }
                String result = root.path("result").asText("");
                if (!result.isEmpty()) chunkConsumer.accept(LLMResponse.streamChunk(result, LLMProvider.WENXIN, config.getModel()));
                if (root.has("is_end") && root.get("is_end").asBoolean()) {
                    int pt = root.path("usage_prompt_tokens").asInt(0);
                    int ct = root.path("usage_completion_tokens").asInt(0);
                    int tt = root.path("usage_total_tokens").asInt(0);
                    chunkConsumer.accept(LLMResponse.streamEnd(LLMProvider.WENXIN, config.getModel(), new TokenUsage(pt, ct, tt, 0), "stop"));
                }
            }
        } catch (LlmException e) { chunkConsumer.accept(LLMResponse.error(e.getMessage(), LLMProvider.WENXIN, config.getModel()));
        } catch (Exception e) { chunkConsumer.accept(LLMResponse.error("Wenxin stream failed", LLMProvider.WENXIN, config.getModel())); }
    }

    private String buildUrl(ProviderConfig config, String token) {
        String base = config.getApiBase();
        if (base == null || base.isBlank()) base = LLMProvider.WENXIN.getDefaultApiBase();
        String model = config.getModel() != null && !config.getModel().isBlank() ? config.getModel() : LLMProvider.WENXIN.getDefaultModel();
        return base.replaceAll("/+$", "") + "/" + model + "?access_token=" + token;
    }

    private String buildRequest(List<ChatMessage> messages, ProviderConfig config, double temperature, int maxTokens, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("temperature", temperature).put("max_output_tokens", maxTokens).put("stream", stream);
            ArrayNode arr = root.putArray("messages");
            for (ChatMessage msg : messages) {
                String role = "system".equals(msg.getRole()) ? "user" : msg.getRole();
                arr.addObject().put("role", role).put("content", msg.getContent());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) { throw new LlmException("Failed to build request", LlmException.ErrorCategory.CONFIG_ERROR, LLMProvider.WENXIN, e); }
    }

    private LLMResponse parseResponse(String json, ProviderConfig config, long durationMs) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error_code") && root.get("error_code").asInt() != 0)
                throw new LlmException("API error: " + root.path("error_msg").asText(), LlmException.ErrorCategory.API_ERROR, LLMProvider.WENXIN);
            String content = root.path("result").asText("");
            int pt = root.path("usage_prompt_tokens").asInt(0);
            int ct = root.path("usage_completion_tokens").asInt(0);
            int tt = root.path("usage_total_tokens").asInt(0);
            return new LLMResponse.Builder().content(content).finished(true)
                    .tokenUsage(new TokenUsage(pt, ct, tt, 0)).provider(LLMProvider.WENXIN)
                    .model(config.getModel()).durationMs(durationMs).finishReason("stop").build();
        } catch (LlmException e) { throw e; } catch (Exception e) {
            throw new LlmException("Failed to parse response", LlmException.ErrorCategory.PARSE_ERROR, LLMProvider.WENXIN, e);
        }
    }

    private static class AccessTokenCache {
        final String token;
        final long expiresAt;
        AccessTokenCache(String token, long ttlMs) { this.token = token; this.expiresAt = System.currentTimeMillis() + ttlMs; }
        boolean isExpired() { return System.currentTimeMillis() >= expiresAt; }
    }
}
