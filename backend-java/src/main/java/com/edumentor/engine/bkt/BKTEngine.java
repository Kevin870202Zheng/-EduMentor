package com.edumentor.engine.bkt;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BKT (Bayesian Knowledge Tracing) 引擎接口 — 贝叶斯知识追踪核心算法。
 * <p>
 * 基于四参数贝叶斯知识追踪模型，实现学生对知识点掌握度的动态追踪。
 * 核心功能包括：
 * </p>
 * <ul>
 *   <li><b>update</b> — 根据单次作答结果更新掌握度</li>
 *   <li><b>batchUpdate</b> — 批量按时间顺序更新掌握度</li>
 *   <li><b>predict</b> — 预测答对概率或练习后掌握度</li>
 *   <li><b>query</b> — 评估综合掌握度、查找薄弱知识点</li>
 *   <li><b>persist</b> — 创建/重置 BKT 状态</li>
 * </ul>
 *
 * <h3>算法原理</h3>
 * <pre>
 *   1. 贝叶斯更新（给定观测 O）:
 *      P(Lₙ | O) = P(O | Lₙ) * P(Lₙ) / P(O)
 *
 *      其中:
 *        P(correct | L) = P(L) * (1-P(S)) + (1-P(L)) * P(G)
 *        P(wrong | L)   = P(L) * P(S) + (1-P(L)) * (1-P(G))
 *
 *   2. 学习概率叠加:
 *      P(Lₙ₊₁) = P(Lₙ | O) + (1 - P(Lₙ | O)) * P(T)
 *
 *   3. 预测:
 *      P(correct) = P(L) * (1-P(S)) + (1-P(L)) * P(G)
 * </pre>
 *
 * @author EduMentor Team
 */
public interface BKTEngine {

    /** 默认掌握阈值 — 掌握度 >= 0.80 视为已掌握 */
    double DEFAULT_MASTERY_THRESHOLD = 0.80;

    /** 默认初始掌握概率 */
    double DEFAULT_INITIAL_MASTERY = 0.15;

    /** 最小掌握度（防止浮点数下溢） */
    double MIN_MASTERY = 0.001;

    /** 最大掌握度 */
    double MAX_MASTERY = 0.999;

    // ══════════════════════════════════════════════════════════════
    //  更新 (Update)
    // ══════════════════════════════════════════════════════════════

    /**
     * 执行完整的 BKT 更新：贝叶斯更新 + 学习概率叠加。
     * 最常用的更新方法，适用于学生完成一次练习后的知识追踪更新。
     *
     * @param state     当前 BKT 状态（会被就地修改）
     * @param isCorrect 本次作答是否正确
     * @param params    BKT 四参数
     * @return 更新后的 BKTState（与入参相同引用）
     */
    BKTState update(BKTState state, boolean isCorrect, BKTParams params);

    /**
     * 仅执行贝叶斯更新（不含学习概率叠加）。
     * 适用于纯评估场景，如批量历史数据回放。
     *
     * @param state     当前 BKT 状态（会被就地修改）
     * @param isCorrect 本次作答是否正确
     * @param params    BKT 四参数
     * @return 更新后的 BKTState
     */
    BKTState bayesianUpdate(BKTState state, boolean isCorrect, BKTParams params);

    // ══════════════════════════════════════════════════════════════
    //  批量更新 (Batch Update)
    // ══════════════════════════════════════════════════════════════

    /**
     * 批量处理多道题的作答结果（按时间顺序依次更新）。
     * 适用于导入历史答题数据后的状态恢复。
     *
     * @param state   初始 BKT 状态
     * @param params  BKT 参数
     * @param results 作答结果列表（按时间顺序，true = 正确）
     * @return 更新后的 BKT 状态
     */
    BKTState batchUpdate(BKTState state, BKTParams params, List<Boolean> results);

    // ══════════════════════════════════════════════════════════════
    //  预测 (Predict)
    // ══════════════════════════════════════════════════════════════

