package com.edumentor.engine.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 用量追踪器 — 线程安全地追踪所有 LLM 调用的 Token 消耗。
 * <p>
 * 支持按供应商、按日期、按模型维度的用量统计。
 * 提供实时查询和定期重置（按日）的能力。
 * </p>
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>实时记录每次 LLM 调用的 Token 消耗</li>
 *   <li>按供应商聚合统计</li>
 *   <li>按日自动重置统计（每天零点清空当日计数）</li>
 *   <li>成本估算汇总</li>
 * </ul>
 *
 * @author EduMentor Team
 */
@Component
public class TokenTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenTracker.class);

    // ──── 按供应商的当日统计 ────
    private final ConcurrentHashMap<LLMProvider, ProviderStats> statsMap = new ConcurrentHashMap<>();

    // ──── 全部供应商汇总 ────
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalCostMicroUsd = new AtomicLong(0); // 总成本（微美元，避免浮点精度问题）

    // ──── 记录日期（用于每日重置） ────
    private volatile LocalDate currentDate = LocalDate.now();

    /**
     * 记录一次 LLM 调用的 Token 用量。
     *
     * @param tokenUsage Token 用量
     * @param provider   供应商
     */
    public void record(TokenUsage tokenUsage, LLMProvider provider) {
        if (tokenUsage == null || provider == null) return;

        checkDailyReset();

        ProviderStats stats = statsMap.computeIfAbsent(provider, k -> new ProviderStats());

        stats.promptTokens.addAndGet(tokenUsage.getPromptTokens());
        stats.completionTokens.addAndGet(tokenUsage.getCompletionTokens());
        stats.totalTokens.addAndGet(tokenUsage.getTotalTokens());
        stats.requestCount.incrementAndGet();
        stats.costMicroUsd.addAndGet(Math.round(tokenUsage.getEstimatedCostUsd() * 1_000_000));

        totalPromptTokens.addAndGet(tokenUsage.getPromptTokens());
        totalCompletionTokens.addAndGet(tokenUsage.getCompletionTokens());
        totalRequests.incrementAndGet();
        totalCostMicroUsd.addAndGet(Math.round(tokenUsage.getEstimatedCostUsd() * 1_000_000));

        if (log.isDebugEnabled()) {
            log.debug("Token tracked [{}]: {}", provider, tokenUsage);
        }
    }

    /**
     * 获取指定供应商的当日统计。
     *
     * @param provider 供应商
     * @return 供应商标记（如果无记录返回空统计）
     */
    public ProviderStats getStats(LLMProvider provider) {
        checkDailyReset();
        return statsMap.getOrDefault(provider, new ProviderStats());
    }

    /**
     * 获取所有供应商的当日统计（按供应商分组）。
     *
     * @return 供应商 → 统计映射
     */
    public Map<LLMProvider, ProviderStats> getAllStats() {
        checkDailyReset();
        return Map.copyOf(statsMap);
    }

    /**
     * 获取全局统计摘要。
     *
     * @return 统计摘要字符串
     */
    public String getSummary() {
        checkDailyReset();
        return String.format(
                "TokenTracker[date=%s, requests=%d, prompt=%d, completion=%d, total=%d, cost=$%.6f]",
                currentDate,
                totalRequests.get(),
                totalPromptTokens.get(),
                totalCompletionTokens.get(),
                totalPromptTokens.get() + totalCompletionTokens.get(),
                totalCostMicroUsd.get() / 1_000_000.0
        );
    }

    /**
     * 获取当日总请求数。
     *
     * @return 请求数
     */
    public long getTotalRequests() {
        checkDailyReset();
        return totalRequests.get();
    }

    /**
     * 获取当日总 Token 消耗。
     *
     * @return 总 Token 数
     */
    public long getTotalTokens() {
        checkDailyReset();
        return totalPromptTokens.get() + totalCompletionTokens.get();
    }

    /**
     * 获取当日总成本（美元）。
     *
     * @return 估算成本
     */
    public double getTotalCostUsd() {
        checkDailyReset();
        return totalCostMicroUsd.get() / 1_000_000.0;
    }

    /**
     * 检查是否跨日，跨日则自动重置统计。
     */
    private void checkDailyReset() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            reset(today);
        }
    }

    /**
     * 手动重置所有统计（通常由定时任务每日零点调用）。
     *
     * @param newDate 新日期
     */
    public synchronized void reset(LocalDate newDate) {
        log.info("TokenTracker daily reset: {} → {}", currentDate, newDate);
        statsMap.clear();
        totalPromptTokens.set(0);
        totalCompletionTokens.set(0);
        totalRequests.set(0);
        totalCostMicroUsd.set(0);
        currentDate = newDate;
    }

    /**
     * 获取当前记录日期。
     *
     * @return 当前日期
     */
    public LocalDate getCurrentDate() {
        return currentDate;
    }

    // ──── 内部统计类 ────

    /**
     * 单一供应商的 Token 统计。
     */
    public static class ProviderStats {
        private final AtomicInteger promptTokens = new AtomicInteger(0);
        private final AtomicInteger completionTokens = new AtomicInteger(0);
        private final AtomicInteger totalTokens = new AtomicInteger(0);
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicLong costMicroUsd = new AtomicLong(0);

        public int getPromptTokens() { return promptTokens.get(); }
        public int getCompletionTokens() { return completionTokens.get(); }
        public int getTotalTokens() { return totalTokens.get(); }
        public int getRequestCount() { return requestCount.get(); }
        public long getCostMicroUsd() { return costMicroUsd.get(); }
        public double getCostUsd() { return costMicroUsd.get() / 1_000_000.0; }

        @Override
        public String toString() {
            return String.format("ProviderStats{requests=%d, tokens=%d, cost=$%.6f}",
                    requestCount.get(), totalTokens.get(), getCostUsd());
        }
    }
}
