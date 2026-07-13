package com.edumentor.qa.service;

import com.edumentor.entity.enums.ChatRole;
import com.edumentor.entity.enums.MessageType;
import com.edumentor.engine.llm.ChatMessage;
import com.edumentor.engine.llm.LLMResponse;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.engine.rag.RAGEngine;
import com.edumentor.qa.dto.ChatHistoryDto;
import com.edumentor.qa.dto.ChatRequest;
import com.edumentor.qa.dto.ChatResponse;
import com.edumentor.qa.dto.ChatResponse.SourceInfo;
import com.edumentor.qa.dto.ChatResponse.TokenUsage;
import com.edumentor.qa.entity.ChatHistory;
import com.edumentor.qa.repository.ChatHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 智能答疑服务 — 基于 LLM + RAG 的问答核心逻辑。
 *
 * <p>核心职责：
 * <ul>
 *   <li>整合 LLM 和 RAG 引擎，提供上下文增强的智能问答</li>
 *   <li>管理对话历史（创建/查询/删除会话）</li>
 *   <li>支持同步回答和流式回答两种模式</li>
 *   <li>追踪 Token 用量并持久化到对话历史</li>
 * </ul>
 * </p>
 */
@Service
public class QAService {

    private static final Logger log = LoggerFactory.getLogger(QAService.class);

    private static final String SYSTEM_PROMPT = """
        你是一名专业的智能学伴（EduMentor），正在辅导一名学生。
        ## 你的角色
        - 你是一名耐心、专业、富有启发性的学科导师
        - 擅长用 Socratic 提问法引导学生思考
        ## 行为规则
        1. 始终使用中文回答
        2. 基于提供的参考资料作答，不编造不存在的知识
        3. 如果参考资料不足以回答，诚实地告知学生
        4. 使用分步讲解的方式，先给出核心答案再展开
        5. 适当使用追问，引导学生深入思考
        6. 鼓励学生提出后续问题
        ## 输出格式
        - 使用 Markdown 格式增强可读性
        - 代码示例使用代码块展示
        - 关键概念使用**粗体**强调
        ## 参考资料
        {rag_context}
        """;

    private static final String SYSTEM_PROMPT_NO_RAG = """
        你是一名专业的智能学伴（EduMentor），正在辅导一名学生。
        ## 你的角色
        - 耐心、专业、富有启发性的学科导师
        - 擅长用 Socratic 提问法引导学生思考
        ## 行为规则
        1. 始终使用中文回答
        2. 使用分步讲解的方式
        3. 鼓励学生提出后续问题
        4. 使用 Markdown 格式增强可读性
        请回答学生的以下问题：
        """;

    private static final int MAX_HISTORY_ROUNDS = 10;

    private final LLMService llmService;
    private final RAGEngine ragEngine;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ObjectMapper objectMapper;

