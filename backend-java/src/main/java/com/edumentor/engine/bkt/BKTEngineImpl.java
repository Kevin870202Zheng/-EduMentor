package com.edumentor.engine.bkt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BKT 引擎实现 — 贝叶斯知识追踪核心算法，线程安全。
 * <p>
 * 基于四参数贝叶斯知识追踪模型，实现学生对知识点掌握度的动态追踪。
 * 引擎本身是无状态的（所有状态在 BKTState 中管理），因此线程安全。
 * BKTState 需要外部同步或使用线程隔离的实例。
 * </p>
 *
 * <h3>并发保证</h3>
 * <ul>
 *   <li>引擎方法本身无共享状态，可并发调用</li>
 *   <li>内部缓存（如知识点参数缓存）使用 ConcurrentHashMap</li>
 *   <li>BKTState 的修改由调用方通过同步或锁保证</li>
 * </ul>
 *
 * @author EduMentor Team
 */
@Service
public class BKTEngineImpl implements BKTEngine {

    private static final Logger log = LoggerFactory.getLogger(BKTEngineImpl.class);

    /** 知识点难度 → BKTParams 缓存 */
    private final Map<Integer, BKTParams> paramsCache = new ConcurrentHashMap<>();

    /** 掌握度判定精度 */
    private static final double EPSILON = 1e-6;

    public BKTEngineImpl() {
        // 预热缓存
        paramsCache.put(1, BKTParams.easyParams());
        paramsCache.put(2, BKTParams.easyParams());
        paramsCache.put(3, BKTParams.defaultParams());
        paramsCache.put(4, BKTParams.hardParams());
        paramsCache.put(5, BKTParams.hardParams());
    }

    // ══════════════════════════════════════════════════════════════
    //  更新
    // ══════════════════════════════════════════════════════════════

    @Override
    public BKTState update(BKTState state, boolean isCorrect, BKTParams params) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(params, "params must not be null");

        double masteryBefore = state.getMastery();
        state.update(isCorrect, params.getPGuess(), params.getPSlip(), params.getPTransition());

        log.debug("BKT update: kp={}, correct={}, mastery {:.4f} → {:.4f}",
                state.getKnowledgePointId(), isCorrect, masteryBefore, state.getMastery());

