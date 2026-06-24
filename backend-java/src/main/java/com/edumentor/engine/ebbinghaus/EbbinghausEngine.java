package com.edumentor.engine.ebbinghaus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 艾宾浩斯遗忘曲线排程引擎 — 基于间隔重复（Spaced Repetition）算法。
 * <p>
 * 使用艾宾浩斯遗忘曲线理论，动态计算知识点复习的最佳时间间隔。
 * 随着复习次数增加，间隔逐渐延长，帮助知识从短期记忆转化为长期记忆。
 * </p>
 *
 * <h3>标准复习间隔（天）</h3>
 * <pre>
 *   第 1 次复习  →  1 天
 *   第 2 次复习  →  3 天
 *   第 3 次复习  →  7 天
 *   第 4 次复习  →  14 天
 *   第 5 次复习  →  30 天
 *   第 6+ 次复习 →  60 天（长期保持）
 * </pre>
 *
 * <h3>掌握度调整</h3>
 * <ul>
 *   <li>复习结果优秀（mastery ≥ 0.9）：间隔 × 1.2（延长）</li>
 *   <li>复习结果一般（mastery 0.5 ~ 0.9）：间隔 × 1.0（保持）</li>
 *   <li>复习结果差（mastery < 0.5）：间隔 × 0.5（缩短，需再次复习）</li>
 * </ul>
 *
 * <h3>遗忘曲线衰减模型</h3>
 * <p>
 * 基于公式 R = e^(-t/S)，其中 R 为记忆保存率，t 为经过时间，S 为记忆强度。
 * 每次成功复习后 S 按复习周期倍数增长。
 * </p>
 *
 * @author EduMentor Team
 */
@Component
public class EbbinghausEngine {

    private static final Logger log = LoggerFactory.getLogger(EbbinghausEngine.class);

    /** 标准复习间隔数组（天数），索引对应复习周期（0-based） */
    private static final int[] STANDARD_INTERVALS = {1, 3, 7, 14, 30, 60};

    /** 长期保持间隔（第 6 次复习后） */
    private static final long LONG_TERM_INTERVAL = 60;

    /** 掌握度优秀阈值 — 延长间隔 */
    private static final BigDecimal MASTERY_EXCELLENT_THRESHOLD = new BigDecimal("0.90");

    /** 掌握度及格阈值 — 正常间隔 */
    private static final BigDecimal MASTERY_PASS_THRESHOLD = new BigDecimal("0.50");

    /** 掌握度优秀时的间隔乘数 */
    private static final BigDecimal EXCELLENT_MULTIPLIER = new BigDecimal("1.2");

    /** 掌握度不及格时的间隔乘数 */
    private static final BigDecimal FAIL_MULTIPLIER = new BigDecimal("0.5");

    /** 长期掌握度锁定阈值 — 达到后不再强制排程 */
    public static final BigDecimal MASTERY_LOCK_THRESHOLD = new BigDecimal("0.95");

    /**
     * 计算下次复习日期。
     *
     * @param reviewCycle 当前复习周期（从 1 开始计数，表示已完成复习的次数）
     * @param masteryLevel 本次复习后的掌握度（0-1），可 null
     * @return 下次复习日期
     */
    public LocalDate calculateNextReviewDate(int reviewCycle, BigDecimal masteryLevel) {
        long baseInterval = getBaseInterval(reviewCycle);
        double adjustedInterval = adjustInterval(baseInterval, masteryLevel);
        LocalDate nextDate = LocalDate.now().plusDays((long) adjustedInterval);

        log.debug("Ebbinghaus: cycle={}, mastery={}, baseInterval={}, adjustedInterval={}, nextDate={}",
                reviewCycle, masteryLevel, baseInterval, adjustedInterval, nextDate);

        return nextDate;
    }

    /**
     * 获取标准复习间隔（天数）。
     *
     * @param reviewCycle 复习周期（1-based）
     * @return 间隔天数
     */
    public long getBaseInterval(int reviewCycle) {
        if (reviewCycle <= 0) {
            return STANDARD_INTERVALS[0];
        }
        int index = reviewCycle - 1;
        if (index < STANDARD_INTERVALS.length) {
            return STANDARD_INTERVALS[index];
        }
        return LONG_TERM_INTERVAL;
    }

