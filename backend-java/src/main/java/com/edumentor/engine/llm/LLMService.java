package com.edumentor.engine.llm;

import com.edumentor.engine.llm.LLMConfig.ProviderConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LLM 核心服务 — 多供应商 LLM 调用的统一入口。
 * <p>
 * 负责：
 * <ul>
 *   <li>供应商选择与自动切换（根据配置选择当前供应商）</li>
 *   <li>速率限制（请求频率 + Token 速率双重限流）</li>
 *   <li>Token 用量追踪（按供应商、按日统计）</li>
 *   <li>自动重试（可配置重试次数、指数退避）</li>
 *   <li>流式响应支持（SSE 兼容）</li>
 *   <li>RAG 增强问答（结合检索结果的上下文增强）</li>
 *   <li>结构化输出（JSON Schema 约束输出）</li>
 *   <li>供应商健康检查与降级</li>
 * </ul>
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 非流式问答
 * LLMResponse response = llmService.ask("请分析这道题的解题思路");
 *
 * // 流式问答
 * llmService.askStream("请解释这个概念", chunk -> {
 *     sseEmitter.send(chunk.getContent());
 * });
 *
 * // RAG 增强问答
 * List<String> contexts = ragEngine.retrieve(question);
 * LLMResponse response = llmService.askWithContext(question, contexts);
 *
 * // 结构化输出
 * DiagnosisResult result = llmService.askStructured(
 *     "根据以下数据进行分析...",
 *     DiagnosisResult.class,
 *     "analysis_schema"
 * );
 * }</pre>
 *
 * @author EduMentor Team
 */
