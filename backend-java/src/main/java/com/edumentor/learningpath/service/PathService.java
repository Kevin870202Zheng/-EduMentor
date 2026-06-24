package com.edumentor.learningpath.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.learningpath.dto.*;
import com.edumentor.learningpath.entity.LearningPath;
import com.edumentor.learningpath.entity.LearningPathNode;
import com.edumentor.learningpath.entity.PathNodeStatus;
import com.edumentor.learningpath.entity.PathStatus;
import com.edumentor.learningpath.repository.LearningPathNodeRepository;
import com.edumentor.learningpath.repository.LearningPathRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习路径规划服务 — 核心业务逻辑。
 * <p>
 * 提供个性化学习路径的生成、查询、状态管理、智能适配等功能。
 * 路径规划算法基于知识图谱拓扑排序（Kahn 算法）、学生掌握度和知识难度综合排序。
 * 跨模块查询（knowledge_points、knowledge_relations、error_records）通过 EntityManager 完成。
 * </p>
 *
 * <h3>路径规划流程</h3>
 * <ol>
 *   <li>查询课程下所有知识点及前置关系</li>
 *   <li>获取学生各知识点的掌握度（来自已完成路径节点和错题记录）</li>
 *   <li>Kahn 拓扑排序：前置知识点优先</li>
 *   <li>按难度递进、重要性排序调整序列</li>
 *   <li>跳过已掌握知识点（可选）</li>
 *   <li>聚焦到特定知识点（可选）</li>
 *   <li>创建路径及节点记录</li>
 * </ol>
 *
 * @author EduMentor Team
 */
@Service
public class PathService {

    private static final Logger log = LoggerFactory.getLogger(PathService.class);

    private final LearningPathRepository learningPathRepository;
    private final LearningPathNodeRepository learningPathNodeRepository;
    private final EntityManager entityManager;

    public PathService(LearningPathRepository learningPathRepository,
                       LearningPathNodeRepository learningPathNodeRepository,
                       EntityManager entityManager) {
        this.learningPathRepository = learningPathRepository;
        this.learningPathNodeRepository = learningPathNodeRepository;
        this.entityManager = entityManager;
    }

    // ══════════════════════════════════════════════════════════════
    //  路径规划
    // ══════════════════════════════════════════════════════════════

