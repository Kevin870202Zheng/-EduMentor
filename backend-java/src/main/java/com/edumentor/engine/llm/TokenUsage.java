package com.edumentor.engine.llm;

/**
 * Token 用量统计 — 记录单次 LLM 调用的 Token 消耗。
 *
 * @author EduMentor Team
 */
public class TokenUsage {

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final double estimatedCostUsd;

    public TokenUsage(int promptTokens, int completionTokens, String model) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.estimatedCostUsd = estimateCost(promptTokens, completionTokens, model);
    }

    public TokenUsage(int promptTokens, int completionTokens, int totalTokens, double estimatedCostUsd) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public double getEstimatedCostUsd() { return estimatedCostUsd; }

    public TokenUsage merge(TokenUsage other) {
        if (other == null) return this;
        return new TokenUsage(this.promptTokens + other.promptTokens, this.completionTokens + other.completionTokens, this.totalTokens + other.totalTokens, this.estimatedCostUsd + other.estimatedCostUsd);
    }

    private static double estimateCost(int pt, int ct, String model) {
        if (model == null) return 0.0;
        String m = model.toLowerCase();
        double ppk, cpk;
        if (m.contains("gpt-4o-mini")) { ppk = 0.00015; cpk = 0.00060; }
        else if (m.contains("gpt-4o")) { ppk = 0.00250; cpk = 0.01000; }
        else if (m.contains("gpt-4")) { ppk = 0.01000; cpk = 0.03000; }
        else if (m.contains("gpt-3.5")) { ppk = 0.00050; cpk = 0.00150; }
        else if (m.contains("glm")) { ppk = 0.00010; cpk = 0.00010; }
        else if (m.contains("ernie")) { ppk = 0.00008; cpk = 0.00008; }
        else if (m.contains("qwen")) { ppk = 0.00020; cpk = 0.00020; }
        else { ppk = 0.00100; cpk = 0.00200; }
        return Math.round(((pt / 1000.0) * ppk + (ct / 1000.0) * cpk) * 1_000_000.0) / 1_000_000.0;
    }

    @Override
    public String toString() {
        return String.format("TokenUsage{prompt=%d, completion=%d, total=%d, cost=$%.6f}", promptTokens, completionTokens, totalTokens, estimatedCostUsd);
    }
}
