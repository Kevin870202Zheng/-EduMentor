package com.edumentor.engine.bkt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * BKT 知识追踪状态 — 记录学生在某个知识点上的掌握度演变。
 * <p>
 * 每个学生-知识点对持有一个 {@code BKTState} 实例，包含：
 * <ul>
 *   <li>当前掌握概率 P(Lₙ) — 经过 n 次观察后的后验概率</li>
 *   <li>练习计数 — 总练习次数、正确次数、连续正确/错误次数</li>
 *   <li>最近掌握度历史 — 用于趋势分析和可视化</li>
 *   <li>时间戳 — 上次更新时间和状态创建时间</li>
 * </ul>
 * </p>
 *
 * <p><strong>序列化说明：</strong>
 * 本类支持序列化为 JSON 格式，直接存储在 {@code student_profiles.bkt_state} JSONB 字段中。
 * 使用 Jackson 注解确保与数据库存储兼容。
 * </p>
 *
 * @author EduMentor Team
 */
public class BKTState {

    /** 知识点 ID */
    private UUID knowledgePointId;

    /** 当前掌握概率 P(Lₙ) — 范围 [0.0, 1.0] */
    private double mastery;

    /** 初始掌握概率 P(L₀) */
    private double initialMastery;

    /** 总练习次数 */
    private int totalAttempts;

    /** 正确次数 */
    private int correctCount;

    /** 连续正确次数 */
    private int consecutiveCorrect;

    /** 连续错误次数 */
    private int consecutiveWrong;

    /** 最后一次作答是否正确 */
    private boolean lastCorrect;

    /** 掌握度历史记录（按时间顺序，最多保存 50 条） */
    private final List<MasterySnapshot> history;

    /** 上次更新时间 */
    private LocalDateTime lastUpdatedAt;

    /** 状态创建时间 */
    private LocalDateTime createdAt;

    // ───── 历史快照阈值 ─────

    /** 最大历史记录条数 */
    private static final int MAX_HISTORY_SIZE = 50;

    /** 当掌握度变化超过此阈值时记录快照 */
    private static final double SNAPSHOT_THRESHOLD = 0.05;

