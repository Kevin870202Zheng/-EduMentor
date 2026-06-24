package com.edumentor.engine.llm;

import com.edumentor.engine.llm.LLMConfig.ProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Mock LLM 供应商适配器 — 用于开发和测试环境。
 * <p>
 * 不调用任何外部 API，直接根据输入返回预设的响应。
 * 支持模拟延迟、错误等场景。
 * </p>
 *
 * @author EduMentor Team
 */
@Component
public class MockProvider implements LLMProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockProvider.class);

    /** 模拟的延迟时间（毫秒） */
    private volatile long simulatedDelayMs = 200;

    /** 是否模拟流式响应 */
    private volatile boolean simulateStreaming = true;

    @Override
    public LLMProvider getProvider() {
        return LLMProvider.MOCK;
    }

    @Override
    public LLMResponse generate(String systemPrompt, String userMessage,
                                ProviderConfig config, double temperature, int maxTokens) {
        log.info("[Mock] generate called: system={}, user={}",
                truncate(systemPrompt, 50), truncate(userMessage, 50));

        simulateDelay();

        String response = buildMockResponse(systemPrompt, userMessage);
        TokenUsage tokenUsage = estimateTokenUsage(systemPrompt, userMessage, response);

        return LLMResponse.success(response, tokenUsage, LLMProvider.MOCK, "mock-model", simulatedDelayMs);
    }

    @Override
    public LLMResponse chat(String systemPrompt, List<ChatMessage> messages,
                            ProviderConfig config, double temperature, int maxTokens) {
        log.info("[Mock] chat called: system={}, messages={}",
                truncate(systemPrompt, 50), messages.size());

        simulateDelay();

        String lastUserMsg = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getContent();
        String response = buildMockResponse(systemPrompt, lastUserMsg);
        TokenUsage tokenUsage = estimateTokenUsage(systemPrompt, lastUserMsg, response);

        return LLMResponse.success(response, tokenUsage, LLMProvider.MOCK, "mock-model", simulatedDelayMs);
    }

    @Override
    public void generateStream(String systemPrompt, String userMessage,
                               ProviderConfig config, double temperature, int maxTokens,
                               Consumer<LLMResponse> chunkConsumer) {
        log.info("[Mock] generateStream called: system={}, user={}",
                truncate(systemPrompt, 50), truncate(userMessage, 50));

        String response = buildMockResponse(systemPrompt, userMessage);

        if (simulateStreaming) {
            // 逐字符模拟流式输出
            for (char c : response.toCharArray()) {
                simulateDelay(30); // 每个字符 30ms 延迟
                chunkConsumer.accept(LLMResponse.streamChunk(String.valueOf(c), LLMProvider.MOCK, "mock-model"));
            }
        } else {
            chunkConsumer.accept(LLMResponse.streamChunk(response, LLMProvider.MOCK, "mock-model"));
        }

        TokenUsage tokenUsage = estimateTokenUsage(systemPrompt, userMessage, response);
        chunkConsumer.accept(LLMResponse.streamEnd(LLMProvider.MOCK, "mock-model", tokenUsage, "stop"));
    }

    @Override
    public void chatStream(String systemPrompt, List<ChatMessage> messages,
                           ProviderConfig config, double temperature, int maxTokens,
                           Consumer<LLMResponse> chunkConsumer) {
        String lastUserMsg = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getContent();
        String response = buildMockResponse(systemPrompt, lastUserMsg);

        if (simulateStreaming) {
            for (char c : response.toCharArray()) {
                simulateDelay(30);
                chunkConsumer.accept(LLMResponse.streamChunk(String.valueOf(c), LLMProvider.MOCK, "mock-model"));
            }
        } else {
            chunkConsumer.accept(LLMResponse.streamChunk(response, LLMProvider.MOCK, "mock-model"));
        }

        TokenUsage tokenUsage = estimateTokenUsage(systemPrompt, lastUserMsg, response);
        chunkConsumer.accept(LLMResponse.streamEnd(LLMProvider.MOCK, "mock-model", tokenUsage, "stop"));
    }

    @Override
    public boolean healthCheck(ProviderConfig config) {
        return true; // Mock 永远可用
    }

    /**
     * 根据输入构建模拟响应。
     */
    private String buildMockResponse(String systemPrompt, String userMessage) {
        StringBuilder sb = new StringBuilder();

        // 检测常见的问答模式并给出 Mock 响应
        if (containsAny(userMessage, "你好", "您好", "hello", "hi")) {
            sb.append("你好！我是智学导师（EduMentor）AI 助手。");
            sb.append(" 我可以帮你解答学习问题、分析知识掌握情况、规划学习路径。");
            sb.append(" 请问有什么可以帮助你的？");
        } else if (containsAny(userMessage, "诊断", "分析", "掌握度")) {
            sb.append("根据你的学习数据分析，当前知识掌握情况如下：\n\n");
            sb.append("1. 基础概念：掌握度 85%，建议巩固\n");
            sb.append("2. 进阶应用：掌握度 62%，需要加强练习\n");
            sb.append("3. 综合推理：掌握度 43%，是本阶段的薄弱环节\n\n");
            sb.append("建议重点关注综合推理类题目的练习。");
        } else if (containsAny(userMessage, "路径", "计划", "方案")) {
            sb.append("根据你的学习目标和当前水平，推荐以下学习路径：\n\n");
            sb.append("第一阶段（基础巩固，3天）：回顾核心概念\n");
            sb.append("第二阶段（能力提升，5天）：专项突破训练\n");
            sb.append("第三阶段（综合应用，7天）：模拟测试+错题复盘");
        } else if (containsAny(userMessage, "题", "练习", "题目")) {
            sb.append("这是一道考察理解能力的题目。\n\n");
            sb.append("解题思路：\n");
            sb.append("1. 首先理解题目条件\n");
            sb.append("2. 分析已知信息与目标的关系\n");
            sb.append("3. 运用相关公式或原理求解\n\n");
            sb.append("**答案提示**：这是一个 Mock 响应，实际使用时请配置真实的 LLM 供应商。");
        } else {
            sb.append("感谢你的提问！这是一个 Mock 响应，当前系统配置为开发模式（Mock 供应商）。\n\n");
            sb.append("要使用真实的 AI 能力，请配置 application.yml 中的 llm 部分：\n");
            sb.append("- 设置 llm.provider 为 openai / ollama / zhipu / wenxin\n");
            sb.append("- 配置相应供应商的 API 密钥和端点");
        }

        return sb.toString();
    }

    private void simulateDelay() {
        simulateDelay(simulatedDelayMs);
    }

    private void simulateDelay(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private TokenUsage estimateTokenUsage(String systemPrompt, String userMessage, String response) {
        int promptTokens = (systemPrompt.length() + userMessage.length()) / 2; // 粗略估算
        int completionTokens = response.length() / 2;
        return new TokenUsage(promptTokens, completionTokens, "mock-model");
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ──── 测试辅助方法 ────

    public void setSimulatedDelayMs(long delayMs) {
        this.simulatedDelayMs = delayMs;
    }

    public void setSimulateStreaming(boolean simulateStreaming) {
        this.simulateStreaming = simulateStreaming;
    }
}