    /**
     * 为指定学生规划个性化学习路径。
     * <p>
     * 算法步骤：
     * <ol>
     *   <li>查询课程下所有知识点（EntityManager 跨模块）</li>
     *   <li>查询知识点间前置关系（knowledge_relations 表）</li>
     *   <li>Kahn 拓扑排序确保前置知识点在前</li>
     *   <li>按难度递进排序</li>
     *   <li>（可选）跳过已掌握知识点</li>
     *   <li>（可选）聚焦到目标知识点</li>
     *   <li>创建路径及节点</li>
     * </ol>
     *
     * @param request 路径规划请求
     * @return 创建好的学习路径 DTO
     */
    @Transactional
    public LearningPathDto planPath(PathPlanRequest request) {
        UUID studentId = request.getStudentId();
        UUID courseId = request.getCourseId();

        log.info("开始为学生 {} 规划课程 {} 的学习路径: name={}", studentId, courseId, request.getName());

        // 1. 查询课程下的知识点（跨模块 EntityManager）
        List<Object[]> kpRows = findKnowledgePointRowsByCourse(courseId);
        if (kpRows.isEmpty()) {
            throw new ResourceNotFoundException("课程下无知识点: courseId=" + courseId);
        }

        // 将查询结果映射为临时对象
        List<KpInfo> allKps = kpRows.stream()
                .map(row -> new KpInfo(
                        (UUID) row[0],    // id
                        (String) row[1],  // name
                        row[2] != null ? ((Number) row[2]).intValue() : 3,  // difficulty
                        row[3] != null ? ((Number) row[3]).intValue() : 3)) // importance
                .collect(Collectors.toList());

        // 2. 查询知识点前置关系
        Map<UUID, List<UUID>> prerequisiteMap = findPrerequisiteRelations(allKps);

        // 3. Kahn 拓扑排序
        List<UUID> sortedKpIds = topologicalSort(allKps, prerequisiteMap);

        // 4. 按难度递进微调（同层按难度升序）
        List<KpInfo> sortedKps = sortedKpIds.stream()
                .map(id -> allKps.stream().filter(kp -> kp.id.equals(id)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 5. 聚焦模式：截取到目标知识点
        UUID focusKpId = request.getFocusKpId() != null ? request.getFocusKpId() : null;
        if (focusKpId != null) {
            sortedKps = truncateToTarget(sortedKps, focusKpId);
        }

        // 6. 跳过已掌握的知识点
        Set<UUID> masteredKpIds = Collections.emptySet();
        if (Boolean.TRUE.equals(request.getSkipMastered())) {
            masteredKpIds = findMasteredKnowledgePointIds(studentId);
        }

        // 7. 创建路径实体
        LearningPath learningPath = new LearningPath();
        learningPath.setStudentId(studentId);
        learningPath.setCourseId(courseId);
        learningPath.setCreatedBy(studentId);
        learningPath.setName(request.getName());
        learningPath.setDescription(request.getDescription());
        learningPath.setStatus(PathStatus.DRAFT);
        if (request.getDailyMinutes() != null) {
            learningPath.setDailyMinutes(request.getDailyMinutes());
        }
        learningPath = learningPathRepository.save(learningPath);
        final LearningPath savedPath = learningPath;

        // 8. 创建路径节点
        List<LearningPathNode> nodes = new ArrayList<>();
        int orderIndex = 0;
        for (KpInfo kp : sortedKps) {
            if (masteredKpIds.contains(kp.id)) {
                continue;
            }
            LearningPathNode node = new LearningPathNode();
            node.setLearningPath(savedPath);
            node.setKnowledgePointId(kp.id);
            node.setKnowledgePointName(kp.name);
            node.setOrderIndex(orderIndex++);
            node.setStatus(PathNodeStatus.PENDING);
            node.setIsRecommended(true);
            node.setEstimatedMinutes(estimateMinutesByDifficulty(kp.difficulty));
            nodes.add(node);
        }

        List<LearningPathNode> savedNodes = learningPathNodeRepository.saveAll(nodes);
        savedPath.setTotalNodes(savedNodes.size());
        savedPath.setCompletedNodes(0);
        savedPath.setProgress(0);
        savedPath.getNodes().addAll(savedNodes);
        learningPathRepository.save(savedPath);

        log.info("学习路径创建完成: pathId={}, 节点数量={}", savedPath.getId(), savedNodes.size());
        return LearningPathDto.fromEntity(savedPath);
    }

    // ══════════════════════════════════════════════════════════════
    //  查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 根据 ID 获取学习路径详情（含节点）。
     *
     * @param pathId 路径 ID
     * @return 路径 DTO
     */
    @Transactional(readOnly = true)
    public LearningPathDto getPath(UUID pathId) {
        LearningPath path = learningPathRepository.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("学习路径", pathId));
        // 显式初始化节点（避免懒加载异常）
        path.getNodes().size();
        return LearningPathDto.fromEntity(path);
    }

    /**
     * 获取学生的所有学习路径。
     *
     * @param studentId 学生用户 ID
     * @return 路径 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<LearningPathDto> getStudentPaths(UUID studentId) {
        TypedQuery<LearningPath> query = entityManager.createQuery(
                "SELECT lp FROM LearningPath lp WHERE lp.studentId = :studentId ORDER BY lp.createdAt DESC",
                LearningPath.class);
        query.setParameter("studentId", studentId);
        List<LearningPath> result = query.getResultList();
        // 显式初始化节点，避免在事务外访问懒加载代理
        result.forEach(p -> p.getNodes().size());
        return result.stream()
                .map(LearningPathDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取学生当前活跃的路径。
     *
     * @param studentId 学生用户 ID
     * @return 活跃路径 DTO（可能为空）
     */
    @Transactional(readOnly = true)
    public Optional<LearningPathDto> getActivePath(UUID studentId) {
        Optional<LearningPath> pathOpt = learningPathRepository
                .findTopByStudentIdAndStatusOrderByCreatedAtDesc(studentId, PathStatus.ACTIVE);
        pathOpt.ifPresent(p -> p.getNodes().size());
        return pathOpt.map(LearningPathDto::fromEntity);
    }

    /**
     * 获取课程的知识图谱结构（节点 + 关系）。
     * <p>
     * 使用 EntityManager 跨模块查询 knowledge_points 和 knowledge_relations 表。
     * 若提供了 studentId，还会标记掌握状态和薄弱点。
     * </p>
     *
     * @param courseId  课程 ID
     * @param studentId 学生用户 ID（可选，用于标记掌握度和薄弱点）
     * @return 知识图谱 DTO
     */
    @Transactional(readOnly = true)
    public KnowledgeGraphDto getKnowledgeGraph(UUID courseId, UUID studentId) {
        // 获取知识点
        List<Object[]> kpRows = findKnowledgePointRowsByCourse(courseId);
        Set<UUID> masteredIds = (studentId != null)
                ? findMasteredKnowledgePointIds(studentId)
                : Collections.emptySet();
        Set<UUID> weakIds = (studentId != null)
                ? findWeakKnowledgePointIds(studentId)
                : Collections.emptySet();

        List<KnowledgeGraphDto.GraphNode> nodes = kpRows.stream().map(row -> {
            UUID id = (UUID) row[0];
            String name = (String) row[1];
            Integer difficulty = row[2] != null ? ((Number) row[2]).intValue() : 3;
            Integer importance = row[3] != null ? ((Number) row[3]).intValue() : 3;
            KnowledgeGraphDto.GraphNode node = new KnowledgeGraphDto.GraphNode(id, name, difficulty, importance);
            node.setMasteryLevel(masteredIds.contains(id) ? 1.0 : 0.0);
            node.setWeak(weakIds.contains(id));
            return node;
        }).collect(Collectors.toList());

        // 获取关系连线
        List<Object[]> relations = findKnowledgeRelations(courseId);
        List<KnowledgeGraphDto.GraphEdge> edges = relations.stream().map(row -> {
            UUID sourceId = (UUID) row[0];
            UUID targetId = (UUID) row[1];
            String relationType = (String) row[2];
            Double weight = row[3] != null ? ((Number) row[3]).doubleValue() : 1.0;
            return new KnowledgeGraphDto.GraphEdge(sourceId, targetId, relationType, weight);
        }).collect(Collectors.toList());

        return new KnowledgeGraphDto(nodes, edges);
    }

    // ══════════════════════════════════════════════════════════════
    //  路径状态管理
    // ══════════════════════════════════════════════════════════════

    /**
     * 激活路径（状态变为 ACTIVE）。
     * <p>
     * 只有 DRAFT 或 PAUSED 状态的路径可以激活。
     * 激活时自动将第一个 PENDING 节点标记为 IN_PROGRESS。
     * </p>
     *
     * @param pathId 路径 ID
     * @return 更新后的路径 DTO
     */
    @Transactional
    public LearningPathDto activatePath(UUID pathId) {
        LearningPath path = learningPathRepository.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("学习路径", pathId));

        if (path.getStatus() != PathStatus.DRAFT && path.getStatus() != PathStatus.PAUSED) {
            throw new ValidationException("只有 DRAFT 或 PAUSED 状态的路径可以激活，当前状态: " + path.getStatus());
        }

        path.setStatus(PathStatus.ACTIVE);
        path = learningPathRepository.save(path);

        // 自动将第一个 PENDING 节点设为 IN_PROGRESS
        learningPathNodeRepository.findTopByLearningPathIdAndStatusOrderByOrderIndexAsc(
                        pathId, PathNodeStatus.PENDING)
                .ifPresent(firstNode -> {
                    firstNode.markInProgress();
                    learningPathNodeRepository.save(firstNode);
                });

        // 重新加载以获取最新节点状态
        path = learningPathRepository.findById(pathId).orElse(path);
        path.getNodes().size();
        log.info("路径已激活: pathId={}", pathId);
        return LearningPathDto.fromEntity(path);
    }

