package com.edumentor.tutoring.service;

import com.edumentor.entity.enums.ChatRole;
import com.edumentor.entity.enums.MessageType;
import com.edumentor.engine.llm.LLMResponse;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.engine.rag.RAGEngine;
import com.edumentor.qa.entity.ChatHistory;
import com.edumentor.qa.repository.ChatHistoryRepository;
import com.edumentor.student.entity.StudentProfile;
import com.edumentor.tutoring.dto.SessionDto;
import com.edumentor.tutoring.dto.TutoringRequest;
import com.edumentor.tutoring.dto.TutoringResponse;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 智能答疑辅导服务 — RAG 增强版。
 *
 * <p>提供深度智能答疑辅导能力，使用 RAG（检索增强生成）技术
 * 为 LLM 提供精准的上下文，使回答更有依据、更准确、可溯源。</p>
 *
 * <h3>答疑分级策略 (L1~L5)</h3>
 * <table>
 *   <tr><th>级别</th><th>名称</th><th>应用场景</th></tr>
 *   <tr><td>L1</td><td>提示引导</td><td>简单概念混淆时，提示关键词和相关章节</td></tr>
 *   <tr><td>L2</td><td>分步引导</td><td>中等难度问题，分步引导+类比案例</td></tr>
 *   <tr><td>L3</td><td>结构化讲解</td><td>复杂概念不理解，结构化讲解+图示</td></tr>
 *   <tr><td>L4</td><td>完整示范</td><td>综合应用困难，完整解题示范+变式</td></tr>
 *   <tr><td>L5</td><td>拓展延伸</td><td>学有余力，跨学科关联+前沿延伸</td></tr>
 * </table>
 *
 * @author EduMentor Team
 */
@Service
public class TutoringService {

    private static final Logger log = LoggerFactory.getLogger(TutoringService.class);

    /** 答疑分级策略 */
    public static final Map<String, Map<String, String>> TUTORING_LEVELS = Map.of(
        "L1", Map.of("name", "提示引导", "description", "简单概念混淆时，提示关键词和相关章节"),
        "L2", Map.of("name", "分步引导", "description", "中等难度问题，分步引导+类比案例"),
        "L3", Map.of("name", "结构化讲解", "description", "复杂概念不理解，结构化讲解+图示"),
        "L4", Map.of("name", "完整示范", "description", "综合应用困难，完整解题示范+变式"),
        "L5", Map.of("name", "拓展延伸", "description", "学有余力，跨学科关联+前沿延伸")
    );

    /** 辅导专用系统提示词 */
    private static final String TUTORING_SYSTEM_PROMPT = """
        你是一名专业的智能学伴（EduMentor），正在深度辅导一名学生。
        ## 你的角色
        - 你是一名耐心、专业、富有启发性的学科导师
        - 擅长用苏格拉底提问法引导学生思考
        - 基于学生的掌握水平和学习风格调整讲解方式
        ## 学生信息
        {student_info}
        ## 参考资料
        {rag_context}
        ## 行为规则
        1. 始终使用中文回答
        2. 基于参考资料作答，不编造不存在的知识
        3. 使用分步讲解的方式，先给出核心答案再展开
        4. 适当使用追问，引导学生深入思考
        5. 鼓励学生提出后续问题
        6. 根据问题的难度选择适合的辅导级别（L1~L5）
        ## 历史对话
        {history_context}
        """;

    private static final int MAX_HISTORY_ROUNDS = 10;

