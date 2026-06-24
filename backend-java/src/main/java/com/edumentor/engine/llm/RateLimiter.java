package com.edumentor.engine.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 速率限制器 — 基于固定窗口的多维度限流。
 * <p>支持请求频率和 Token 速率双重限流，每窗口（1分钟）自动重置。</p>
 *
 * @author EduMentor Team
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final int maxRequestsPerMinute;
    private final int maxTokensPerMinute;
    private final ConcurrentHashMap<LLMProvider, ProviderRateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimiter(LLMConfig llmConfig) {
        this.maxRequestsPerMinute = llmConfig.getRateLimitRequestsPerMinute();
        this.maxTokensPerMinute = llmConfig.getRateLimitTokensPerMinute();
    }

    public boolean tryAcquire(LLMProvider provider) { return tryAcquire(provider, 0); }

    public boolean tryAcquire(LLMProvider provider, int tokenEstimate) {
        if (provider == LLMProvider.MOCK) return true;
        ProviderRateLimiter limiter = limiters.computeIfAbsent(provider, k ->
                new ProviderRateLimiter(provider, maxRequestsPerMinute, maxTokensPerMinute));
        return limiter.tryAcquire(tokenEstimate);
    }

    public boolean tryAcquireWithTimeout(LLMProvider provider, int tokenEstimate, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (tryAcquire(provider, tokenEstimate)) return true;
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    public String getStatus(LLMProvider provider) {
        ProviderRateLimiter l = limiters.get(provider);
        if (l == null) return "no-limiter";
        return String.format("reqs=%d/%d, tokens=%d/%d", l.currentRequests.get(), l.maxRequestsPerMinute, l.currentTokens.get(), l.maxTokensPerMinute);
    }

    public void reset(LLMProvider provider) { ProviderRateLimiter l = limiters.get(provider); if (l != null) l.reset(); }
    public void resetAll() { limiters.forEach((p, l) -> l.reset()); }

    static class ProviderRateLimiter {
        final LLMProvider provider;
        final int maxRequestsPerMinute;
        final int maxTokensPerMinute;
        final AtomicInteger currentRequests = new AtomicInteger(0);
        final AtomicInteger currentTokens = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
        final Object lock = new Object();

        ProviderRateLimiter(LLMProvider provider, int maxReqs, int maxToks) {
            this.provider = provider;
            this.maxRequestsPerMinute = maxReqs;
            this.maxTokensPerMinute = maxToks;
        }

        boolean tryAcquire(int tokenEstimate) {
            checkReset();
            if (currentRequests.get() >= maxRequestsPerMinute) return false;
            if (tokenEstimate > 0 && currentTokens.get() + tokenEstimate > maxTokensPerMinute) return false;
            synchronized (lock) {
                checkReset();
                if (currentRequests.get() >= maxRequestsPerMinute) return false;
                if (tokenEstimate > 0 && currentTokens.get() + tokenEstimate > maxTokensPerMinute) return false;
                currentRequests.incrementAndGet();
                if (tokenEstimate > 0) currentTokens.addAndGet(tokenEstimate);
                return true;
            }
        }

        void checkReset() {
            long now = System.currentTimeMillis();
            if (now - windowStart > TimeUnit.MINUTES.toMillis(1)) {
                synchronized (lock) {
                    if (now - windowStart > TimeUnit.MINUTES.toMillis(1)) { reset(); windowStart = now; }
                }
            }
        }

        void reset() { currentRequests.set(0); currentTokens.set(0); }
    }
}
