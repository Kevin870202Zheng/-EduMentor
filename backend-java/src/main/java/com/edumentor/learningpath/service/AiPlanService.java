package com.edumentor.learningpath.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.engine.llm.ChatMessage;
import com.edumentor.engine.llm.LLMResponse;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.engine.rag.RAGEngine;
import com.edumentor.entity.enums.ChatRole;
import com.edumentor.entity.enums.MessageType;
import com.edumentor.learningpath.dto.AiPathPlanResult;
import com.edumentor.learningpath.dto.AiPlanChatRequest;
import com.edumentor.learningpath.dto.AiPlanResponse;
import com.edumentor.learningpath.dto.AiPlanStartRequest;
import com.edumentor.learningpath.dto.LearningPathDto;
import com.edumentor.learningpath.dto.PathTemplateNodeDto;
import com.edumentor.learningpath.entity.LearningPath;
import com.edumentor.learningpath.entity.LearningPathNode;
import com.edumentor.learningpath.entity.PathNodeStatus;
import com.edumentor.learningpath.entity.PathStatus;
import com.edumentor.learningpath.repository.LearningPathNodeRepository;
import com.edumentor.learningpath.repository.LearningPathRepository;
import com.edumentor.qa.entity.ChatHistory;
import com.edumentor.qa.repository.ChatHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 对话式路径规划服务。
 * <p>
 * 流程：学生描述目标 → RAG 检索候选知识点 → LLM 多轮对话（追问/澄清）→
 * 用户确认后 askStructured 输出结构化路径 JSON → 校验并落库 DRAFT 路径（source=AI）。
 * 会话持久化到 chat_history（sessionId）。
 * </p>
 *
 * @author EduMentor Team
 */
@Service
public class AiPlanService {

    private static final Logger log = LoggerFactory.getLogger(AiPlanService.class);

    /** 单轮对话最大历史轮数（与 QAService 保持一致） */
    private static final int MAX_HISTORY_ROUNDS = 10;

    /** 结构化输出候选池最大数量（控制 prompt 长度） */
    private static final int MAX_CANDIDATES = 40;

    private final LLMService llmService;
    private final RAGEngine ragEngine;
    private final ChatHistoryRepository chatHistoryRepository;
    private final LearningPathRepository learningPathRepository;
    private final LearningPathNodeRepository learningPathNodeRepository;
    private final EntityManager entityManager;

    public AiPlanService(LLMService llmService,
                         Optional<RAGEngine> ragEngine,
                         ChatHistoryRepository chatHistoryRepository,
                         LearningPathRepository learningPathRepository,
                         LearningPathNodeRepository learningPathNodeRepository,
                         EntityManager entityManager) {
        this.llmService = llmService;
        this.ragEngine = ragEngine.orElse(null);
        this.chatHistoryRepository = chatHistoryRepository;
        this.learningPathRepository = learningPathRepository;
        this.learningPathNodeRepository = learningPathNodeRepository;
        this.entityManager = entityManager;
    }

    /**
     * 开启 AI 规划会话。
     * <p>
     * 保存用户目标 → RAG 检索候选知识点 → LLM 首轮回复（追问/建议）→ 保存助手消息。
     * </p>
     *
     * @param request 开启请求
     * @return 会话响应（sessionId + 首轮回复 + 候选）
     */
    @Transactional
    public AiPlanResponse start(AiPlanStartRequest request) {
        String sessionId = generateSessionId();
        UUID userId = request.getStudentId();
        UUID courseId = request.getCourseId();
        String goal = request.getGoal();

        // 1. 保存用户目标
        saveMessage(userId, courseId, sessionId, ChatRole.USER, MessageType.TEXT, goal, null);

        // 2. RAG 检索候选知识点
        List<RAGEngine.DocumentChunk> docs = retrieve(goal, courseId, 8);
        List<PathTemplateNodeDto> candidates = extractCandidates(docs, courseId);

        // 3. LLM 首轮回复（追问/建议）
        String systemPrompt = buildChatSystemPrompt(candidates, courseId, false);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.userMessage(goal));
        LLMResponse response = llmService.chat(systemPrompt, messages);

