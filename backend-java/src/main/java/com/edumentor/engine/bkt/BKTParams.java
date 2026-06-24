package com.edumentor.engine.bkt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * BKT (Bayesian Knowledge Tracing) 模型参数。
 * <p>
 * 四参数贝叶斯知识追踪模型，描述学生在某个知识点上的学习行为特征。
 * 参数含义：
 * <ul>
 *   <li><b>P(L₀)</b> — 初始掌握概率：学习开始前学生已掌握该知识的概率</li>
 *   <li><b>P(T)</b> — 转换概率（学习概率）：一次练习中学生从"未掌握"状态转换到"已掌握"的概率</li>
 *   <li><b>P(G)</b> — 猜测概率：学生在未掌握状态下答对的概率（瞎猜）</li>
 *   <li><b>P(S)</b> — 失误概率：学生在已掌握状态下答错的概率（粗心）</li>
 * </ul>
 * </p>
 *
 * <h3>参数约束</h3>
 * <pre>
 *   0.0 ≤ P(L₀), P(T), P(G), P(S) ≤ 1.0
 *   P(G) + P(S) &lt; 1.0  (防止模型退化)
 * </pre>
 *
 * <h3>默认参数</h3>
 * 提供三种预设参数集：
 * <ul>
 *   <li>{@link #defaultParams()} — 通用默认参数</li>
 *   <li>{@link #easyParams()} — 简单知识点参数（高 P(L₀)，低 P(S)）</li>
 *   <li>{@link #hardParams()} — 困难知识点参数（低 P(L₀)，高 P(G)）</li>
 * </ul>
 *
 * @author EduMentor Team
 */
public final class BKTParams {

    /** 初始掌握概率 P(L₀) — 范围 [0.0, 1.0] */
    private final BigDecimal pLearn0;

    /** 转换概率 P(T) — 范围 [0.0, 1.0] */
    private final double pTransition;

    /** 猜测概率 P(G) — 范围 [0.0, 1.0] */
    private final double pGuess;

    /** 失误概率 P(S) — 范围 [0.0, 1.0] */
    private final double pSlip;

    /**
     * 使用指定参数构造 BKTParams。
     *
     * @param pLearn0     初始掌握概率 P(L₀)
     * @param pTransition 转换概率 P(T)
     * @param pGuess      猜测概率 P(G)
     * @param pSlip       失误概率 P(S)
     * @throws IllegalArgumentException 如果参数超出 [0.0, 1.0] 范围或 P(G)+P(S) >= 1.0
     */
    public BKTParams(BigDecimal pLearn0, double pTransition, double pGuess, double pSlip) {
        validateProbability(pLearn0.doubleValue(), "P(L₀)");
        validateProbability(pTransition, "P(T)");
        validateProbability(pGuess, "P(G)");
        validateProbability(pSlip, "P(S)");
        validateSumLessThanOne(pGuess, pSlip, "P(G) + P(S)");

        this.pLearn0 = pLearn0;
        this.pTransition = pTransition;
        this.pGuess = pGuess;
        this.pSlip = pSlip;
    }

    /**
     * 使用 double 参数构造 BKTParams（自动转换 P(L₀) 为 BigDecimal）。
     */
    public BKTParams(double pLearn0, double pTransition, double pGuess, double pSlip) {
        this(BigDecimal.valueOf(pLearn0), pTransition, pGuess, pSlip);
    }

    // ───── 工厂方法 ─────

    /**
     * 通用默认参数集。
     * <p>
     * P(L₀)=0.15, P(T)=0.30, P(G)=0.15, P(S)=0.10
     * </p>
     */
    public static BKTParams defaultParams() {
        return new BKTParams(0.15, 0.30, 0.15, 0.10);
    }

    /**
     * 简单知识点参数集（易上手知识点）。
     * <p>
     * P(L₀)=0.35, P(T)=0.25, P(G)=0.20, P(S)=0.08
     * </p>
     */
    public static BKTParams easyParams() {
        return new BKTParams(0.35, 0.25, 0.20, 0.08);
    }

    /**
     * 困难知识点参数集（抽象/复杂知识点）。
     * <p>
     * P(L₀)=0.05, P(T)=0.20, P(G)=0.25, P(S)=0.15
     * </p>
     */
    public static BKTParams hardParams() {
        return new BKTParams(0.05, 0.20, 0.25, 0.15);
    }

    /**
     * 根据知识点难度等级选择预设参数集。
     *
     * @param difficulty 难度等级 (1-5)
     * @return 对应的 BKTParams
     */
    public static BKTParams fromDifficulty(int difficulty) {
        if (difficulty <= 2) {
            return easyParams();
        } else if (difficulty >= 4) {
            return hardParams();
        }
        return defaultParams();
    }

    // ───── 校验 ─────

    private static void validateProbability(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " 必须在 [0.0, 1.0] 范围内，当前值: " + value);
        }
    }

    private static void validateSumLessThanOne(double a, double b, String name) {
        if (a + b >= 1.0) {
            throw new IllegalArgumentException(
                    name + " 必须小于 1.0，当前值: " + (a + b));
        }
    }

    // ───── Getters ─────

    /** 获取初始掌握概率 P(L₀) */
    public BigDecimal getPLearn0() {
        return pLearn0;
    }

    /** 获取转换概率 P(T) */
    public double getPTransition() {
        return pTransition;
    }

    /** 获取猜测概率 P(G) */
    public double getPGuess() {
        return pGuess;
    }

    /** 获取失误概率 P(S) */
    public double getPSlip() {
        return pSlip;
    }

    /**
     * 计算 P(correct)，即学生在一次练习中答对的先验概率。
     * <pre>
     *   P(correct) = P(L) * (1-P(S)) + (1-P(L)) * P(G)
     * </pre>
     *
     * @param mastery 当前掌握度 P(L)
     * @return P(correct)
     */
    public double predictCorrect(double mastery) {
        mastery = clamp(mastery);
        return mastery * (1.0 - pSlip) + (1.0 - mastery) * pGuess;
    }

    // ───── 工具方法 ─────

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BKTParams that)) return false;
        return Double.compare(pTransition, that.pTransition) == 0
                && Double.compare(pGuess, that.pGuess) == 0
                && Double.compare(pSlip, that.pSlip) == 0
                && pLearn0.compareTo(that.pLearn0) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pLearn0, pTransition, pGuess, pSlip);
    }

    @Override
    public String toString() {
        return String.format("BKTParams{P(L₀)=%.4f, P(T)=%.4f, P(G)=%.4f, P(S)=%.4f}",
                pLearn0.doubleValue(), pTransition, pGuess, pSlip);
    }
}