    /**
     * 暂停路径（状态变为 PAUSED）。
     * <p>
     * 只有 ACTIVE 状态的路径可以暂停。
     * </p>
     *
     * @param pathId 路径 ID
     * @return 更新后的路径 DTO
     */
    @Transactional
    public LearningPathDto pausePath(UUID pathId) {
        LearningPath path = learningPathRepository.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("学习路径", pathId));

        if (path.getStatus() != PathStatus.ACTIVE) {
            throw new ValidationException("只有 ACTIVE 状态的路径可以暂停，当前状态: " + path.getStatus());
        }

        path.setStatus(PathStatus.PAUSED);
        path = learningPathRepository.save(path);
        path.getNodes().size();
        log.info("路径已暂停: pathId={}", pathId);
        return LearningPathDto.fromEntity(path);
    }

    /**
     * 完成路径（状态变为 COMPLETED）。
     * <p>
     * 将所有未完成节点标记为 SKIPPED，并将路径标记为完成。
     * </p>
     *
     * @param pathId 路径 ID
     * @return 更新后的路径 DTO
     */
    @Transactional
    public LearningPathDto completePath(UUID pathId) {
        LearningPath path = learningPathRepository.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("学习路径", pathId));

        if (path.getStatus() == PathStatus.COMPLETED) {
            throw new ValidationException("路径已经处于完成状态");
        }

        // 将所有未完成的节点标记为跳过
        List<LearningPathNode> pendingNodes = learningPathNodeRepository
                .findByLearningPathIdAndStatus(pathId, PathNodeStatus.PENDING);
        for (LearningPathNode node : pendingNodes) {
            node.skip();
        }
        learningPathNodeRepository.saveAll(pendingNodes);

        // 重新计算进度
        path.setStatus(PathStatus.COMPLETED);
        path.recalculateProgress();
        path = learningPathRepository.save(path);
        path.getNodes().size();
        log.info("路径已完成: pathId={}", pathId);
        return LearningPathDto.fromEntity(path);
    }

    // ══════════════════════════════════════════════════════════════
    //  节点进度管理
    // ══════════════════════════════════════════════════════════════

    /**
     * 更新路径节点的进度状态。
     * <p>
     * 支持将节点标记为 IN_PROGRESS / COMPLETED / SKIPPED。
     * 当节点完成时，自动重新计算路径整体进度。
     * 所有节点完成后，自动将路径标记为 COMPLETED。
     * </p>
     *
     * @param request 进度更新请求
     * @return 更新后的路径 DTO
     */
    @Transactional
    public LearningPathDto updateNodeProgress(PathProgressUpdateRequest request) {
        LearningPathNode node = learningPathNodeRepository.findById(request.getNodeId())
                .orElseThrow(() -> new ResourceNotFoundException("路径节点", request.getNodeId()));

        PathNodeStatus newStatus;
        try {
            newStatus = PathNodeStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("不支持的状态: " + request.getStatus()
                    + "，可选值: IN_PROGRESS, COMPLETED, SKIPPED");
        }

        switch (newStatus) {
            case IN_PROGRESS:
                node.markInProgress();
                break;
            case COMPLETED:
                if (request.getActualMinutes() != null) {
                    node.setActualMinutes(request.getActualMinutes());
                }
                node.markCompleted();
                break;
            case SKIPPED:
                node.skip();
                break;
            default:
                throw new ValidationException("不支持的状态变更: " + newStatus);
        }

        learningPathNodeRepository.save(node);

        // 重新计算路径进度
        LearningPath path = node.getLearningPath();
        path.recalculateProgress();

        // 检查是否所有节点都已完成
        long pendingCount = learningPathNodeRepository
                .countByLearningPathIdAndStatus(path.getId(), PathNodeStatus.PENDING);
        long inProgressCount = learningPathNodeRepository
                .countByLearningPathIdAndStatus(path.getId(), PathNodeStatus.IN_PROGRESS);
        if (pendingCount == 0 && inProgressCount == 0) {
            path.setStatus(PathStatus.COMPLETED);
        }

        learningPathRepository.save(path);
        path.getNodes().size();
        return LearningPathDto.fromEntity(path);
    }

    /**
     * 获取路径中的下一个待学习节点。
     * <p>
     * 查找第一个 PENDING 状态的节点作为推荐的下一个学习目标。
     * </p>
     *
     * @param pathId 路径 ID
     * @return 下一个待学节点 DTO（可能为空）
     */
    @Transactional(readOnly = true)
    public Optional<LearningPathNodeDto> getNextNode(UUID pathId) {
        return learningPathNodeRepository
                .findTopByLearningPathIdAndStatusOrderByOrderIndexAsc(pathId, PathNodeStatus.PENDING)
                .map(LearningPathNodeDto::fromEntity);
    }

    // ══════════════════════════════════════════════════════════════
    //  智能适配
    // ══════════════════════════════════════════════════════════════

    /**
     * 智能适配学习路径 — 根据最新学情调整路径节点。
     * <p>
     * 适配策略说明：
     * <ul>
     *   <li><b>REORDER</b> — 根据知识点难度重新排序未完成节点</li>
     *   <li><b>SHORTEN</b> — 跳过已掌握的知识点，缩短路径</li>
     *   <li><b>EXPAND</b> — 为薄弱知识点添加补充节点</li>
     *   <li><b>FOCUS_WEAK</b> — 薄弱知识点优先排列</li>
     * </ul>
     *
     * @param request 适配请求
     * @return 更新后的路径 DTO
     */
    @Transactional
    public LearningPathDto adaptPath(PathAdaptRequest request) {
        LearningPath path = learningPathRepository.findById(request.getPathId())
                .orElseThrow(() -> new ResourceNotFoundException("学习路径", request.getPathId()));

        List<LearningPathNode> allNodes = learningPathNodeRepository
                .findByLearningPathIdOrderByOrderIndexAsc(path.getId());

        // 分离已完成节点和待处理节点
        List<LearningPathNode> completedNodes = allNodes.stream()
                .filter(n -> n.getStatus() == PathNodeStatus.COMPLETED)
                .collect(Collectors.toList());
        List<LearningPathNode> remainingNodes = allNodes.stream()
                .filter(n -> n.getStatus() == PathNodeStatus.PENDING
                        || n.getStatus() == PathNodeStatus.IN_PROGRESS)
                .collect(Collectors.toList());

        String strategy = request.getAdaptStrategy() != null
                ? request.getAdaptStrategy().toUpperCase() : "REORDER";

        switch (strategy) {
            case "REORDER":
                // 按知识点难度重新排序剩余节点
                remainingNodes.sort(Comparator.comparingInt(
                        n -> getKnowledgePointDifficulty(n.getKnowledgePointId())));
                log.info("REORDER 适配: pathId={}, 节点数={}", path.getId(), remainingNodes.size());
                break;

            case "SHORTEN":
                // 跳过已掌握的知识点
                Set<UUID> masteredIds = findMasteredKnowledgePointIds(path.getStudentId());
                int before = remainingNodes.size();
                remainingNodes.removeIf(n -> masteredIds.contains(n.getKnowledgePointId()));
                int removed = before - remainingNodes.size();
                log.info("SHORTEN 适配: pathId={}, 移除了 {} 个已掌握节点", path.getId(), removed);
                break;

            case "FOCUS_WEAK":
                // 薄弱知识点优先排列
                Set<UUID> weakIds = findWeakKnowledgePointIds(path.getStudentId());
                List<LearningPathNode> weakNodes = remainingNodes.stream()
                        .filter(n -> weakIds.contains(n.getKnowledgePointId()))
                        .collect(Collectors.toList());
                List<LearningPathNode> otherNodes = remainingNodes.stream()
                        .filter(n -> !weakIds.contains(n.getKnowledgePointId()))
                        .collect(Collectors.toList());
                weakNodes.addAll(otherNodes);
                remainingNodes = weakNodes;
                log.info("FOCUS_WEAK 适配: pathId={}, 薄弱节点数={}", path.getId(), weakNodes.size());
                break;

            case "EXPAND":
                // 为薄弱知识点添加补充节点
                Set<UUID> weakIdsForExpand = findWeakKnowledgePointIds(path.getStudentId());
                Set<UUID> existingKpIds = allNodes.stream()
                        .map(LearningPathNode::getKnowledgePointId)
                        .collect(Collectors.toSet());

                // 如果请求中提供了新知识点 ID，添加为补充节点
                if (request.getNewKpIds() != null && !request.getNewKpIds().isEmpty()) {
                    int maxOrder = allNodes.stream()
                            .mapToInt(LearningPathNode::getOrderIndex)
                            .max().orElse(0);
                    for (UUID newKpId : request.getNewKpIds()) {
                        if (!existingKpIds.contains(newKpId)) {
                            LearningPathNode newNode = new LearningPathNode();
                            newNode.setLearningPath(path);
                            newNode.setKnowledgePointId(newKpId);
                            newNode.setKnowledgePointName(findKpNameById(newKpId));
                            newNode.setOrderIndex(++maxOrder);
                            newNode.setStatus(PathNodeStatus.PENDING);
                            newNode.setIsRecommended(false);
                            newNode.setEstimatedMinutes(
                                    estimateMinutesByDifficulty(getKnowledgePointDifficulty(newKpId)));
                            remainingNodes.add(newNode);
                        }
                    }
                }
                log.info("EXPAND 适配: pathId={}, 补充节点后总数={}", path.getId(), remainingNodes.size());
                break;

            default:
                throw new ValidationException("不支持的适配策略: " + request.getAdaptStrategy()
                        + "，可选值: REORDER, SHORTEN, EXPAND, FOCUS_WEAK");
        }

        // 重新编号所有待处理节点
        int orderIndex = completedNodes.size();
        for (LearningPathNode node : remainingNodes) {
            node.setOrderIndex(orderIndex++);
        }

        // 保存变更
        List<LearningPathNode> allToSave = new ArrayList<>(remainingNodes);
        learningPathNodeRepository.saveAll(allToSave);

        // 重新计算路径进度
        path.recalculateProgress();
        path = learningPathRepository.save(path);
        path.getNodes().size();

        log.info("路径适配完成: pathId={}, strategy={}", path.getId(), strategy);
        return LearningPathDto.fromEntity(path);
    }

    // ══════════════════════════════════════════════════════════════
    //  私有辅助方法 — EntityManager 跨模块查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询课程下的所有知识点（跨模块，使用 EntityManager）。
     * 返回 [id, name, difficulty, importance] 数组。
     */
    @SuppressWarnings("unchecked")
    private List<Object[]> findKnowledgePointRowsByCourse(UUID courseId) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT kp.id, kp.name, kp.difficulty, kp.importance " +
                "FROM KnowledgePoint kp WHERE kp.courseId = :courseId ORDER BY kp.orderIndex ASC",
                Object[].class);
        query.setParameter("courseId", courseId);
        return query.getResultList();
    }

    /**
     * 查询知识点间的前置关系（PREREQUISITE 类型，跨模块）。
     * 返回映射：targetKpId -> [sourceKpId, ...] (目标知识点所依赖的前置知识点)
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, List<UUID>> findPrerequisiteRelations(List<KpInfo> kps) {
        if (kps.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> kpIds = kps.stream().map(kp -> kp.id).collect(Collectors.toList());
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT kr.sourceKpId, kr.targetKpId FROM KnowledgeRelation kr " +
                "WHERE kr.sourceKpId IN :kpIds AND kr.targetKpId IN :kpIds " +
                "AND kr.relationType = 'PREREQUISITE'", Object[].class);
        query.setParameter("kpIds", kpIds);

        Map<UUID, List<UUID>> prereqMap = new HashMap<>();
        for (KpInfo kp : kps) {
            prereqMap.put(kp.id, new ArrayList<>());
        }
        for (Object[] row : query.getResultList()) {
            UUID sourceId = (UUID) row[0];
            UUID targetId = (UUID) row[1];
            prereqMap.computeIfAbsent(targetId, k -> new ArrayList<>()).add(sourceId);
        }
        return prereqMap;
    }

    /**
     * Kahn 拓扑排序 — 确保前置知识点排在前面。
     * <p>
     * 算法步骤：
     * <ol>
     *   <li>计算每个节点的入度（依赖的前置知识点数量）</li>
     *   <li>入度为 0 的节点入队</li>
     *   <li>依次出队，将其加入排序结果，并更新邻接节点的入度</li>
     *   <li>若图中存在环，将环中节点按难度追加到末尾</li>
     * </ol>
     *
     * @param kps            所有知识点
     * @param prerequisiteMap 前置关系映射
     * @return 拓扑排序后的知识点 ID 列表
     */
    private List<UUID> topologicalSort(List<KpInfo> kps,
                                        Map<UUID, List<UUID>> prerequisiteMap) {
        // 构建图结构
        Map<UUID, Integer> inDegree = new HashMap<>();
        Map<UUID, List<UUID>> adjacencyList = new HashMap<>();

        for (KpInfo kp : kps) {
            UUID id = kp.id;
            inDegree.put(id, 0);
            adjacencyList.put(id, new ArrayList<>());
        }

        // 构建边：sourceKpId → targetKpId
        for (Map.Entry<UUID, List<UUID>> entry : prerequisiteMap.entrySet()) {
            UUID targetId = entry.getKey();
            for (UUID sourceId : entry.getValue()) {
                adjacencyList.get(sourceId).add(targetId);
                inDegree.merge(targetId, 1, Integer::sum);
            }
        }

        // 入度为 0 的节点入队
        Queue<UUID> queue = new LinkedList<>();
        for (KpInfo kp : kps) {
            if (inDegree.get(kp.id) == 0) {
                queue.add(kp.id);
            }
        }

        List<UUID> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            sorted.add(current);

            for (UUID neighbor : adjacencyList.getOrDefault(current, Collections.emptyList())) {
                inDegree.merge(neighbor, -1, Integer::sum);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // 处理环形依赖中的节点（入度未归零），按难度追加到末尾
        Set<UUID> sortedSet = new HashSet<>(sorted);
        List<KpInfo> unsorted = kps.stream()
                .filter(kp -> !sortedSet.contains(kp.id))
                .sorted(Comparator.comparingInt(kp -> kp.difficulty))
                .collect(Collectors.toList());
        for (KpInfo kp : unsorted) {
            sorted.add(kp.id);
        }

        log.debug("拓扑排序完成: 总知识点={}, 排序后={}, 环中节点={}",
                kps.size(), sorted.size(), unsorted.size());
        return sorted;
    }

    /**
     * 截取到目标知识点（含目标知识点及其前置路径上的所有节点）。
     *
     * @param sortedKps 已排序的知识点列表
     * @param targetId  目标知识点 ID
     * @return 截取后的知识点列表
     */
    private List<KpInfo> truncateToTarget(List<KpInfo> sortedKps, UUID targetId) {
        int targetIndex = -1;
        for (int i = 0; i < sortedKps.size(); i++) {
            if (sortedKps.get(i).id.equals(targetId)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            throw new ResourceNotFoundException("目标知识点不在当前课程的路径中: kpId=" + targetId);
        }
        return sortedKps.subList(0, targetIndex + 1);
    }

    /**
     * 查询学生已掌握的知识点 ID 集合。
     * <p>
     * 从已完成的学习路径节点中获取已掌握的知识点。
     * 使用 EntityManager 跨模块查询。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private Set<UUID> findMasteredKnowledgePointIds(UUID studentId) {
        jakarta.persistence.Query query = entityManager.createQuery(
                "SELECT DISTINCT lpn.knowledgePointId FROM LearningPathNode lpn " +
                "WHERE lpn.learningPath.studentId = :studentId AND lpn.status = 'COMPLETED'");
        query.setParameter("studentId", studentId);
        List<Object> resultList = query.getResultList();
        Set<UUID> result = new HashSet<>();
        for (Object obj : resultList) {
            if (obj instanceof UUID) {
                result.add((UUID) obj);
            }
        }
        return result;
    }

    /**
     * 查询学生薄弱知识点 ID 集合。
     * <p>
     * 从 error_records 表中获取未复习的错题知识点。
     * 使用 EntityManager 原生 SQL 查询（跨模块，避免实体依赖）。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private Set<UUID> findWeakKnowledgePointIds(UUID studentId) {
        jakarta.persistence.Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT knowledge_point_id FROM error_records " +
                "WHERE student_id = :studentId AND is_reviewed = false");
        query.setParameter("studentId", studentId);
        List<Object> resultList = query.getResultList();
        Set<UUID> result = new HashSet<>();
        for (Object obj : resultList) {
            if (obj instanceof UUID) {
                result.add((UUID) obj);
            } else if (obj instanceof java.util.UUID) {
                result.add((java.util.UUID) obj);
            }
        }
        return result;
    }

    /**
     * 获取知识点的难度等级（跨模块 EntityManager 查询）。
     */
    private int getKnowledgePointDifficulty(UUID kpId) {
        TypedQuery<Integer> query = entityManager.createQuery(
                "SELECT kp.difficulty FROM KnowledgePoint kp WHERE kp.id = :kpId", Integer.class);
        query.setParameter("kpId", kpId);
        return query.getResultStream().findFirst().orElse(3);
    }

    /**
     * 根据知识点 ID 查询知识点名称（跨模块）。
     */
    private String findKpNameById(UUID kpId) {
        TypedQuery<String> query = entityManager.createQuery(
                "SELECT kp.name FROM KnowledgePoint kp WHERE kp.id = :kpId", String.class);
        query.setParameter("kpId", kpId);
        return query.getResultStream().findFirst().orElse(null);
    }

    /**
     * 根据难度估算学习时长（分钟）。
     */
    private int estimateMinutesByDifficulty(int difficulty) {
        int baseMinutes = 30;
        return baseMinutes + (difficulty - 1) * 15;
    }

    /**
     * 查询课程下所有知识点关系（跨模块 EntityManager）。
     * 返回 [sourceKpId, targetKpId, relationType, weight] 数组。
     */
    @SuppressWarnings("unchecked")
    private List<Object[]> findKnowledgeRelations(UUID courseId) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT kr.sourceKpId, kr.targetKpId, kr.relationType, kr.weight " +
                "FROM KnowledgeRelation kr " +
                "WHERE kr.sourceKpId IN (SELECT kp.id FROM KnowledgePoint kp WHERE kp.courseId = :courseId)",
                Object[].class);
        query.setParameter("courseId", courseId);
        return query.getResultList();
    }

    // ══════════════════════════════════════════════════════════════
    //  内部数据类
    // ══════════════════════════════════════════════════════════════

    /**
     * 知识点信息 — 临时数据结构，避免直接依赖 course 模块实体。
     */
    private static class KpInfo {
        final UUID id;
        final String name;
        final int difficulty;
        final int importance;

        KpInfo(UUID id, String name, int difficulty, int importance) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.importance = importance;
        }
    }
}