        // 4. 保存助手回复
        saveMessage(userId, courseId, sessionId, ChatRole.ASSISTANT, MessageType.ANSWER,
                response.getContent(), null);

        AiPlanResponse result = new AiPlanResponse();
        result.setSessionId(sessionId);
        result.setReply(response.getContent());
        result.setCandidates(candidates);
        log.info("AI 规划会话已开启: sessionId={}, userId={}, candidates={}",
                sessionId, userId, candidates.size());
        return result;
    }

    /**
     * AI 规划多轮对话。
     * <p>
     * generatePath=false 时普通多轮对话；generatePath=true 时让 LLM 输出
     * 结构化路径 JSON 并落库 DRAFT 路径。
     * </p>
     *
     * @param request 对话请求
     * @return 会话响应
     */
    @Transactional
    public AiPlanResponse chat(AiPlanChatRequest request) {
        UUID userId = request.getStudentId();
        UUID courseId = request.getCourseId();
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        // 1. 保存用户消息
        saveMessage(userId, courseId, sessionId, ChatRole.USER, MessageType.TEXT, message, null);

        // 2. 读取对话历史（不含当前消息）
        List<ChatMessage> history = buildHistoryMessages(sessionId);

        // 3. 生成路径模式
        if (Boolean.TRUE.equals(request.getGeneratePath())) {
            return generatePath(userId, courseId, sessionId, message, history);
        }

        // 4. 普通多轮对话（RAG 检索 + LLM）
        List<RAGEngine.DocumentChunk> docs = retrieve(message, courseId, 6);
        List<PathTemplateNodeDto> candidates = extractCandidates(docs, courseId);
        String systemPrompt = buildChatSystemPrompt(candidates, courseId, true);

        List<ChatMessage> messages = new ArrayList<>(history);
        messages.add(ChatMessage.userMessage(message));
        LLMResponse response = llmService.chat(systemPrompt, messages);

        saveMessage(userId, courseId, sessionId, ChatRole.ASSISTANT, MessageType.ANSWER,
                response.getContent(), null);

        AiPlanResponse result = new AiPlanResponse();
        result.setSessionId(sessionId);
        result.setReply(response.getContent());
        return result;
    }

    /**
     * 生成路径：RAG 检索候选池 → LLM 结构化输出 → 校验 → 落库 DRAFT。
     */
    private AiPlanResponse generatePath(UUID userId, UUID courseId, String sessionId,
                                        String message, List<ChatMessage> history) {
        // 1. 检索候选知识点池
        List<RAGEngine.DocumentChunk> docs = retrieve(message, courseId, 15);
        List<PathTemplateNodeDto> candidates = extractCandidates(docs, courseId);
        if (candidates.isEmpty()) {
            throw new ValidationException("未检索到可用的候选知识点，请稍后再试或提供更明确的学习目标");
        }
        if (candidates.size() > MAX_CANDIDATES) {
            candidates = candidates.subList(0, MAX_CANDIDATES);
        }

        // 2. 构建结构化输出请求（候选池强约束，LLM 不得编造）
        String candidateDesc = buildCandidateDescription(candidates);
        String userPrompt = "用户需求：" + message + "\n\n【候选知识点列表】\n" + candidateDesc
                + "\n\n请从中挑选 5~15 个最相关的知识点，生成一份学习路径。"
                + "每个节点必须填写候选列表中的 knowledgePointId，不得编造。";

        AiPathPlanResult plan;
        try {
            plan = llmService.askStructured(buildStructuredSystemPrompt(), userPrompt,
                    AiPathPlanResult.class, "AiPathPlanResult");
        } catch (Exception e) {
            log.warn("AI 结构化输出失败: {}", e.getMessage());
            throw new ValidationException("AI 生成路径失败，请重试或调整描述");
        }

        // 3. 校验并过滤无效知识点
        if (plan == null || plan.getNodes() == null || plan.getNodes().isEmpty()) {
            throw new ValidationException("AI 未生成有效的路径节点，请重新描述学习目标");
        }
        Set<UUID> candidateIds = candidates.stream()
                .map(PathTemplateNodeDto::getKnowledgePointId)
                .collect(Collectors.toSet());
        List<AiPathPlanResult.AiPathNode> validNodes = plan.getNodes().stream()
                .filter(n -> n.getKnowledgePointId() != null && candidateIds.contains(n.getKnowledgePointId()))
                .collect(Collectors.toList());
        if (validNodes.isEmpty()) {
            throw new ValidationException("AI 选择的节点不在课程知识库中，请重新生成");
        }
        log.info("AI 路径节点校验: 原始={}, 有效={}", plan.getNodes().size(), validNodes.size());

        // 4. 落库 DRAFT 路径（source=AI）
        LearningPath path = new LearningPath();
        path.setStudentId(userId);
        path.setCourseId(courseId);
        path.setCreatedBy(userId);
        path.setName(plan.getName() != null && !plan.getName().isBlank()
                ? plan.getName() : "AI 定制学习路径");
        path.setDescription(plan.getDescription());
        path.setStatus(PathStatus.DRAFT);
        path.setAdaptStrategy("REORDER");
        path.setSource("AI");
        path = learningPathRepository.save(path);
        final LearningPath savedPath = path;

        List<LearningPathNode> pathNodes = new ArrayList<>();
        int orderIndex = 0;
        for (AiPathPlanResult.AiPathNode aiNode : validNodes) {
            LearningPathNode node = new LearningPathNode();
            node.setLearningPath(savedPath);
            node.setKnowledgePointId(aiNode.getKnowledgePointId());
            node.setKnowledgePointName(findKpNameById(aiNode.getKnowledgePointId()));
            node.setOrderIndex(orderIndex++);
            node.setStatus(PathNodeStatus.PENDING);
            node.setIsRecommended(true);
            node.setEstimatedMinutes(aiNode.getEstimatedMinutes() != null
                    ? aiNode.getEstimatedMinutes() : estimateMinutesByDifficulty(findKpDifficulty(aiNode.getKnowledgePointId())));
            node.setAiReason(aiNode.getReason());
            pathNodes.add(node);
        }
        List<LearningPathNode> savedNodes = learningPathNodeRepository.saveAll(pathNodes);
        savedPath.setTotalNodes(savedNodes.size());
        savedPath.setCompletedNodes(0);
        savedPath.setProgress(0);
        savedPath.getNodes().addAll(savedNodes);
        learningPathRepository.save(savedPath);

        // 5. 保存助手消息（路径概要，供后续对话参考）
        String summary = buildPathSummary(savedPath, savedNodes);
        saveMessage(userId, courseId, sessionId, ChatRole.ASSISTANT, MessageType.ANSWER, summary, null);

        AiPlanResponse result = new AiPlanResponse();
        result.setSessionId(sessionId);
        result.setReply("已为你生成学习路径「" + savedPath.getName() + "」，共 " + savedNodes.size()
                + " 个知识点节点。你可以在下方预览并调整。");
        result.setPath(LearningPathDto.fromEntity(savedPath));
        log.info("AI 路径已落库: pathId={}, sessionId={}, 节点={}", savedPath.getId(), sessionId, savedNodes.size());
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  候选池 / 提示词
    // ══════════════════════════════════════════════════════════════

    /**
     * RAG 检索（courseId 为空时跨全部课程知识库）。
     */
    private List<RAGEngine.DocumentChunk> retrieve(String question, UUID courseId, int topK) {
        if (ragEngine == null || !ragEngine.isEnabled()) {
            log.warn("RAG 引擎不可用，跳过检索");
            return Collections.emptyList();
        }
        try {
            if (courseId != null) {
                return ragEngine.retrieveByCourse(question, courseId.toString(), topK);
            }
            return ragEngine.retrieve(question, topK);
        } catch (Exception e) {
            log.warn("RAG 检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 RAG 结果提取候选知识点；RAG 无结果时回退到课程全部 LEAF 知识点。
     */
    private List<PathTemplateNodeDto> extractCandidates(List<RAGEngine.DocumentChunk> docs, UUID courseId) {
        Set<UUID> kpIds = new LinkedHashMap<UUID, Boolean>().keySet();
        Set<UUID> seen = new HashSet<>();
        List<PathTemplateNodeDto> result = new ArrayList<>();
        for (RAGEngine.DocumentChunk doc : docs) {
            if (doc.getKnowledgePointId() != null) {
                UUID kpId;
                try {
                    kpId = UUID.fromString(doc.getKnowledgePointId());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (seen.add(kpId)) {
                    String name = findKpNameById(kpId);
                    if (name != null) {
                        result.add(PathTemplateNodeDto.of(kpId, name, result.size(),
                                estimateMinutesByDifficulty(findKpDifficulty(kpId))));
                    }
                }
            }
        }
        // RAG 无结果时回退：课程全部 LEAF 知识点
        if (result.isEmpty() && courseId != null) {
            return findLeafKnowledgePoints(courseId);
        }
        return result;
    }

    /**
     * 查询课程下全部 LEAF 知识点（RAG 回退候选池）。
     */
    @SuppressWarnings("unchecked")
    private List<PathTemplateNodeDto> findLeafKnowledgePoints(UUID courseId) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT kp.id, kp.name, kp.difficulty FROM KnowledgePoint kp " +
                "WHERE kp.courseId = :courseId AND kp.type = 'LEAF' ORDER BY kp.orderIndex ASC",
                Object[].class);
        query.setParameter("courseId", courseId);
        List<PathTemplateNodeDto> result = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            result.add(PathTemplateNodeDto.of(
                    (UUID) row[0], (String) row[1], result.size(),
                    estimateMinutesByDifficulty(((Number) row[2]).intValue())));
        }
        return result;
    }

    /**
     * 构建候选知识点列表描述（供 LLM 选择）。
     */
    private String buildCandidateDescription(List<PathTemplateNodeDto> candidates) {
        StringBuilder sb = new StringBuilder();
        for (PathTemplateNodeDto c : candidates) {
            sb.append("- ").append(c.getKnowledgePointId()).append(" | ")
              .append(c.getKnowledgePointName()).append(" | 预计 ")
              .append(c.getEstimatedMinutes()).append(" 分钟\n");
        }
        return sb.toString();
    }

    /**
     * 普通对话系统提示词（含候选知识点上下文）。
     */
    private String buildChatSystemPrompt(List<PathTemplateNodeDto> candidates, UUID courseId, boolean isChat) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名教育路径规划助手，帮助学生规划个性化学习路径。");
        sb.append("你可以通过提问澄清学生的学习目标（如：目标考试、每日可用时间、偏好的主题方向）。");
        if (isChat) {
            sb.append("如果学生提出调整需求（如「更侧重实务」「去掉太难的点」），应结合上下文给出新的建议，");
            sb.append("并在最后告诉学生：确认满意后点击「生成路径」按钮，我将输出结构化路径方案。");
        }
        if (candidates != null && !candidates.isEmpty()) {
            sb.append("\n\n当前课程相关的候选知识点：\n");
            sb.append(buildCandidateDescription(candidates));
            sb.append("\n你可以参考这些知识点名称进行规划建议。");
        }
        return sb.toString();
    }

    /**
     * 结构化输出系统提示词（低温度 + 强约束）。
     */
    private String buildStructuredSystemPrompt() {
        return "你是一名教育路径规划专家。你的任务是根据学生的需求，从给定的候选知识点中挑选最相关的知识点，"
                + "生成一份结构化的学习路径方案。\n"
                + "严格要求：\n"
                + "1. 只允许使用候选知识点列表中出现的 knowledgePointId，不得编造或虚构任何 ID；\n"
                + "2. 每个节点的 JSON 必须包含三个字段：knowledgePointId（候选列表中的 UUID 字符串）、"
                + "estimatedMinutes（整数）、reason（30 字以内的选择理由），禁止输出节点名称等其他字段；\n"
                + "3. 按学习逻辑排序（先基础后进阶、先前置后后续）；\n"
                + "4. 节点数量控制在 5~15 个；\n"
                + "5. 只输出合法 JSON 对象（顶层含 name/description/estimatedMinutes/nodes 四个字段），不包含任何额外说明。";
    }

    /**
     * 生成路径概要（存为助手消息，供后续对话参考）。
     */
    private String buildPathSummary(LearningPath path, List<LearningPathNode> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("已生成路径「").append(path.getName()).append("」，共 ").append(nodes.size()).append(" 个节点：\n");
        for (int i = 0; i < nodes.size(); i++) {
            LearningPathNode n = nodes.get(i);
            sb.append(i + 1).append(". ").append(n.getKnowledgePointName());
            if (n.getAiReason() != null && !n.getAiReason().isBlank()) {
                sb.append("（").append(n.getAiReason()).append("）");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    //  会话持久化（chat_history）
    // ══════════════════════════════════════════════════════════════

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void saveMessage(UUID userId, UUID courseId, String sessionId, ChatRole role,
                             MessageType messageType, String content, Integer tokenCount) {
        ChatHistory message = new ChatHistory();
        message.setUserId(userId);
        message.setCourseId(courseId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        chatHistoryRepository.save(message);
    }

    /**
     * 读取会话历史（最近 N 轮，正序）。
     */
    private List<ChatMessage> buildHistoryMessages(String sessionId) {
        List<ChatHistory> recent = chatHistoryRepository.findBySessionIdOrderByCreatedAtDesc(
                sessionId, PageRequest.of(0, MAX_HISTORY_ROUNDS * 2));
        if (recent.isEmpty()) {
            return new ArrayList<>();
        }
        Collections.reverse(recent);
        List<ChatMessage> messages = new ArrayList<>();
        for (ChatHistory msg : recent) {
            if (ChatRole.USER == msg.getRole()) {
                messages.add(ChatMessage.userMessage(msg.getContent()));
            } else if (ChatRole.ASSISTANT == msg.getRole()) {
                messages.add(ChatMessage.assistantMessage(msg.getContent()));
            }
        }
        return messages;
    }

    // ══════════════════════════════════════════════════════════════
    //  辅助
    // ══════════════════════════════════════════════════════════════

    /**
     * 根据知识点 ID 查询名称（跨模块）。
     */
    private String findKpNameById(UUID kpId) {
        TypedQuery<String> query = entityManager.createQuery(
                "SELECT kp.name FROM KnowledgePoint kp WHERE kp.id = :kpId", String.class);
        query.setParameter("kpId", kpId);
        return query.getResultStream().findFirst().orElse(null);
    }

    /**
     * 根据知识点 ID 查询难度（跨模块）。
     */
    private int findKpDifficulty(UUID kpId) {
        TypedQuery<Integer> query = entityManager.createQuery(
                "SELECT kp.difficulty FROM KnowledgePoint kp WHERE kp.id = :kpId", Integer.class);
        query.setParameter("kpId", kpId);
        return query.getResultStream().findFirst().orElse(3);
    }

    /**
     * 根据难度估算学习时长（分钟）。
     */
    private int estimateMinutesByDifficulty(int difficulty) {
        return 30 + (difficulty - 1) * 15;
    }
}
