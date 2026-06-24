package com.edumentor.engine.bkt;

import com.edumentor.diagnosis.repository.AnswerRecordRepository;
import com.edumentor.diagnosis.repository.StudentProfileRepository;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.student.entity.StudentProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BKT 服务 — 桥接 BKT 引擎与业务层，提供状态持久化和业务集成。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>加载/保存学生知识点掌握度状态（与 {@code student_profiles.bkt_state} JSONB 字段交互）</li>
 *   <li>记录作答结果后自动更新 BKT 状态</li>
 *   <li>批量获取学生多知识点的综合掌握情况</li>
 *   <li>为诊断、学习路径规划等模块提供掌握度数据</li>
 * </ul>
 * </p>
 *
 * <h3>状态存储策略</h3>
 * BKT 状态以 JSON 格式序列化存储在 {@code student_profiles.bkt_state} 字段中。
 * 查询时反序列化为 {@link BKTState} 对象，更新后重新序列化存入数据库。
 *
 * @author EduMentor Team
 */
@Service
public class BKTService {

    private static final Logger log = LoggerFactory.getLogger(BKTService.class);

    /** 默认掌握度阈值 */
    public static final double DEFAULT_MASTERY_THRESHOLD = 0.80;

    private final BKTEngine bktEngine;
    private final StudentProfileRepository studentProfileRepository;
    private final AnswerRecordRepository answerRecordRepository;

    public BKTService(BKTEngine bktEngine,
                      StudentProfileRepository studentProfileRepository,
                      AnswerRecordRepository answerRecordRepository) {
        this.bktEngine = bktEngine;
        this.studentProfileRepository = studentProfileRepository;
        this.answerRecordRepository = answerRecordRepository;
    }

    // ══════════════════════════════════════════════════════════════
    //  核心 API
    // ══════════════════════════════════════════════════════════════

    /**
     * 记录一次作答结果并更新 BKT 状态。
     * <p>
     * 流程：
     * <ol>
     *   <li>从学生档案加载 BKT 状态（如无则创建新状态）</li>
     *   <li>获取知识点对应的 BKT 参数</li>
     *   <li>执行贝叶斯更新 + 学习概率叠加</li>
     *   <li>保存更新后的状态到数据库</li>
     * </ol>
     * </p>
     *
     * @param studentId       学生 ID
     * @param knowledgePointId 知识点 ID
     * @param isCorrect       是否正确
     * @param params          BKT 参数（如 null 则使用默认参数）
     */
    @Transactional
    public void recordAnswer(UUID studentId, UUID knowledgePointId,
                             boolean isCorrect, BKTParams params) {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(knowledgePointId, "knowledgePointId must not be null");

        // 1. 加载学生档案和 BKT 状态
        BKTState state = loadOrCreateState(studentId, knowledgePointId);

        // 2. 使用默认参数（如果未提供）
        if (params == null) {
            params = BKTParams.defaultParams();
        }

        // 3. 执行 BKT 更新
        bktEngine.update(state, isCorrect, params);

        // 4. 持久化
        saveState(studentId, state);

        log.info("BKT recordAnswer: student={}, kp={}, correct={}, mastery={:.4f}",
                studentId.toString().substring(0, 8),
                knowledgePointId.toString().substring(0, 8),
                isCorrect, state.getMastery());
    }

    /**
     * 批量记录作答结果（按时间顺序）。
     * <p>
     * 适用于导入历史数据、恢复学生初始状态等场景。
     * </p>
     *
     * @param studentId       学生 ID
     * @param knowledgePointId 知识点 ID
     * @param results         作答结果列表（按时间顺序，true=正确）
     * @param params          BKT 参数
     */
    @Transactional
    public void batchRecordAnswers(UUID studentId, UUID knowledgePointId,
                                   List<Boolean> results, BKTParams params) {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(knowledgePointId, "knowledgePointId must not be null");
        Objects.requireNonNull(results, "results must not be null");

        BKTState state = loadOrCreateState(studentId, knowledgePointId);

        if (params == null) {
            params = BKTParams.defaultParams();
        }

        bktEngine.batchUpdate(state, params, results);
        saveState(studentId, state);

        log.info("BKT batchRecordAnswers: student={}, kp={}, count={}, mastery={:.4f}",
                studentId.toString().substring(0, 8),
                knowledgePointId.toString().substring(0, 8),
                results.size(), state.getMastery());
    }