    private final LLMService llmService;
    private final RAGEngine ragEngine;
    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public TutoringService(LLMService llmService,
                           Optional<RAGEngine> ragEngine,
                           ChatHistoryRepository chatHistoryRepository,
                           UserRepository userRepository,
                           ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.ragEngine = ragEngine.orElse(null);
        this.chatHistoryRepository = chatHistoryRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // ════════════════════════════════════════════
    //  核心 API
    // ════════════════════════════════════════════

    /**
     * 智能答疑（非流式，RAG 增强版）。
     *
     * @param request 提问请求
     * @param userId  用户 ID
     * @return 回答响应
     */
    @Transactional
    public TutoringResponse ask(TutoringRequest request, UUID userId) {
        String sessionId = resolveSessionId(request, userId);
        String question = request.getQuestion();

        // 1. 保存用户消息
        saveChatMessage(userId, null, sessionId, ChatRole.USER, MessageType.QUESTION, question, null);

        // 2. 构建学生画像上下文
        String studentInfo = buildStudentContext(userId, request.getContext());

        // 3. 获取 RAG 上下文
        String ragContext = buildRagContext(question, request);

        // 4. 构建对话历史上下文
        String historyContext = buildHistoryContext(sessionId);

        // 5. 构建完整系统提示词
        String systemPrompt = buildSystemPrompt(studentInfo, ragContext, historyContext);

        // 6. 调用 LLM
        LLMResponse llmResponse = llmService.ask(systemPrompt, question);

        // 7. 保存助手消息
        String metadataJson = buildMetadataJson(llmResponse);
        saveChatMessage(userId, null, sessionId, ChatRole.ASSISTANT, MessageType.ANSWER,
                llmResponse.getContent(), llmResponse.getTokenUsage() != null
                        ? llmResponse.getTokenUsage().getTotalTokens() : null);

        // 8. 构建响应
        TutoringResponse response = new TutoringResponse();
        response.setAnswer(llmResponse.getContent());
        response.setSessionId(sessionId);
        response.setQuestion(question);
        if (llmResponse.getTokenUsage() != null) {
            TutoringResponse.TokenUsage usage = new TutoringResponse.TokenUsage();
            usage.setPromptTokens(llmResponse.getTokenUsage().getPromptTokens());
            usage.setCompletionTokens(llmResponse.getTokenUsage().getCompletionTokens());
            usage.setTotalTokens(llmResponse.getTokenUsage().getTotalTokens());
            response.setTokenUsage(usage);
        }
        response.setModel(llmResponse.getModel());
        response.setTutoringLevel(estimateTutoringLevel(question, request.getContext()));

        // 9. 获取来源信息
        response.setSources(buildSources(question, request));

        log.info("Tutoring completed - session: {}, tokens: {}",
                sessionId, llmResponse.getTokenUsage() != null
                        ? llmResponse.getTokenUsage().getTotalTokens() : 0);
        return response;
    }

    /**
     * 智能答疑（流式 SSE，RAG 增强版）。
     *
     * @param request       提问请求
     * @param userId        用户 ID
     * @param chunkConsumer 流式块消费者
     */
    @Transactional
    public void askStream(TutoringRequest request, UUID userId,
                          Consumer<TutoringStreamEvent> chunkConsumer) {
        String sessionId = resolveSessionId(request, userId);
        String question = request.getQuestion();

        // 1. 保存用户消息
        saveChatMessage(userId, null, sessionId, ChatRole.USER, MessageType.QUESTION, question, null);

        // 2. 构建学生画像上下文
        String studentInfo = buildStudentContext(userId, request.getContext());

        // 3. 获取 RAG 上下文并先发送来源信息
        String ragContext = buildRagContext(question, request);
        if (ragContext != null && !ragContext.isBlank()) {
            List<TutoringResponse.SourceInfo> sources = buildSources(question, request);
            chunkConsumer.accept(new TutoringStreamEvent("rag_context", null, sources, null, sessionId));
        }

        // 4. 构建对话历史上下文
        String historyContext = buildHistoryContext(sessionId);

        // 5. 构建完整系统提示词
        String systemPrompt = buildSystemPrompt(studentInfo, ragContext, historyContext);

        // 6. 流式调用 LLM
        StringBuilder fullAnswer = new StringBuilder();

        llmService.askStream(systemPrompt, question, response -> {
            if (!response.isFinished()) {
                fullAnswer.append(response.getContent());
                chunkConsumer.accept(new TutoringStreamEvent("chunk", response.getContent(), null, null, sessionId));
            }

            if (response.isFinished()) {
                // 保存完整回答
                saveChatMessage(userId, null, sessionId, ChatRole.ASSISTANT, MessageType.ANSWER,
                        fullAnswer.toString(), response.getTokenUsage() != null
                                ? response.getTokenUsage().getTotalTokens() : null);

                // 发送完成事件
                chunkConsumer.accept(new TutoringStreamEvent("done", null, null,
                        Map.of("sessionId", sessionId), sessionId));
            }

            if (response.isError()) {
                chunkConsumer.accept(new TutoringStreamEvent("error", response.getContent(),
                        null, null, sessionId));
            }
        });
    }

    // ════════════════════════════════════════════
    //  对话历史管理
    // ════════════════════════════════════════════

    /**
     * 获取对话历史。
     *
     * @param sessionId 会话 ID
     * @return 消息列表（按时间升序）
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(String sessionId) {
        return chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatHistory::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取会话列表。
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    @Transactional(readOnly = true)
    public List<SessionDto> getSessions(UUID userId) {
        List<String> sessionIds = chatHistoryRepository.findDistinctSessionIdByUserId(
                userId, org.springframework.data.domain.PageRequest.of(0, 50));

        List<SessionDto> sessions = new ArrayList<>();
        for (String sessionId : sessionIds) {
            List<ChatHistory> messages = chatHistoryRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId);
            if (messages.isEmpty()) continue;

            SessionDto dto = new SessionDto();
            dto.setSessionId(sessionId);
            dto.setMessageCount(messages.size());

            // 第一条消息时间作为创建时间
            dto.setCreatedAt(messages.get(0).getCreatedAt());
            // 最后一条消息时间作为更新时间
            dto.setUpdatedAt(messages.get(messages.size() - 1).getCreatedAt());

            // 第一条用户消息作为标题
            messages.stream()
                    .filter(m -> m.getRole() == ChatRole.USER)
                    .findFirst()
                    .ifPresent(firstUserMsg -> {
                        String content = firstUserMsg.getContent();
                        dto.setTitle(content.length() > 50 ? content.substring(0, 50) + "..." : content);
                    });

            if (dto.getTitle() == null) {
                dto.setTitle("新对话");
            }

            sessions.add(dto);
        }

        // 按更新时间降序排列
        sessions.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return sessions;
    }

    /**
     * 删除会话。
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void deleteSession(String sessionId) {
        chatHistoryRepository.deleteBySessionId(sessionId);
        log.info("Tutoring session deleted: {}", sessionId);
    }

    // ════════════════════════════════════════════
    //  内部方法
    // ════════════════════════════════════════════

    /**
     * 解析或创建会话 ID。
     */
    private String resolveSessionId(TutoringRequest request, UUID userId) {
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            return request.getSessionId();
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 保存聊天消息。
     */
    private void saveChatMessage(UUID userId, UUID knowledgePointId, String sessionId,
                                  ChatRole role, MessageType messageType, String content,
                                  Integer tokenCount) {
        ChatHistory message = new ChatHistory();
        message.setUserId(userId);
        message.setKnowledgePointId(knowledgePointId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        chatHistoryRepository.save(message);
    }

    /**
     * 构建学生画像上下文。
     */
    private String buildStudentContext(UUID userId, Map<String, Object> extraContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 年级: 未知\n");
        sb.append("- 学习风格: visual\n");

        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getDisplayName() != null) {
                sb.insert(0, "- 学生姓名: " + user.getDisplayName() + "\n");
            }
        } catch (Exception e) {
            log.warn("无法获取用户信息: {}", e.getMessage());
        }

        if (extraContext != null && !extraContext.isEmpty()) {
            sb.append("- 额外上下文:\n");
            extraContext.forEach((key, value) ->
                    sb.append("  - ").append(key).append(": ").append(value).append("\n"));
        }

        return sb.toString();
    }

    /**
     * 构建 RAG 增强上下文。
     */
    private String buildRagContext(String question, TutoringRequest request) {
        if (ragEngine == null || !ragEngine.isEnabled()) {
            return "";
        }
        try {
            if (request.getKnowledgePointId() != null && !request.getKnowledgePointId().isBlank()) {
                return ragEngine.buildEnhancedContext(question,
                        request.getKnowledgePointId(), 5);
            }
            return ragEngine.buildEnhancedContext(question, 5);
        } catch (Exception e) {
            log.warn("RAG context building failed, continuing without RAG", e);
            return "";
        }
    }

    /**
     * 构建 RAG 来源信息列表。
     */
    private List<TutoringResponse.SourceInfo> buildSources(String question, TutoringRequest request) {
        if (ragEngine == null || !ragEngine.isEnabled()) {
            return Collections.emptyList();
        }
        try {
            List<RAGEngine.SourceInfo> ragSources = ragEngine.retrieveSources(question, 5);
            return ragSources.stream()
                    .map(s -> {
                        TutoringResponse.SourceInfo info = new TutoringResponse.SourceInfo();
                        info.setTitle(s.getTitle());
                        info.setContent(s.getSnippet());
                        info.setScore(s.getScore());
                        info.setSourceType(s.getSourceType());
                        return info;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to retrieve RAG sources", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建对话历史上下文。
     */
    private String buildHistoryContext(String sessionId) {
        List<ChatHistory> messages = chatHistoryRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        if (messages.isEmpty()) {
            return "";
        }

        // 取最近 N 轮对话（排除当前最新用户消息，它已有）
        int startIndex = Math.max(0, messages.size() - 1 - MAX_HISTORY_ROUNDS * 2);
        List<ChatHistory> contextMessages = messages.subList(startIndex, messages.size() - 1);

        StringBuilder sb = new StringBuilder();
        for (ChatHistory msg : contextMessages) {
            String role = msg.getRole() == ChatRole.USER ? "学生" : "学伴";
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建组合的系统提示词。
     */
    private String buildSystemPrompt(String studentInfo, String ragContext, String historyContext) {
        return TUTORING_SYSTEM_PROMPT
                .replace("{student_info}", studentInfo)
                .replace("{rag_context}", ragContext != null && !ragContext.isBlank() ? ragContext : "暂无参考资料")
                .replace("{history_context}", historyContext != null && !historyContext.isBlank()
                        ? historyContext : "暂无历史对话");
    }

    /**
     * 构建元数据 JSON。
     */
    private String buildMetadataJson(LLMResponse llmResponse) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("model", llmResponse.getModel());
            if (llmResponse.getMetadata() != null) {
                metadata.putAll(llmResponse.getMetadata());
            }
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 估算辅导级别（当前为基于问题长度的简单策略）。
     */
    private String estimateTutoringLevel(String question, Map<String, Object> context) {
        if (context != null && context.containsKey("tutoringLevel")) {
            return (String) context.get("tutoringLevel");
        }
        int length = question.length();
        if (length < 20) return "L1";
        if (length < 50) return "L2";
        if (length < 100) return "L3";
        if (length < 200) return "L4";
        return "L5";
    }

    // ════════════════════════════════════════════
    //  流式事件类型
    // ════════════════════════════════════════════

    /**
     * 流式辅导事件 — 用于 SSE 推送。
     */
    public static class TutoringStreamEvent {
        private final String type;
        private final String content;
        private final List<TutoringResponse.SourceInfo> sources;
        private final Map<String, Object> metadata;
        private final String sessionId;

        public TutoringStreamEvent(String type, String content,
                                    List<TutoringResponse.SourceInfo> sources,
                                    Map<String, Object> metadata, String sessionId) {
            this.type = type;
            this.content = content;
            this.sources = sources;
            this.metadata = metadata;
            this.sessionId = sessionId;
        }

        public String getType() { return type; }
        public String getContent() { return content; }
        public List<TutoringResponse.SourceInfo> getSources() { return sources; }
        public Map<String, Object> getMetadata() { return metadata; }
        public String getSessionId() { return sessionId; }
    }
}