    /**
     * 预测学生答对指定知识点题目的概率。
     *
     * @param state  当前 BKT 状态
     * @param params BKT 参数
     * @return P(correct) 在 [0.0, 1.0] 范围内
     */
    double predictCorrectProbability(BKTState state, BKTParams params);

    /**
     * 预测学生经过 n 次练习后的掌握度（蒙特卡洛模拟）。
     *
     * @param state           当前状态
     * @param params          BKT 参数
     * @param futureAttempts  未来练习次数
     * @param correctRate     假设的答对率 [0.0, 1.0]
     * @return 预测的掌握度
     */
    double predictMasteryAfterAttempts(BKTState state, BKTParams params,
                                       int futureAttempts, double correctRate);

    /**
     * 估计达到掌握阈值所需的最少练习次数。
     *
     * @param state       当前状态
     * @param params      BKT 参数
     * @param threshold   掌握阈值（默认 0.80）
     * @param correctRate 假设的答对率
     * @return 估计所需的练习次数，超过上限返回 -1
     */
    int estimateAttemptsToMastery(BKTState state, BKTParams params,
                                  double threshold, double correctRate);

    // ══════════════════════════════════════════════════════════════
    //  查询 (Query)
    // ══════════════════════════════════════════════════════════════

    /**
     * 批量评估学生对多个知识点的综合掌握情况（加权平均）。
     *
     * @param states 知识点 → BKTState 映射
     * @return 综合掌握度
     */
    double evaluateOverallMastery(Map<UUID, BKTState> states);

    /**
     * 筛选出薄弱知识点（掌握度低于阈值）。
     *
     * @param states    知识点 → BKTState 映射
     * @param threshold 薄弱阈值
     * @return 薄弱知识点 ID 列表
     */
    List<UUID> findWeakKnowledgePoints(Map<UUID, BKTState> states, double threshold);

    /**
     * 计算掌握度的趋势方向（上升/下降/平稳）。
     *
     * @param state BKT 状态
     * @return +1 上升，-1 下降，0 平稳或数据不足
     */
    int trendDirection(BKTState state);

    /**
     * 获取掌握度的置信区间（基于练习次数估算，Wilson Score）。
     *
     * @param state BKT 状态
     * @return [lowerBound, upperBound]
     */
    double[] confidenceInterval(BKTState state);

    // ══════════════════════════════════════════════════════════════
    //  持久化辅助 (Persist)
    // ══════════════════════════════════════════════════════════════

    /**
     * 为指定知识点创建初始 BKT 状态（使用默认初始掌握度）。
     *
     * @param knowledgePointId 知识点 ID
     * @return 初始化的 BKTState
     */
    BKTState createInitialState(UUID knowledgePointId);

    /**
     * 使用指定初始掌握度创建 BKT 状态。
     *
     * @param knowledgePointId 知识点 ID
     * @param initialMastery   初始掌握概率
     * @return 初始化的 BKTState
     */
    BKTState createInitialState(UUID knowledgePointId, double initialMastery);

    /**
     * 将掌握度映射到分类等级。
     *
     * @param mastery 掌握度
     * @return 等级: "未掌握"、"初步掌握"、"部分掌握"、"基本掌握"、"完全掌握"
     */
    static String masteryLevel(double mastery) {
        if (mastery < 0.20) return "未掌握";
        if (mastery < 0.40) return "初步掌握";
        if (mastery < 0.60) return "部分掌握";
        if (mastery < 0.80) return "基本掌握";
        return "完全掌握";
    }

    /**
     * 将掌握度归一化到 [0.0, 1.0] 范围内。
     */
    static double normalizeMastery(double mastery) {
        if (Double.isNaN(mastery)) return MIN_MASTERY;
        return Math.max(MIN_MASTERY, Math.min(MAX_MASTERY, mastery));
    }
}