    public QAService(LLMService llmService,
                     Optional<RAGEngine> ragEngine,
                     ChatHistoryRepository chatHistoryRepository,
                     ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.ragEngine = ragEngine.orElse(null);
        this.chatHistoryRepository = chatHistoryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步问答 — 发送问题并获取完整回答。
     *
     * @param request 提问请求
     * @param userId  当前用户 ID
     * @return 回答响应（含 Token 用量和来源）
     */
    public ChatResponse ask(ChatRequest request, UUID userId) {
        String sessionId = resolveSessionId(request, userId);
        String question = request.getQuestion();
        UUID courseId = request.getCourseId();

        // 1. 保存用户消息
        saveMessage(userId, courseId, sessionId, ChatRole.USER, resolveMessageType(request), question, null, null);

        // 2. 一次 RAG 检索，结果同时用于构建上下文和来源信息
        List<RAGEngine.DocumentChunk> docs = retrieveDocuments(question, request, 5);
        String ragContext = formatRagContext(docs);
        List<SourceInfo> sources = formatSources(docs, 3);

        // 3. 构建只含 RAG 的系统提示词（历史作为独立 messages 传入）
        String systemPrompt = buildSystemPrompt(ragContext);

        // 4. 构建对话历史消息列表（包含当前提问）
        List<ChatMessage> messages = buildHistoryMessages(sessionId);
        messages.add(ChatMessage.userMessage(question));

        // 5. 调用 LLM（多轮对话）
        LLMResponse llmResponse = llmService.chat(systemPrompt, messages);

        // 6. 保存助手消息
        String metadataJson = buildMetadataJson(llmResponse, sources);
        saveMessage(userId, courseId, sessionId, ChatRole.ASSISTANT, MessageType.ANSWER,
                llmResponse.getContent(), extractTotalTokens(llmResponse), metadataJson);

        // 7. 构建响应
        ChatResponse response = new ChatResponse();
        response.setAnswer(llmResponse.getContent());
        response.setSessionId(sessionId);
        if (llmResponse.getTokenUsage() != null) {
            TokenUsage usage = new TokenUsage();
            usage.setPromptTokens(llmResponse.getTokenUsage().getPromptTokens());
            usage.setCompletionTokens(llmResponse.getTokenUsage().getCompletionTokens());
            usage.setTotalTokens(llmResponse.getTokenUsage().getTotalTokens());
            response.setTokenUsage(usage);
        }
        response.setSources(sources);

        log.info("QA completed - session: {}, tokens: {}",
                sessionId, extractTotalTokens(llmResponse));
        return response;
    }

    /**
     * 流式问答 — 通过回调逐块返回生成内容。
     *
     * @param request      提问请求
     * @param userId       当前用户 ID
     * @param chunkConsumer 流式内容块消费者（接收 {@link LLMResponse}）
     */
    public void streamAsk(ChatRequest request, UUID userId, Consumer<LLMResponse> chunkConsumer) {
        String sessionId = resolveSessionId(request, userId);
        String question = request.getQuestion();
        UUID courseId = request.getCourseId();

        // 1. 保存用户消息
        saveMessage(userId, courseId, sessionId, ChatRole.USER, resolveMessageType(request), question, null, null);

        // 2. 一次 RAG 检索，结果同时用于构建上下文和来源信息
        List<RAGEngine.DocumentChunk> docs = retrieveDocuments(question, request, 5);
        String ragContext = formatRagContext(docs);
        List<SourceInfo> sources = formatSources(docs, 3);

        // 3. 构建只含 RAG 的系统提示词（历史作为独立 messages 传入）
        String systemPrompt = buildSystemPrompt(ragContext);

        // 4. 构建对话历史消息列表（包含当前提问）
        List<ChatMessage> messages = buildHistoryMessages(sessionId);
        messages.add(ChatMessage.userMessage(question));

        // 5. 流式调用 LLM（多轮对话）
        StringBuilder fullAnswer = new StringBuilder();

        llmService.chatStream(systemPrompt, messages, response -> {
            if (!response.isFinished()) {
                fullAnswer.append(response.getContent());
            }
            chunkConsumer.accept(response);

            if (response.isFinished() && response.getTokenUsage() != null) {
                String metadataJson = buildMetadataJson(response, sources);
                saveMessage(userId, courseId, sessionId, ChatRole.ASSISTANT, MessageType.ANSWER,
                        fullAnswer.toString(),
                        response.getTokenUsage().getTotalTokens(),
                        metadataJson);
                log.info("QA stream completed - session: {}, tokens: {}",
                        sessionId, response.getTokenUsage().getTotalTokens());
            }
        });
    }

    /**
     * 获取指定会话的对话历史。
     *
     * @param sessionId 会话 ID
     * @return 对话历史 DTO 列表（按时间升序）
     */
    @Transactional(readOnly = true)
    public List<ChatHistoryDto> getHistory(String sessionId) {
        return chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatHistoryDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定用户的会话 ID 列表。
     *
     * @param userId 用户 ID
     * @return 会话 ID 列表（按最新消息时间降序）
     */
    @Transactional(readOnly = true)
    public List<String> getSessions(UUID userId) {
        return chatHistoryRepository.findDistinctSessionIdByUserId(userId, PageRequest.of(0, 50));
    }

    /**
     * 获取指定用户的最近聊天消息。
     *
     * @param userId 用户 ID
     * @param limit  限制条数
     * @return 最近消息列表（按时间升序）
     */
    @Transactional(readOnly = true)
    public List<ChatHistoryDto> getRecentMessages(UUID userId, int limit) {
        List<ChatHistory> messages = chatHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
        List<ChatHistoryDto> result = messages.stream()
                .map(ChatHistoryDto::fromEntity)
                .collect(Collectors.toList());
        Collections.reverse(result);
        return result;
    }

    /**
     * 删除指定会话的所有消息。
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void deleteSession(String sessionId) {
        chatHistoryRepository.deleteBySessionId(sessionId);
        log.info("Deleted session: {}", sessionId);
    }

    // ──── 私有方法 ────

    /**
     * 解析或创建会话 ID。如果请求中未提供，则生成一个新的短 ID。
     */
    private String resolveSessionId(ChatRequest request, UUID userId) {
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            return request.getSessionId();
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 保存消息到对话历史。
     */
    private void saveMessage(UUID userId, UUID courseId, String sessionId, ChatRole role,
                             MessageType messageType, String content,
                             Integer tokenCount, String metadata) {
        ChatHistory message = new ChatHistory();
        message.setUserId(userId);
        message.setCourseId(courseId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setMetadata(metadata);
        chatHistoryRepository.save(message);
    }

    /**
     * 根据请求中的 messageType 或默认值解析 MessageType 枚举。
     */
    private MessageType resolveMessageType(ChatRequest request) {
        if (request.getMessageType() != null && !request.getMessageType().isBlank()) {
            try {
                return MessageType.valueOf(request.getMessageType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return MessageType.TEXT;
            }
        }
        return MessageType.TEXT;
    }

    /**
     * 一次 RAG 检索 — 结果同时用于构建上下文和来源信息，避免重复检索。
     *
     * @param question 用户问题
     * @param request  请求参数（含 courseId/knowledgePointId 过滤条件）
     * @param topK     检索数量
     * @return 文档片段列表（按相关性降序）
     */
    private List<RAGEngine.DocumentChunk> retrieveDocuments(String question, ChatRequest request, int topK) {
        if (ragEngine == null || !ragEngine.isEnabled()) {
            return Collections.emptyList();
        }
        try {
            if (request.getCourseId() != null) {
                return ragEngine.retrieveByCourse(question, request.getCourseId().toString(), topK);
            }
            if (request.getKnowledgePointId() != null) {
                return ragEngine.retrieveByKnowledgePoint(question, request.getKnowledgePointId().toString(), topK);
            }
            return ragEngine.retrieve(question, topK);
        } catch (Exception e) {
            log.warn("RAG retrieval failed, continuing without RAG", e);
            return Collections.emptyList();
        }
    }

    /**
     * 将文档片段格式化为 LLM 参考上下文字符串。
     */
    private String formatRagContext(List<RAGEngine.DocumentChunk> docs) {
        if (docs.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            RAGEngine.DocumentChunk doc = docs.get(i);
            sb.append("[").append(i + 1).append("] ");
            if (doc.getTitle() != null && !doc.getTitle().isBlank()) {
                sb.append(doc.getTitle()).append(" — ");
            }
            sb.append(doc.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 从文档片段构建来源信息列表（取前 maxCount 个用于前端展示）。
     */
    private List<SourceInfo> formatSources(List<RAGEngine.DocumentChunk> docs, int maxCount) {
        if (docs.isEmpty()) return Collections.emptyList();
        int count = Math.min(maxCount, docs.size());
        List<SourceInfo> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RAGEngine.DocumentChunk doc = docs.get(i);
            SourceInfo info = new SourceInfo();
            info.setTitle(doc.getTitle());
            info.setContent(doc.getContent());
            info.setScore(doc.getScore());
            info.setSourceType(doc.getSourceType());
            result.add(info);
        }
        return result;
    }

    /**
     * 构建对话历史消息列表（取最近 N 轮对话，作为独立 ChatMessage 返回）。
     * 历史消息作为独立的 user/assistant messages 传入 LLM API，
     * 避免拼入 system prompt 导致的每次调用 prompt 膨胀和 KV cache 无法复用。
     */
    private List<ChatMessage> buildHistoryMessages(String sessionId) {
        int limit = MAX_HISTORY_ROUNDS * 2; // 每轮含 user + assistant 两条消息
        List<ChatHistory> recentMessages = chatHistoryRepository
                .findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, limit));

        if (recentMessages.isEmpty()) {
            return new ArrayList<>();
        }

        // 反转回正序，保证对话历史按时间先后排列
        Collections.reverse(recentMessages);

        List<ChatMessage> messages = new ArrayList<>();
        for (ChatHistory msg : recentMessages) {
            if (ChatRole.USER == msg.getRole()) {
                messages.add(ChatMessage.userMessage(msg.getContent()));
            } else {
                messages.add(ChatMessage.assistantMessage(msg.getContent()));
            }
        }
        return messages;
    }

    /**
     * 构建只含 RAG 上下文的系统提示词（不含对话历史）。
     * 历史消息通过 {@link #buildHistoryMessages} 作为独立 messages 传入 LLM。
     */
    private String buildSystemPrompt(String ragContext) {
        if (ragContext != null && !ragContext.isBlank()) {
            return SYSTEM_PROMPT.replace("{rag_context}", ragContext);
        }
        return SYSTEM_PROMPT_NO_RAG;
    }

    /**
     * 构建元数据 JSON（从 LLMResponse 中提取模型、来源等信息）。
     */
    private String buildMetadataJson(LLMResponse llmResponse, List<SourceInfo> sources) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("model", llmResponse.getModel());
            if (sources != null && !sources.isEmpty()) {
                metadata.put("sources", sources);
            }
            if (llmResponse.getMetadata() != null) {
                metadata.putAll(llmResponse.getMetadata());
            }
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata JSON", e);
            return "{}";
        }
    }

    /**
     * 安全提取总 Token 数。
     */
    private Integer extractTotalTokens(LLMResponse response) {
        return response.getTokenUsage() != null
                ? response.getTokenUsage().getTotalTokens()
                : null;
    }
}
