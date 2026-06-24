package com.edumentor.engine.llm;

import com.edumentor.engine.llm.LLMConfig.ProviderConfig;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 供应商适配器接口 — 所有供应商实现的统一抽象。
 * <p>
 * 每个供应商（OpenAI、Ollama、智谱、文心、Mock）都实现此接口，
 * LLMService 通过此接口与具体供应商解耦。
 * </p>
 *
 * <h3>实现要求</h3>
 * <ul>
 *   <li>所有方法必须处理 I/O 异常，包装为 {@link LlmException}</li>
 *   <li>流式回调必须保证线程安全</li>
 *   <li>实现类应使用构造函数注入配置</li>
 * </ul>
 *
 * @author EduMentor Team
 */
public interface LLMProviderAdapter {

    /**
     * 返回此适配器支持的供应商类型。
     *
     * @return LLMProvider 枚举值
     */
    LLMProvider getProvider();

    /**
     * 非流式文本生成。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param config       供应商配置
     * @param temperature  生成温度
     * @param maxTokens    最大 Token 数
     * @return LLM 响应（含完整生成的文本和 Token 用量）
     * @throws LlmException 调用失败时抛出
     */
    LLMResponse generate(String systemPrompt, String userMessage,
                         ProviderConfig config, double temperature, int maxTokens);

    /**
     * 非流式对话生成（支持多轮对话）。
     *
     * @param systemPrompt 系统提示词
     * @param messages     多轮对话历史（最后一条为当前用户消息）
     * @param config       供应商配置
     * @param temperature  生成温度
     * @param maxTokens    最大 Token 数
     * @return LLM 响应
     * @throws LlmException 调用失败时抛出
     */
    LLMResponse chat(String systemPrompt, List<ChatMessage> messages,
                     ProviderConfig config, double temperature, int maxTokens);

    /**
     * 流式文本生成。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param config       供应商配置
     * @param temperature  生成温度
     * @param maxTokens    最大 Token 数
     * @param chunkConsumer 流式块回调（每生成一个文本块调用一次）
     * @throws LlmException 调用失败时抛出
     */
    void generateStream(String systemPrompt, String userMessage,
                        ProviderConfig config, double temperature, int maxTokens,
                        Consumer<LLMResponse> chunkConsumer);

    /**
     * 流式对话生成（支持多轮对话）。
     *
     * @param systemPrompt 系统提示词
     * @param messages     多轮对话历史
     * @param config       供应商配置
     * @param temperature  生成温度
     * @param maxTokens    最大 Token 数
     * @param chunkConsumer 流式块回调
     * @throws LlmException 调用失败时抛出
     */
    void chatStream(String systemPrompt, List<ChatMessage> messages,
                    ProviderConfig config, double temperature, int maxTokens,
                    Consumer<LLMResponse> chunkConsumer);

    /**
     * 结构化输出生成 — 要求模型以特定格式返回结果。
     * 默认实现通过 Prompt 约束 + JSON 解析实现。
     *
     * @param systemPrompt  系统提示词
     * @param userMessage   用户消息
     * @param config        供应商配置
     * @param outputSchema  输出格式描述（JSON Schema 或自然语言说明）
     * @param temperature   生成温度（结构化输出建议 0.1~0.3）
     * @param maxTokens     最大 Token 数
     * @return LLM 响应（content 应为合法的 JSON 字符串）
     * @throws LlmException 调用失败或解析失败时抛出
     */
    default LLMResponse generateStructured(String systemPrompt, String userMessage,
                                           ProviderConfig config, String outputSchema,
                                           double temperature, int maxTokens) {
        // 默认实现：在 Prompt 中加入格式要求，然后解析输出
        String enhancedPrompt = systemPrompt + "\n\n你必须严格按照以下格式输出，只输出合法的JSON，不包含任何其他内容：\n" + outputSchema;
        return generate(enhancedPrompt, userMessage, config, temperature, maxTokens);
    }

    /**
     * 检查供应商服务是否可用（健康检查）。
     *
     * @param config 供应商配置
     * @return true 如果服务可用
     */
    boolean healthCheck(ProviderConfig config);
}