    public BKTState() {
        this.mastery = 0.15;
        this.initialMastery = 0.15;
        this.totalAttempts = 0;
        this.correctCount = 0;
        this.consecutiveCorrect = 0;
        this.consecutiveWrong = 0;
        this.lastCorrect = false;
        this.history = new ArrayList<>();
        this.lastUpdatedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 构造指定知识点的 BKT 状态。
     *
     * @param knowledgePointId 知识点 ID
     * @param initialMastery   初始掌握概率
     */
    public BKTState(UUID knowledgePointId, double initialMastery) {
        this();
        this.knowledgePointId = Objects.requireNonNull(knowledgePointId, "knowledgePointId must not be null");
        this.mastery = clamp(initialMastery);
        this.initialMastery = this.mastery;
    }

    // ───── 状态更新 ─────

    /**
     * 执行一次贝叶斯更新，根据本次作答结果更新掌握度。
     *
     * @param isCorrect 本次作答是否正确
     * @param pGuess    猜测概率 P(G)
     * @param pSlip     失误概率 P(S)
     * @param pTransition 转换概率 P(T)
     */
    public void update(boolean isCorrect, double pGuess, double pSlip, double pTransition) {
        // 1. 贝叶斯更新：P(Lₙ | 观测) ∝ P(观测 | Lₙ) * P(Lₙ)
        double pMasteryBefore = this.mastery;

        double pEvidence;
        if (isCorrect) {
            // P(correct | L) = P(L)*(1-P(S)) + (1-P(L))*P(G)
            pEvidence = pMasteryBefore * (1.0 - pSlip) + (1.0 - pMasteryBefore) * pGuess;
            // P(L | correct) = P(L)*(1-P(S)) / P(correct)
            this.mastery = (pMasteryBefore * (1.0 - pSlip)) / pEvidence;
        } else {
            // P(wrong | L) = P(L)*P(S) + (1-P(L))*(1-P(G))
            pEvidence = pMasteryBefore * pSlip + (1.0 - pMasteryBefore) * (1.0 - pGuess);
            // P(L | wrong) = P(L)*P(S) / P(wrong)
            this.mastery = (pMasteryBefore * pSlip) / pEvidence;
        }

        // 防止除零或 NaN
        if (Double.isNaN(this.mastery) || Double.isInfinite(this.mastery)) {
            this.mastery = pMasteryBefore;
        }
        this.mastery = clamp(this.mastery);

        // 2. 学习概率：P(Lₙ₊₁) = P(Lₙ | 观测) + (1 - P(Lₙ | 观测)) * P(T)
        double postUpdate = this.mastery + (1.0 - this.mastery) * pTransition;
        this.mastery = clamp(postUpdate);

        // 3. 更新计数
        this.totalAttempts++;
        if (isCorrect) {
            this.correctCount++;
            this.consecutiveCorrect++;
            this.consecutiveWrong = 0;
        } else {
            this.consecutiveCorrect = 0;
            this.consecutiveWrong++;
        }
        this.lastCorrect = isCorrect;
        this.lastUpdatedAt = LocalDateTime.now();

        // 4. 记录历史快照（如果变化超过阈值）
        if (Math.abs(this.mastery - pMasteryBefore) >= SNAPSHOT_THRESHOLD) {
            recordSnapshot();
        }
    }

    /**
     * 仅使用贝叶斯更新（不含学习概率 P(T) 部分）。
     * 适用于单次观测后不希望叠加学习概率的场景。
     */
    public void bayesianUpdateOnly(boolean isCorrect, double pGuess, double pSlip) {
        double pMasteryBefore = this.mastery;

        double pEvidence;
        if (isCorrect) {
            pEvidence = pMasteryBefore * (1.0 - pSlip) + (1.0 - pMasteryBefore) * pGuess;
            this.mastery = (pMasteryBefore * (1.0 - pSlip)) / pEvidence;
        } else {
            pEvidence = pMasteryBefore * pSlip + (1.0 - pMasteryBefore) * (1.0 - pGuess);
            this.mastery = (pMasteryBefore * pSlip) / pEvidence;
        }

        if (Double.isNaN(this.mastery) || Double.isInfinite(this.mastery)) {
            this.mastery = pMasteryBefore;
        }
        this.mastery = clamp(this.mastery);

        this.totalAttempts++;
        if (isCorrect) {
            this.correctCount++;
            this.consecutiveCorrect++;
            this.consecutiveWrong = 0;
        } else {
            this.consecutiveCorrect = 0;
            this.consecutiveWrong++;
        }
        this.lastCorrect = isCorrect;
        this.lastUpdatedAt = LocalDateTime.now();

        if (Math.abs(this.mastery - pMasteryBefore) >= SNAPSHOT_THRESHOLD) {
            recordSnapshot();
        }
    }

    /**
     * 应用学习概率：P(Lₙ₊₁) = P(Lₙ) + (1 - P(Lₙ)) * P(T)
     * 用于非观测场景（如时间推移导致的遗忘/巩固）。
     */
    public void applyLearningProbability(double pTransition) {
        double before = this.mastery;
        this.mastery = clamp(this.mastery + (1.0 - this.mastery) * pTransition);
        if (Math.abs(this.mastery - before) >= SNAPSHOT_THRESHOLD) {
            recordSnapshot();
        }
        this.lastUpdatedAt = LocalDateTime.now();
    }

    // ───── 历史快照 ─────

    private void recordSnapshot() {
        MasterySnapshot snapshot = new MasterySnapshot(
                LocalDateTime.now(),
                this.mastery,
                this.totalAttempts,
                this.lastCorrect);
        this.history.add(snapshot);

        // 裁剪历史记录
        if (this.history.size() > MAX_HISTORY_SIZE) {
            this.history.subList(0, this.history.size() - MAX_HISTORY_SIZE).clear();
        }
    }

    // ───── 状态判断 ─────

    /**
     * 判断是否已达到掌握状态（掌握度 >= 阈值）。
     *
     * @param threshold 掌握阈值，默认 0.80
     * @return true 如果已掌握
     */
    public boolean isMastered(double threshold) {
        return this.mastery >= threshold;
    }

    /**
     * 使用默认阈值 0.80 判断是否已掌握。
     */
    public boolean isMastered() {
        return isMastered(0.80);
    }

    /**
     * 判断是否需要干预（掌握度低于阈值且练习次数足够多）。
     *
     * @param minAttempts 最小练习次数
     * @param stagnationThreshold 停滞阈值
     * @return true 如果可能需要干预
     */
    public boolean needsIntervention(int minAttempts, double stagnationThreshold) {
        if (this.totalAttempts < minAttempts) {
            return false;
        }
        // 如果连续多次错误或掌握度停滞不前
        return this.consecutiveWrong >= 3 || this.mastery < stagnationThreshold;
    }

    /**
     * 预测下一次答对的概率。
     *
     * @param pGuess 猜测概率
     * @param pSlip  失误概率
     * @return P(correct) = P(L) * (1-P(S)) + (1-P(L)) * P(G)
     */
    public double predictCorrectProbability(double pGuess, double pSlip) {
        return this.mastery * (1.0 - pSlip) + (1.0 - this.mastery) * pGuess;
    }

    // ───── 重置 ─────

    /**
     * 将状态重置为初始值。
     */
    public void reset() {
        this.mastery = this.initialMastery;
        this.totalAttempts = 0;
        this.correctCount = 0;
        this.consecutiveCorrect = 0;
        this.consecutiveWrong = 0;
        this.lastCorrect = false;
        this.history.clear();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    // ───── Getters & Setters ─────

    public UUID getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(UUID knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public double getMastery() {
        return mastery;
    }

    public void setMastery(double mastery) {
        this.mastery = clamp(mastery);
    }

    public double getInitialMastery() {
        return initialMastery;
    }

    public void setInitialMastery(double initialMastery) {
        this.initialMastery = clamp(initialMastery);
    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(int totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getConsecutiveCorrect() {
        return consecutiveCorrect;
    }

    public void setConsecutiveCorrect(int consecutiveCorrect) {
        this.consecutiveCorrect = consecutiveCorrect;
    }

    public int getConsecutiveWrong() {
        return consecutiveWrong;
    }

    public void setConsecutiveWrong(int consecutiveWrong) {
        this.consecutiveWrong = consecutiveWrong;
    }

    public boolean isLastCorrect() {
        return lastCorrect;
    }

    public void setLastCorrect(boolean lastCorrect) {
        this.lastCorrect = lastCorrect;
    }

    public List<MasterySnapshot> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ───── 内部类 ─────

    /**
     * 掌握度历史快照 — 记录某时间点的掌握度状态。
     */
    public static class MasterySnapshot {
        private final LocalDateTime timestamp;
        private final double mastery;
        private final int totalAttempts;
        private final boolean wasCorrect;

        public MasterySnapshot(LocalDateTime timestamp, double mastery,
                               int totalAttempts, boolean wasCorrect) {
            this.timestamp = timestamp;
            this.mastery = Math.round(mastery * 10000.0) / 10000.0;
            this.totalAttempts = totalAttempts;
            this.wasCorrect = wasCorrect;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public double getMastery() { return mastery; }
        public int getTotalAttempts() { return totalAttempts; }
        public boolean isWasCorrect() { return wasCorrect; }
    }

    // ───── 工具方法 ─────

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String toString() {
        return String.format("BKTState{kp=%s, mastery=%.4f, attempts=%d, correct=%d}",
                knowledgePointId != null ? knowledgePointId.toString().substring(0, 8) : "null",
                mastery, totalAttempts, correctCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BKTState bktState)) return false;
        return Double.compare(mastery, bktState.mastery) == 0
                && totalAttempts == bktState.totalAttempts
                && Objects.equals(knowledgePointId, bktState.knowledgePointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(knowledgePointId, mastery, totalAttempts);
    }
}