@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);

    private final LLMConfig llmConfig;
    private final TokenTracker tokenTracker;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    /** 所有已注册的供应商适配器（由 Spring 自动注入） */
    private final Map<LLMProvider, LLMProviderAdapter> providerAdapters;

    /** 默认系统提示词 */
    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个专业的教育助手「智学导师」（EduMentor），专门帮助中小学生解答学习问题。" +
                    "你的特点是：耐心、细致、善于引导。" +
                    "请用清晰易懂的语言回答问题，必要时给出步骤和示例。" +
                    "如果遇到不确定的问题，请诚实地告诉学生你不确定，而不是给出错误答案。";

    /** RAG 增强专用的系统提示词前缀 */
    private static final String RAG_SYSTEM_PROMPT_PREFIX =
            "你是一个专业的教育助手「智学导师」（EduMentor）。" +
                    "下面是与你问题相关的一些参考资料，请基于这些资料来回答问题。" +
                    "如果参考资料不足以回答问题，请结合你自己的知识来回答，但需要说明哪些是根据参考资料得出的。" +
                    "请用清晰易懂的语言，适合中小学生的理解水平。\n\n" +
                    "=== 参考资料 ===\n";

    public LLMService(LLMConfig llmConfig,
                      TokenTracker tokenTracker,
                      RateLimiter rateLimiter,
                      ObjectMapper objectMapper,
                      List<LLMProviderAdapter> adapters) {
        this.llmConfig = llmConfig;
        this.tokenTracker = tokenTracker;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.providerAdapters = new ConcurrentHashMap<>();

        // 注册所有适配器
        for (LLMProviderAdapter adapter : adapters) {
            this.providerAdapters.put(adapter.getProvider(), adapter);
            log.info("Registered LLM provider adapter: {}", adapter.getProvider());
        }
    }

    // ════════════════════════════════════════════
    // 核心 API
    // ════════════════════════════════════════════

    /**
     * 非流式问答。
     *
     * @param userMessage 用户消息
     * @return LLM 响应
     */
    public LLMResponse ask(String userMessage) {
        return ask(DEFAULT_SYSTEM_PROMPT, userMessage);
    }

    /**
     * 非流式问答（自定义系统提示词）。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return LLM 响应
     */
    public LLMResponse ask(String systemPrompt, String userMessage) {
        LLMProviderAdapter adapter = getCurrentAdapter();
        ProviderConfig config = llmConfig.getCurrentProviderConfig();
        LLMProvider provider = llmConfig.getCurrentProvider();

        // 速率限制检查
        if (!rateLimiter.tryAcquire(provider)) {
            log.warn("Rate limit exceeded for provider: {}", provider);
            return waitAndRetry(adapter, provider, config, systemPrompt, userMessage);
        }

        return executeWithRetry(() -> {
            LLMResponse response = adapter.generate(systemPrompt, userMessage, config,
                    llmConfig.getTemperature(), llmConfig.getMaxTokens());
            tokenTracker.record(response.getTokenUsage(), provider);
            return response;
        }, provider);
    }

    /**
     * 多轮对话（非流式）。
     *
     * @param systemPrompt 系统提示词
     * @param messages     对话历史（最后一条为当前用户消息）
     * @return LLM 响应
     */
    public LLMResponse chat(String systemPrompt, List<ChatMessage> messages) {
        LLMProviderAdapter adapter = getCurrentAdapter();
        ProviderConfig config = llmConfig.getCurrentProviderConfig();
        LLMProvider provider = llmConfig.getCurrentProvider();

        if (!rateLimiter.tryAcquire(provider)) {
            log.warn("Rate limit exceeded for provider: {}", provider);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return executeWithRetry(() -> {
            LLMResponse response = adapter.chat(systemPrompt, messages, config,
                    llmConfig.getTemperature(), llmConfig.getMaxTokens());
            tokenTracker.record(response.getTokenUsage(), provider);
            return response;
        }, provider);
    }

    /**
     * 流式问答。
     *
     * @param userMessage   用户消息
     * @param chunkConsumer 流式块回调
     */
    public void askStream(String userMessage, Consumer<LLMResponse> chunkConsumer) {
        askStream(DEFAULT_SYSTEM_PROMPT, userMessage, chunkConsumer);
    }

    /**
     * 流式问答（自定义系统提示词）。
     *
     * @param systemPrompt  系统提示词
     * @param userMessage   用户消息
     * @param chunkConsumer 流式块回调
     */
    public void askStream(String systemPrompt, String userMessage, Consumer<LLMResponse> chunkConsumer) {
        LLMProviderAdapter adapter = getCurrentAdapter();
        ProviderConfig config = llmConfig.getCurrentProviderConfig();
        LLMProvider provider = llmConfig.getCurrentProvider();

        if (!rateLimiter.tryAcquire(provider)) {
            log.warn("Rate limit exceeded for streaming, provider: {}", provider);
            chunkConsumer.accept(LLMResponse.error("请求过于频繁，请稍后再试", provider, config.getModel()));
            return;
        }

        try {
            adapter.generateStream(systemPrompt, userMessage, config,
                    llmConfig.getTemperature(), llmConfig.getMaxTokens(), response -> {
                        // 记录最终的 Token 用量
                        if (response.isFinished() && response.getTokenUsage() != null) {
                            tokenTracker.record(response.getTokenUsage(), provider);
                        }
                        chunkConsumer.accept(response);
                    });
        } catch (Exception e) {
            log.error("Streaming ask failed: {}", e.getMessage(), e);
            chunkConsumer.accept(LLMResponse.error("流式请求失败: " + e.getMessage(),
                    provider, config.getModel()));
        }
    }

    /**
     * 多轮对话（流式）。
     *
     * @param systemPrompt  系统提示词
     * @param messages      对话历史
     * @param chunkConsumer 流式块回调
     */
    public void chatStream(String systemPrompt, List<ChatMessage> messages,
                           Consumer<LLMResponse> chunkConsumer) {
        LLMProviderAdapter adapter = getCurrentAdapter();
        ProviderConfig config = llmConfig.getCurrentProviderConfig();
        LLMProvider provider = llmConfig.getCurrentProvider();

        if (!rateLimiter.tryAcquire(provider)) {
            chunkConsumer.accept(LLMResponse.error("请求过于频繁，请稍后再试", provider, config.getModel()));
            return;
        }

        try {
            adapter.chatStream(systemPrompt, messages, config,
                    llmConfig.getTemperature(), llmConfig.getMaxTokens(), response -> {
                        if (response.isFinished() && response.getTokenUsage() != null) {
                            tokenTracker.record(response.getTokenUsage(), provider);
                        }
                        chunkConsumer.accept(response);
                    });
        } catch (Exception e) {
            log.error("Streaming chat failed: {}", e.getMessage(), e);
            chunkConsumer.accept(LLMResponse.error("流式对话失败: " + e.getMessage(),
                    provider, config.getModel()));
        }
    }

    // ════════════════════════════════════════════
    // RAG 增强问答
    // ════════════════════════════════════════════

    /**
     * RAG 增强问答 — 将检索到的上下文注入到系统提示词中。
     *
     * @param userMessage 用户消息
     * @param contexts    检索到的相关上下文列表
     * @return LLM 响应
     */
    public LLMResponse askWithContext(String userMessage, List<String> contexts) {
        return askWithContext(DEFAULT_SYSTEM_PROMPT, userMessage, contexts);
    }

    /**
     * RAG 增强问答 — 自定义系统提示词。
     *
     * @param systemPrompt 系统提示词基础
     * @param userMessage  用户消息
     * @param contexts     检索到的相关上下文列表
     * @return LLM 响应
     */
    public LLMResponse askWithContext(String systemPrompt, String userMessage, List<String> contexts) {
        // 构建 RAG 增强的系统提示词
        String ragPrompt = buildRAGPrompt(systemPrompt, contexts);
        return ask(ragPrompt, userMessage);
    }

    /**
     * RAG 增强的流式问答。
     *
     * @param userMessage   用户消息
     * @param contexts      检索到的相关上下文列表
     * @param chunkConsumer 流式块回调
     */
    public void askWithContextStream(String userMessage, List<String> contexts,
                                     Consumer<LLMResponse> chunkConsumer) {
        askWithContextStream(DEFAULT_SYSTEM_PROMPT, userMessage, contexts, chunkConsumer);
    }

    /**
     * RAG 增强的流式问答（自定义系统提示词）。
     *
     * @param systemPrompt  系统提示词基础
     * @param userMessage   用户消息
     * @param contexts      检索到的相关上下文列表
     * @param chunkConsumer 流式块回调
     */
    public void askWithContextStream(String systemPrompt, String userMessage, List<String> contexts,
                                     Consumer<LLMResponse> chunkConsumer) {
        String ragPrompt = buildRAGPrompt(systemPrompt, contexts);
        askStream(ragPrompt, userMessage, chunkConsumer);
    }

    // ════════════════════════════════════════════
    // 结构化输出
    // ════════════════════════════════════════════

    /**
     * 结构化输出问答 — 要求 LLM 返回指定格式的 JSON。
     *
     * @param userMessage  用户消息
     * @param outputClass  输出对象的 Class
     * @param schemaName   JSON Schema 名称（用于构建提示词）
     * @param <T>          输出类型
     * @return 解析后的结构化对象
     */
    public <T> T askStructured(String userMessage, Class<T> outputClass, String schemaName) {
        return askStructured(DEFAULT_SYSTEM_PROMPT, userMessage, outputClass, schemaName);
    }

    /**
     * 结构化输出问答（自定义系统提示词）。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param outputClass  输出对象的 Class
     * @param schemaName   JSON Schema 名称
     * @param <T>          输出类型
     * @return 解析后的结构化对象
     */
    public <T> T askStructured(String systemPrompt, String userMessage,
                               Class<T> outputClass, String schemaName) {
        String schema = getJsonSchema(outputClass, schemaName);
        LLMProviderAdapter adapter = getCurrentAdapter();
        ProviderConfig config = llmConfig.getCurrentProviderConfig();
        LLMProvider provider = llmConfig.getCurrentProvider();

        if (!rateLimiter.tryAcquire(provider)) {
            throw new LlmException("Rate limit exceeded for structured output",
                    LlmException.ErrorCategory.RATE_LIMIT, provider);
        }

        return executeWithRetry(() -> {
            // 使用较低温度以确保输出的确定性
            double structuredTemp = Math.min(llmConfig.getTemperature(), 0.3);

            LLMResponse response = adapter.generateStructured(
                    systemPrompt, userMessage, config, schema, structuredTemp, llmConfig.getMaxTokens());

            tokenTracker.record(response.getTokenUsage(), provider);

            // 解析 JSON 输出为指定类型
            return parseStructuredOutput(response.getContent(), outputClass);
        }, provider);
    }

    // ════════════════════════════════════════════
    // 供应商管理
    // ════════════════════════════════════════════

    /**
     * 获取当前配置的供应商适配器。
     *
     * @return LLMProviderAdapter
     * @throws LlmException 如果当前供应商未注册
     */
    public LLMProviderAdapter getCurrentAdapter() {
        LLMProvider currentProvider = llmConfig.getCurrentProvider();
        LLMProviderAdapter adapter = providerAdapters.get(currentProvider);
        if (adapter == null) {
            throw new LlmException("No adapter registered for provider: " + currentProvider,
                    LlmException.ErrorCategory.CONFIG_ERROR, currentProvider);
        }
        return adapter;
    }

    /**
     * 动态切换供应商（覆盖配置文件中的设置）。
     *
     * @param provider 供应商枚举值
     */
    public void switchProvider(LLMProvider provider) {
        if (!providerAdapters.containsKey(provider)) {
            throw new LlmException("Provider not available: " + provider,
                    LlmException.ErrorCategory.CONFIG_ERROR, provider);
        }
        llmConfig.setProvider(provider.name().toLowerCase());
        log.info("Switched LLM provider to: {}", provider);
    }

    /**
     * 获取当前供应商。
     *
     * @return 当前 LLMProvider
     */
    public LLMProvider getCurrentProvider() {
        return llmConfig.getCurrentProvider();
    }

    /**
     * 获取当前供应商的配置。
     *
     * @return ProviderConfig
     */
    public ProviderConfig getCurrentConfig() {
        return llmConfig.getCurrentProviderConfig();
    }

    /**
     * 健康检查 — 检查所有已注册供应商的可用性。
     *
     * @return 供应商 → 健康状态 映射
     */
    public Map<LLMProvider, Boolean> healthCheck() {
        Map<LLMProvider, Boolean> results = new LinkedHashMap<>();
        for (Map.Entry<LLMProvider, LLMProviderAdapter> entry : providerAdapters.entrySet()) {
            try {
                ProviderConfig config = getConfigForProvider(entry.getKey());
                boolean healthy = entry.getValue().healthCheck(config);
                results.put(entry.getKey(), healthy);
                log.debug("Health check [{}]: {}", entry.getKey(), healthy ? "UP" : "DOWN");
            } catch (Exception e) {
                results.put(entry.getKey(), false);
                log.warn("Health check [{}] failed: {}", entry.getKey(), e.getMessage());
            }
        }
        return results;
    }

    /**
     * 获取当前供应商的 Token 用量统计摘要。
     *
     * @return 统计摘要字符串
     */
    public String getTokenStats() {
        return tokenTracker.getSummary();
    }

    // ════════════════════════════════════════════
    // 内部方法
    // ════════════════════════════════════════════

    /**
     * 构建 RAG 增强的系统提示词。
     */
    private String buildRAGPrompt(String baseSystemPrompt, List<String> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return baseSystemPrompt;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(baseSystemPrompt).append("\n\n");
        sb.append(RAG_SYSTEM_PROMPT_PREFIX).append("\n");

        for (int i = 0; i < contexts.size(); i++) {
            sb.append("--- 参考资料 ").append(i + 1).append(" ---\n");
            sb.append(contexts.get(i)).append("\n\n");
        }

        sb.append("=== 参考资料结束 ===\n");
        sb.append("请基于以上参考资料回答用户的提问。如果参考资料不充分，可以结合你的知识补充，但请注明。");

        return sb.toString();
    }

    /**
     * 获取供应商配置（用于健康检查）。
     */
    private ProviderConfig getConfigForProvider(LLMProvider provider) {
        return llmConfig.getProviderConfig(provider);
    }

    /**
     * 带重试的执行包装。
     */
    private <T> T executeWithRetry(SupplierWithException<T> supplier, LLMProvider provider) {
        int maxRetries = llmConfig.getMaxRetries();
        int attempt = 0;
        long baseDelay = 1000; // 1 second

        while (true) {
            try {
                return supplier.get();
            } catch (LlmException e) {
                attempt++;
                if (attempt > maxRetries || !shouldRetry(e)) {
                    throw e;
                }
                // 指数退避 + 随机抖动
                long delay = (long) (baseDelay * Math.pow(2, attempt - 1) * (0.5 + Math.random() * 0.5));
                log.warn("LLM call failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt, maxRetries, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException("Retry interrupted", LlmException.ErrorCategory.UNKNOWN, provider, ie);
                }
            }
        }
    }

    /**
     * 判断错误是否应该重试。
     */
    private boolean shouldRetry(LlmException e) {
        return switch (e.getCategory()) {
            case NETWORK, RATE_LIMIT, SERVICE_UNAVAILABLE -> true;  // 可重试
            case AUTHENTICATION, TOKEN_LIMIT, CONFIG_ERROR, PARSE_ERROR -> false; // 不可重试
            case API_ERROR -> {
                // HTTP 5xx 可重试，4xx 不可重试
                int status = e.getHttpStatus();
                yield status >= 500 || status == 429;
            }
            case UNKNOWN -> false;
        };
    }

    /**
     * 限流等待后重试（仅用于非流式调用）。
     */
    private LLMResponse waitAndRetry(LLMProviderAdapter adapter, LLMProvider provider,
                                     ProviderConfig config, String systemPrompt, String userMessage) {
        try {
            Thread.sleep(2000); // 等待 2 秒后重试
            if (rateLimiter.tryAcquire(provider)) {
                return executeWithRetry(() -> {
                    LLMResponse response = adapter.generate(systemPrompt, userMessage, config,
                            llmConfig.getTemperature(), llmConfig.getMaxTokens());
                    tokenTracker.record(response.getTokenUsage(), provider);
                    return response;
                }, provider);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return LLMResponse.error("请求被限流，请稍后再试", provider, config.getModel());
    }

    /**
     * 生成 JSON Schema 描述（用于结构化输出提示词）。
     */
    private String getJsonSchema(Class<?> outputClass, String schemaName) {
        // 构建简单的 JSON Schema 提示
        StringBuilder sb = new StringBuilder();
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n");
        sb.append("  \"type\": \"object\",\n");
        sb.append("  \"title\": \"").append(schemaName).append("\",\n");
        sb.append("  \"properties\": {\n");

        try {
            // 尝试通过 Jackson 反射获取字段信息
            var fields = outputClass.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                var field = fields[i];
                String fieldName = field.getName();
                String fieldType = mapJavaTypeToJsonType(field.getType());
                sb.append("    \"").append(fieldName).append("\": {\n");
                sb.append("      \"type\": \"").append(fieldType).append("\"\n");
                sb.append("    }");
                if (i < fields.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("Failed to generate JSON schema for {}: {}", outputClass.getSimpleName(), e.getMessage());
            sb.append("    // Auto-generated schema failed, use simple format\n");
        }

        sb.append("  },\n");
        sb.append("  \"required\": [\"*\"],\n");
        sb.append("  \"additionalProperties\": false\n");
        sb.append("}\n");
        sb.append("```\n");
        sb.append("请严格按此 JSON Schema 格式输出，只输出合法 JSON，不包含任何额外说明。");

        return sb.toString();
    }

    /**
     * 解析结构化输出 JSON。
     */
    private <T> T parseStructuredOutput(String content, Class<T> outputClass) {
        // 尝试提取 JSON 部分（模型有时会在 JSON 前后添加说明文字）
        String jsonContent = extractJsonContent(content);
        try {
            return objectMapper.readValue(jsonContent, outputClass);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse structured output as {}: {}",
                    outputClass.getSimpleName(), e.getMessage());
            log.debug("Raw content: {}", content);
            throw new LlmException("Failed to parse structured output: " + e.getMessage(),
                    LlmException.ErrorCategory.PARSE_ERROR, llmConfig.getCurrentProvider(), e);
        }
    }

    /**
     * 从文本中提取 JSON 内容。
     */
    private String extractJsonContent(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }

        // 尝试查找 ```json ... ``` 代码块
        int jsonBlockStart = text.indexOf("```json");
        if (jsonBlockStart >= 0) {
            int contentStart = jsonBlockStart + 7;
            int jsonBlockEnd = text.indexOf("```", contentStart);
            if (jsonBlockEnd >= 0) {
                return text.substring(contentStart, jsonBlockEnd).trim();
            }
        }

        // 尝试查找第一个 { 和最后一个 }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }

        return text.trim();
    }

    /**
     * Java 类型到 JSON Schema 类型的映射。
     */
    private String mapJavaTypeToJsonType(Class<?> javaType) {
        if (javaType == String.class) return "string";
        if (javaType == Integer.class || javaType == int.class) return "integer";
        if (javaType == Long.class || javaType == long.class) return "integer";
        if (javaType == Double.class || javaType == double.class) return "number";
        if (javaType == Float.class || javaType == float.class) return "number";
        if (javaType == Boolean.class || javaType == boolean.class) return "boolean";
        if (javaType == List.class || javaType == Set.class || javaType.isArray()) return "array";
        return "string"; // 默认
    }

    // ──── 函数式接口 ────

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws LlmException;
    }
}