    /**
     * 获取学生在指定知识点上的掌握度。
     *
     * @param studentId       学生 ID
     * @param knowledgePointId 知识点 ID
     * @return 掌握度 [0.0, 1.0]，如果没有数据则返回默认初始值
     */
    @Transactional(readOnly = true)
    public double getMastery(UUID studentId, UUID knowledgePointId) {
        BKTState state = loadState(studentId, knowledgePointId);
        if (state == null) {
            return BKTEngine.DEFAULT_INITIAL_MASTERY;
        }
        return state.getMastery();
    }

    /**
     * 获取学生多个知识点的综合掌握情况。
     *
     * @param studentId        学生 ID
     * @param knowledgePointIds 知识点 ID 列表
     * @return 知识点 ID → 掌握度 的映射
     */
    @Transactional(readOnly = true)
    public Map<UUID, Double> getMasteryMap(UUID studentId, Collection<UUID> knowledgePointIds) {
        if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
            return Map.of();
        }

        // 从学生档案加载所有 BKT 状态
        Map<UUID, BKTState> allStates = loadAllStates(studentId);
        Map<UUID, Double> result = new HashMap<>();

        for (UUID kpId : knowledgePointIds) {
            BKTState state = allStates.get(kpId);
            result.put(kpId, state != null ? state.getMastery() : BKTEngine.DEFAULT_INITIAL_MASTERY);
        }