        return state;
    }

    @Override
    public BKTState bayesianUpdate(BKTState state, boolean isCorrect, BKTParams params) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(params, "params must not be null");

        state.bayesianUpdateOnly(isCorrect, params.getPGuess(), params.getPSlip());
        return state;
    }

    // ══════════════════════════════════════════════════════════════
    //  批量更新
    // ══════════════════════════════════════════════════════════════

    @Override
    public BKTState batchUpdate(BKTState state, BKTParams params, List<Boolean> results) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(params, "params must not be null");
        Objects.requireNonNull(results, "results must not be null");

        for (boolean isCorrect : results) {
            update(state, isCorrect, params);
        }
        return state;
    }

    // ══════════════════════════════════════════════════════════════
    //  预测
    // ══════════════════════════════════════════════════════════════

    @Override
    public double predictCorrectProbability(BKTState state, BKTParams params) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(params, "params must not be null");
        return state.predictCorrectProbability(params.getPGuess(), params.getPSlip());
    }

    @Override
    public double predictMasteryAfterAttempts(BKTState state, BKTParams params,
                                               int futureAttempts, double correctRate) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(params, "params must not be null");

        if (futureAttempts <= 0) {
            return state.getMastery();
        }

        double predictedMastery = state.getMastery();
        for (int i = 0; i < futureAttempts; i++) {
            boolean isCorrect = Math.random() < correctRate;
            double before = predictedMastery;

            double pEvidence;
            if (isCorrect) {
                pEvidence = before * (1.0 - params.getPSlip())
                        + (1.0 - before) * params.getPGuess();
                predictedMastery = (before * (1.0 - params.getPSlip())) / pEvidence;
            } else {
                pEvidence = before * params.getPSlip()
                        + (1.0 - before) * (1.0 - params.getPGuess());
                predictedMastery = (before * params.getPSlip()) / pEvidence;
            }

            if (Double.isNaN(predictedMastery) || Double.isInfinite(predictedMastery)) {
                predictedMastery = before;
            }

            predictedMastery = clamp(predictedMastery
                    + (1.0 - predictedMastery) * params.getPTransition());
        }

        return clamp(predictedMastery);
    }

    @Override
    public int estimateAttemptsToMastery(BKTState state, BKTParams params,
                                          double threshold, double correctRate) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(params, "params must not be null");

        if (state.getMastery() >= threshold) {
            return 0;
        }

        double mastery = state.getMastery();
        int attempts = 0;
        int maxAttempts = 100;

        while (mastery < threshold && attempts < maxAttempts) {
            boolean isCorrect = Math.random() < correctRate;
            double before = mastery;

            double pEvidence;
            if (isCorrect) {
                pEvidence = before * (1.0 - params.getPSlip())
                        + (1.0 - before) * params.getPGuess();
                mastery = (before * (1.0 - params.getPSlip())) / pEvidence;
            } else {
                pEvidence = before * params.getPSlip()
                        + (1.0 - before) * (1.0 - params.getPGuess());
                mastery = (before * params.getPSlip()) / pEvidence;
            }

            if (Double.isNaN(mastery) || Double.isInfinite(mastery)) {
                mastery = before;
            }

            mastery = clamp(mastery + (1.0 - mastery) * params.getPTransition());
            attempts++;
        }

        return attempts >= maxAttempts ? -1 : attempts;
    }

    // ══════════════════════════════════════════════════════════════
    //  查询
    // ══════════════════════════════════════════════════════════════

    @Override
    public double evaluateOverallMastery(Map<UUID, BKTState> states) {
        if (states == null || states.isEmpty()) {
            return 0.0;
        }
        return states.values().stream()
                .mapToDouble(BKTState::getMastery)
                .average()
                .orElse(0.0);
    }

    @Override
    public List<UUID> findWeakKnowledgePoints(Map<UUID, BKTState> states, double threshold) {
        if (states == null || states.isEmpty()) {
            return List.of();
        }
        return states.entrySet().stream()
                .filter(entry -> entry.getValue().getMastery() < threshold)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public int trendDirection(BKTState state) {
        if (state == null) {
            return 0;
        }
        List<BKTState.MasterySnapshot> history = state.getHistory();
        if (history.size() < 3) {
            return 0;
        }
        int size = history.size();
        double recent = history.get(size - 1).getMastery();
        double older = history.get(size - 3).getMastery();
        double diff = recent - older;
        if (Math.abs(diff) < 0.03) {
            return 0;
        }
        return diff > 0 ? 1 : -1;
    }

    @Override
    public double[] confidenceInterval(BKTState state) {
        if (state == null || state.getTotalAttempts() == 0) {
            return new double[]{0.0, 1.0};
        }
        double mastery = state.getMastery();
        int n = state.getTotalAttempts();
        double z = 1.96;
        double denominator = 1.0 + z * z / n;
        double center = (mastery + z * z / (2.0 * n)) / denominator;
        double margin = z * Math.sqrt((mastery * (1.0 - mastery) / n)
                + (z * z / (4.0 * n * n))) / denominator;

        return new double[]{
                clamp(center - margin),
                clamp(center + margin)
        };
    }

    // ══════════════════════════════════════════════════════════════
    //  持久化辅助
    // ══════════════════════════════════════════════════════════════

    @Override
    public BKTState createInitialState(UUID knowledgePointId) {
        return new BKTState(knowledgePointId, DEFAULT_INITIAL_MASTERY);
    }

    @Override
    public BKTState createInitialState(UUID knowledgePointId, double initialMastery) {
        return new BKTState(knowledgePointId, initialMastery);
    }

    /**
     * 根据难度等级获取缓存中的 BKT 参数。
     *
     * @param difficulty 难度等级 (1-5)
     * @return BKTParams
     */
    public BKTParams getParamsForDifficulty(int difficulty) {
        int key = Math.max(1, Math.min(5, difficulty));
        return paramsCache.getOrDefault(key, BKTParams.defaultParams());
    }

    // ══════════════════════════════════════════════════════════════
    //  工具方法
    // ══════════════════════════════════════════════════════════════

    private static double clamp(double value) {
        if (Double.isNaN(value)) return MIN_MASTERY;
        return Math.max(MIN_MASTERY, Math.min(MAX_MASTERY, value));
    }
}