    /**
     * 根据掌握度调整复习间隔。
     *
     * @param baseInterval 基础间隔（天）
     * @param masteryLevel 掌握度（0-1），null 时按 1.0 处理
     * @return 调整后的间隔（天）
     */
    public double adjustInterval(long baseInterval, BigDecimal masteryLevel) {
        if (masteryLevel == null) {
            return baseInterval;
        }

        if (masteryLevel.compareTo(MASTERY_EXCELLENT_THRESHOLD) >= 0) {
            // 掌握良好，延长间隔
            return BigDecimal.valueOf(baseInterval)
                    .multiply(EXCELLENT_MULTIPLIER)
                    .setScale(0, RoundingMode.HALF_UP)
                    .doubleValue();
        } else if (masteryLevel.compareTo(MASTERY_PASS_THRESHOLD) >= 0) {
            // 掌握一般，保持间隔
            return baseInterval;
        } else {
            // 掌握较差，缩短间隔
            double adjusted = BigDecimal.valueOf(baseInterval)
                    .multiply(FAIL_MULTIPLIER)
                    .setScale(0, RoundingMode.HALF_UP)
                    .doubleValue();
            // 至少 1 天
            return Math.max(1, adjusted);
        }
    }

    /**
     * 计算基于遗忘曲线的当前记忆保存率。
     * <p>
     * 使用公式 R = e^(-t / (S * cycleMultiplier))
     * 其中 t 为距离上次复习的天数，S 为基础记忆强度参数。
     * </p>
     *
     * @param daysSinceLastReview 距离上次复习的天数
     * @param reviewCycle 复习周期（1-based）
     * @return 记忆保存率（0-1）
     */
    public double calculateRetentionRate(long daysSinceLastReview, int reviewCycle) {
        if (daysSinceLastReview <= 0) {
            return 1.0;
        }
        // 基础记忆强度（天），随着复习次数增加而加强
        double memoryStrength = getBaseInterval(reviewCycle) * 1.5;
        if (memoryStrength <= 0) {
            memoryStrength = 1;
        }
        // R = e^(-t / S)
        return Math.exp((double) -daysSinceLastReview / memoryStrength);
    }

    /**
     * 根据复习结果更新掌握度。
     *
     * @param currentMastery 当前掌握度（0-1）
     * @param isCorrect 本次复习是否答对
     * @param responseTimeRatio 答题时间与基准时间的比值（越快越高）
     * @return 更新后的掌握度
     */
    public BigDecimal updateMasteryLevel(BigDecimal currentMastery,
                                         boolean isCorrect,
                                         double responseTimeRatio) {
        if (currentMastery == null) {
            currentMastery = BigDecimal.valueOf(isCorrect ? 0.5 : 0.2);
        }

        double delta;
        if (isCorrect) {
            // 答对：根据答题速度调整提升幅度
            double speedFactor = Math.min(responseTimeRatio, 1.5);
            delta = 0.15 * speedFactor;
        } else {
            // 答错：掌握度下降
            delta = -0.20;
        }

        double newValue = currentMastery.doubleValue() + delta;
        // 限制在 [0, 1] 范围内
        newValue = Math.max(0, Math.min(1, newValue));

        return BigDecimal.valueOf(newValue)
                .setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 判断知识点是否已达到长期掌握（可减少排程频率）。
     *
     * @param masteryLevel 掌握度
     * @return true 如果已达到长期掌握
     */
    public boolean isLongTermMastered(BigDecimal masteryLevel) {
        return masteryLevel != null
                && masteryLevel.compareTo(MASTERY_LOCK_THRESHOLD) >= 0;
    }

    /**
     * 生成建议复习周期映射。
     * <p>
     * 返回从复习周期到间隔天数的完整映射表。
     * </p>
     *
     * @return Map(cycle -> intervalDays)
     */
    public Map<Integer, Long> getIntervalSchedule() {
        Map<Integer, Long> schedule = new HashMap<>();
        for (int i = 0; i < STANDARD_INTERVALS.length; i++) {
            schedule.put(i + 1, (long) STANDARD_INTERVALS[i]);
        }
        schedule.put(STANDARD_INTERVALS.length + 1, LONG_TERM_INTERVAL);
        return schedule;
    }
}