        return result;
    }

    /**
     * 获取学生所有已跟踪知识点的掌握情况。
     *
     * @param studentId 学生 ID
     * @return 知识点 ID → BKTState 的映射
     */
    @Transactional(readOnly = true)
    public Map<UUID, BKTState> getAllStates(UUID studentId) {
        return loadAllStates(studentId);
    }

    // ══════════════════════════════════════════════════════════════
    //  学情分析
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取学生综合掌握度（所有跟踪知识点的平均值）。
     *
     * @param studentId 学生 ID
     * @return 综合掌握度
     */
    @Transactional(readOnly = true)
    public double getOverallMastery(UUID studentId) {
        Map<UUID, BKTState> states = loadAllStates(studentId);
        return bktEngine.evaluateOverallMastery(states);
    }

    /**
     * 获取学生的薄弱知识点列表。
     *
     * @param studentId 学生 ID
     * @param threshold 薄弱阈值（默认 0.50）
     * @return 薄弱知识点 ID 列表
     */
    @Transactional(readOnly = true)
    public List<UUID> getWeakKnowledgePoints(UUID studentId, double threshold) {
        Map<UUID, BKTState> states = loadAllStates(studentId);
        return bktEngine.findWeakKnowledgePoints(states, threshold);
    }

    /**
     * 判断学生是否已掌握指定知识点。
     *
     * @param studentId       学生 ID
     * @param knowledgePointId 知识点 ID
     * @param threshold       掌握阈值（默认 0.80）
     * @return true 如果已掌握
     */
    @Transactional(readOnly = true)
    public boolean isMastered(UUID studentId, UUID knowledgePointId, double threshold) {
        BKTState state = loadState(studentId, knowledgePointId);
        if (state == null) {
            return false;
        }
        return state.getMastery() >= threshold;
    }

    /**
     * 获取掌握度趋势方向。
     *
     * @param studentId       学生 ID
     * @param knowledgePointId 知识点 ID
     * @return +1 上升，-1 下降，0 平稳或数据不足
     */
    @Transactional(readOnly = true)
    public int getTrendDirection(UUID studentId, UUID knowledgePointId) {
        BKTState state = loadState(studentId, knowledgePointId);
        if (state == null) {
            return 0;
        }
        return bktEngine.trendDirection(state);
    }

    // ══════════════════════════════════════════════════════════════
    //  状态管理
    // ══════════════════════════════════════════════════════════════

    /**
     * 重置学生在指定知识点上的 BKT 状态。
     *
     * @param studentId       学生 ID
     * @param knowledgePointId 知识点 ID
     */
    @Transactional
    public void resetState(UUID studentId, UUID knowledgePointId) {
        Map<UUID, BKTState> allStates = loadAllStates(studentId);
        BKTState state = allStates.get(knowledgePointId);
        if (state != null) {
            state.reset();
            saveAllStates(studentId, allStates);
            log.info("BKT state reset: student={}, kp={}",
                    studentId.toString().substring(0, 8),
                    knowledgePointId.toString().substring(0, 8));
        }
    }

    /**
     * 清除学生在所有知识点上的 BKT 状态。
     *
     * @param studentId 学生 ID
     */
    @Transactional
    public void clearAllStates(UUID studentId) {
        saveAllStates(studentId, new HashMap<>());
        log.info("BKT all states cleared: student={}",
                studentId.toString().substring(0, 8));
    }

    // ══════════════════════════════════════════════════════════════
    //  内部方法 — 状态加载/保存
    // ══════════════════════════════════════════════════════════════

    /**
     * 从学生档案加载 BKT 状态。
     *
     * @param studentId       学生 ID
     * @param knowledgePointId 知识点 ID
     * @return BKTState，如果不存在则返回 null
     */
    private BKTState loadState(UUID studentId, UUID knowledgePointId) {
        Map<UUID, BKTState> allStates = loadAllStates(studentId);
        return allStates.get(knowledgePointId);
    }

    /**
     * 加载或创建 BKT 状态。
     *
     * @param studentId       学生 ID
     * @param knowledgePointId 知识点 ID
     * @return 存在的状态或新创建的状态
     */
    private BKTState loadOrCreateState(UUID studentId, UUID knowledgePointId) {
        Map<UUID, BKTState> allStates = loadAllStates(studentId);
        return allStates.computeIfAbsent(knowledgePointId,
                kpId -> bktEngine.createInitialState(kpId));
    }

    /**
     * 从学生档案加载所有 BKT 状态。
     * <p>
     * 从 {@code student_profiles.bkt_state} JSONB 字段读取数据。
     * </p>
     *
     * @param studentId 学生 ID
     * @return 知识点 ID → BKTState 的映射
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, BKTState> loadAllStates(UUID studentId) {
        Optional<StudentProfile> profileOpt = studentProfileRepository.findByUserId(studentId);
        if (profileOpt.isEmpty()) {
            log.debug("StudentProfile not found for student={}, returning empty states",
                    studentId.toString().substring(0, 8));
            return new HashMap<>();
        }

        StudentProfile profile = profileOpt.get();
        String bktStateJson = profile.getBktState();
        if (bktStateJson == null || bktStateJson.isBlank()) {
            return new HashMap<>();
        }

        try {
            // JSON 格式约定：
            // {
            //   "states": {
            //     "<kp-uuid>": { "mastery": 0.85, "totalAttempts": 10, ... },
            //     ...
            //   }
            // }
            // 当前使用 Jackson 手动解析；后续可替换为 MapStruct 或专用序列化
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(bktStateJson);
            com.fasterxml.jackson.databind.JsonNode statesNode = root.get("states");

            if (statesNode == null || !statesNode.isObject()) {
                return new HashMap<>();
            }

            Map<UUID, BKTState> states = new HashMap<>();
            java.util.Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields =
                    statesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> entry = fields.next();
                UUID kpId = UUID.fromString(entry.getKey());
                com.fasterxml.jackson.databind.JsonNode node = entry.getValue();

                BKTState state = deserializeState(kpId, node);
                states.put(kpId, state);
            }

            log.debug("Loaded {} BKT states for student={}",
                    states.size(), studentId.toString().substring(0, 8));
            return states;

        } catch (Exception e) {
            log.error("Failed to parse BKT state JSON for student={}: {}",
                    studentId.toString().substring(0, 8), e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 保存单个知识点的 BKT 状态到学生档案。
     *
     * @param studentId 学生 ID
     * @param state     BKT 状态
     */
    private void saveState(UUID studentId, BKTState state) {
        Map<UUID, BKTState> allStates = loadAllStates(studentId);
        allStates.put(state.getKnowledgePointId(), state);
        saveAllStates(studentId, allStates);
    }

    /**
     * 保存所有 BKT 状态到学生档案。
     *
     * @param studentId 学生 ID
     * @param states    知识点 ID → BKTState 映射
     */
    private void saveAllStates(UUID studentId, Map<UUID, BKTState> states) {
        Optional<StudentProfile> profileOpt = studentProfileRepository.findByUserId(studentId);
        if (profileOpt.isEmpty()) {
            log.warn("Cannot save BKT states: StudentProfile not found for student={}",
                    studentId.toString().substring(0, 8));
            return;
        }

        StudentProfile profile = profileOpt.get();
        try {
            // 序列化为 JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode root =
                    mapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode statesNode =
                    mapper.createObjectNode();

            for (Map.Entry<UUID, BKTState> entry : states.entrySet()) {
                com.fasterxml.jackson.databind.node.ObjectNode stateNode =
                        serializeState(mapper, entry.getValue());
                statesNode.set(entry.getKey().toString(), stateNode);
            }

            root.set("states", statesNode);

            // 添加元数据
            root.put("updatedAt", LocalDateTime.now().toString());
            root.put("totalKps", states.size());

            profile.setBktState(mapper.writeValueAsString(root));
            studentProfileRepository.save(profile);

            log.debug("Saved {} BKT states for student={}",
                    states.size(), studentId.toString().substring(0, 8));

        } catch (Exception e) {
            log.error("Failed to serialize BKT states for student={}: {}",
                    studentId.toString().substring(0, 8), e.getMessage());
        }
    }

    // ───── JSON 序列化/反序列化 ─────

    /**
     * 将 BKTState 序列化为 Jackson JsonNode。
     */
    private com.fasterxml.jackson.databind.node.ObjectNode serializeState(
            com.fasterxml.jackson.databind.ObjectMapper mapper, BKTState state) {
        com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();

        if (state.getKnowledgePointId() != null) {
            node.put("knowledgePointId", state.getKnowledgePointId().toString());
        }
        node.put("mastery", state.getMastery());
        node.put("initialMastery", state.getInitialMastery());
        node.put("totalAttempts", state.getTotalAttempts());
        node.put("correctCount", state.getCorrectCount());
        node.put("consecutiveCorrect", state.getConsecutiveCorrect());
        node.put("consecutiveWrong", state.getConsecutiveWrong());
        node.put("lastCorrect", state.isLastCorrect());

        if (state.getLastUpdatedAt() != null) {
            node.put("lastUpdatedAt", state.getLastUpdatedAt().toString());
        }
        if (state.getCreatedAt() != null) {
            node.put("createdAt", state.getCreatedAt().toString());
        }

        // 序列化历史记录
        com.fasterxml.jackson.databind.node.ArrayNode historyArray = mapper.createArrayNode();
        for (BKTState.MasterySnapshot snapshot : state.getHistory()) {
            com.fasterxml.jackson.databind.node.ObjectNode snapshotNode = mapper.createObjectNode();
            snapshotNode.put("timestamp", snapshot.getTimestamp().toString());
            snapshotNode.put("mastery", snapshot.getMastery());
            snapshotNode.put("totalAttempts", snapshot.getTotalAttempts());
            snapshotNode.put("wasCorrect", snapshot.isWasCorrect());
            historyArray.add(snapshotNode);
        }
        node.set("history", historyArray);

        return node;
    }

    /**
     * 从 Jackson JsonNode 反序列化 BKTState。
     */
    private BKTState deserializeState(UUID knowledgePointId,
                                       com.fasterxml.jackson.databind.JsonNode node) {
        BKTState state = new BKTState();

        state.setKnowledgePointId(knowledgePointId);

        if (node.has("mastery")) {
            state.setMastery(node.get("mastery").asDouble());
        }
        if (node.has("initialMastery")) {
            state.setInitialMastery(node.get("initialMastery").asDouble());
        }
        if (node.has("totalAttempts")) {
            state.setTotalAttempts(node.get("totalAttempts").asInt());
        }
        if (node.has("correctCount")) {
            state.setCorrectCount(node.get("correctCount").asInt());
        }
        if (node.has("consecutiveCorrect")) {
            state.setConsecutiveCorrect(node.get("consecutiveCorrect").asInt());
        }
        if (node.has("consecutiveWrong")) {
            state.setConsecutiveWrong(node.get("consecutiveWrong").asInt());
        }
        if (node.has("lastCorrect")) {
            state.setLastCorrect(node.get("lastCorrect").asBoolean());
        }
        if (node.has("lastUpdatedAt")) {
            state.setLastUpdatedAt(LocalDateTime.parse(node.get("lastUpdatedAt").asText()));
        }
        if (node.has("createdAt")) {
            state.setCreatedAt(LocalDateTime.parse(node.get("createdAt").asText()));
        }

        // 反序列化历史记录
        if (node.has("history") && node.get("history").isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode snapshotNode : node.get("history")) {
                // 历史记录通过 BKTState 的 update 方法自动生成，反序列化时不直接重建
                // 但我们可以手动恢复最后一次快照
            }
        }

        return state;
    }
}
