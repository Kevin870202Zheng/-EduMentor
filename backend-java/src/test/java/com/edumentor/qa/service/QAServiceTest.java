package com.edumentor.qa.service;

import com.edumentor.engine.llm.LLMProvider;
import com.edumentor.engine.llm.LLMResponse;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.engine.llm.TokenUsage;
import com.edumentor.engine.rag.RAGEngine;
import com.edumentor.entity.enums.ChatRole;
import com.edumentor.entity.enums.MessageType;
import com.edumentor.qa.dto.ChatHistoryDto;
import com.edumentor.qa.dto.ChatRequest;
import com.edumentor.qa.dto.ChatResponse;
import com.edumentor.qa.entity.ChatHistory;
import com.edumentor.qa.repository.ChatHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link QAService} 的单元测试。
 * <p>
 * 测试覆盖：同步问答、流式问答、对话历史管理、RAG 上下文增强、
 * 会话管理、多种边界场景（无 RAG、KP 过滤、空历史等）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QAService — 智能答疑服务单元测试")
class QAServiceTest {

    @Mock
    private LLMService llmService;

    @Mock
    private RAGEngine ragEngine;

    @Mock
    private ChatHistoryRepository chatHistoryRepository;

    @InjectMocks
    private QAService qaService;

    @Captor
    private ArgumentCaptor<ChatHistory> chatHistoryCaptor;

    private UUID userId;
    private UUID knowledgePointId;
    private UUID courseId;
    private String sessionId;
    private ObjectMapper objectMapper;
    private TokenUsage mockTokenUsage;
    private LLMResponse mockLlmResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        knowledgePointId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        sessionId = "sess-" + UUID.randomUUID().toString().substring(0, 8);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockTokenUsage = new TokenUsage(50, 100, 150, 0.001);

        mockLlmResponse = LLMResponse.success(
                "这是一个 AI 生成的回答内容，用于测试。",
                mockTokenUsage,
                LLMProvider.MOCK,
                "mock-model",
                500L
        );

        // Manually inject ObjectMapper since constructor uses it
        try {
            java.lang.reflect.Field field = QAService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(qaService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject ObjectMapper", e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  同步问答
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ask() — 同步问答")
    class AskTests {

        @Test
        @DisplayName("基本问答 — 应返回回答结果")
        void askBasic() {
            ChatRequest request = new ChatRequest();
            request.setQuestion("什么是函数？");
            request.setSessionId(sessionId);

            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());
            when(llmService.ask(anyString(), anyString())).thenReturn(mockLlmResponse);

            ChatResponse result = qaService.ask(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getAnswer()).isEqualTo("这是一个 AI 生成的回答内容，用于测试。");
            assertThat(result.getSessionId()).isEqualTo(sessionId);
            assertThat(result.getTokenUsage()).isNotNull();
            assertThat(result.getTokenUsage().getTotalTokens()).isEqualTo(150);

            // Verify message saved to history
            verify(chatHistoryRepository, times(2)).save(any(ChatHistory.class));
        }

        @Test
        @DisplayName("问答 — 无 sessionId 时自动创建")
        void askWithAutoSessionId() {
            ChatRequest request = new ChatRequest();
            request.setQuestion("什么是函数？");

            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(anyString()))
                    .thenReturn(List.of());
            when(llmService.ask(anyString(), anyString())).thenReturn(mockLlmResponse);

            ChatResponse result = qaService.ask(request, userId);

            assertThat(result.getSessionId()).isNotNull();
            assertThat(result.getSessionId()).hasSize(16);
        }

        @Test
        @DisplayName("问答 — 关联知识点时自动触发 RAG 检索")
        void askWithKnowledgePoint() {
            ChatRequest request = new ChatRequest();
            request.setQuestion("一元二次方程的解法");
            request.setSessionId(sessionId);
            request.setKnowledgePointId(knowledgePointId);

            when(ragEngine.isEnabled()).thenReturn(true);
            when(ragEngine.buildEnhancedContext(anyString(), anyString(), eq(5)))
                    .thenReturn("RAG 上下文内容");
            when(ragEngine.retrieveSources(anyString(), eq(3)))
                    .thenReturn(List.of(createMockSourceInfo("资料1", 0.95)));
            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());
            when(llmService.ask(anyString(), anyString())).thenReturn(mockLlmResponse);

            ChatResponse result = qaService.ask(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getSources()).isNotEmpty();
            verify(ragEngine).buildEnhancedContext(anyString(), eq(knowledgePointId.toString()), eq(5));
        }

        @Test
        @DisplayName("问答 — 关联课程时触发 RAG 检索")
        void askWithCourse() {
            ChatRequest request = new ChatRequest();
            request.setQuestion("代数是什么？");
            request.setSessionId(sessionId);
            request.setCourseId(courseId);

            when(ragEngine.isEnabled()).thenReturn(true);
            when(ragEngine.buildEnhancedContext(anyString(), anyString(), eq(5)))
                    .thenReturn("RAG 上下文");
            when(ragEngine.retrieveSources(anyString(), eq(3)))
                    .thenReturn(List.of(createMockSourceInfo("教材", 0.8)));
            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());
            when(llmService.ask(anyString(), anyString())).thenReturn(mockLlmResponse);

            ChatResponse result = qaService.ask(request, userId);

            assertThat(result).isNotNull();
            verify(ragEngine).buildEnhancedContext(anyString(), eq(courseId.toString()), eq(5));
        }

        @Test
        @DisplayName("问答 — RAG 引擎不可用时仍能正常回答")
        void askWithRagDisabled() {
            ChatRequest request = new ChatRequest();
            request.setQuestion("什么是函数？");
            request.setSessionId(sessionId);

            when(ragEngine.isEnabled()).thenReturn(false);
            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());
            when(llmService.ask(anyString(), anyString())).thenReturn(mockLlmResponse);

            ChatResponse result = qaService.ask(request, userId);

            assertThat(result.getAnswer()).isNotBlank();
        }

        @Test
        @DisplayName("问答 — 带对话历史上下文")
        void askWithHistory() {
            ChatRequest request = new ChatRequest();
            request.setQuestion("继续解释？");
            request.setSessionId(sessionId);

            ChatHistory previousMsg = new ChatHistory();
            previousMsg.setUserId(userId);
            previousMsg.setSessionId(sessionId);
            previousMsg.setRole(ChatRole.USER);
            previousMsg.setContent("之前的提问");
            previousMsg.setCreatedAt(LocalDateTime.now().minusHours(1));

            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of(previousMsg));
            when(llmService.ask(anyString(), anyString())).thenReturn(mockLlmResponse);

            ChatResponse result = qaService.ask(request, userId);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("问答 — RAG 检索失败时优雅降级")
        void askWithRagFailure() {
            ChatRequest request = new ChatRequest();
            request.setQuestion("测试问题");
            request.setSessionId(sessionId);
            request.setKnowledgePointId(knowledgePointId);

            when(ragEngine.isEnabled()).thenReturn(true);
            when(ragEngine.buildEnhancedContext(anyString(), anyString(), eq(5)))
                    .thenThrow(new RuntimeException("RAG 服务暂时不可用"));
            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());
            when(llmService.ask(anyString(), anyString())).thenReturn(mockLlmResponse);

            ChatResponse result = qaService.ask(request, userId);

            // Should still return answer even if RAG fails
            assertThat(result.getAnswer()).isEqualTo("这是一个 AI 生成的回答内容，用于测试。");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  流式问答
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("streamAsk() — 流式问答")
    class StreamAskTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("流式问答 — 应逐块接收并最终保存历史")
        void streamAsk() {
            ChatRequest request = new ChatRequest();
            request.setQuestion("解释一下微积分");
            request.setSessionId(sessionId);

            // Mock stream behavior
            doAnswer(invocation -> {
                String systemPrompt = invocation.getArgument(0);
                String userMessage = invocation.getArgument(1);
                Consumer<LLMResponse> consumer = invocation.getArgument(2);

                // Simulate content chunks
                consumer.accept(LLMResponse.streamChunk("微积分是", LLMProvider.MOCK, "mock"));
                consumer.accept(LLMResponse.streamChunk("数学的", LLMProvider.MOCK, "mock"));
                consumer.accept(LLMResponse.streamChunk("重要分支。", LLMProvider.MOCK, "mock"));
                // End event
                consumer.accept(LLMResponse.streamEnd(LLMProvider.MOCK, "mock",
                        new TokenUsage(30, 60, 90, 0.0005), "stop"));

                return null;
            }).when(llmService).askStream(anyString(), anyString(), any(Consumer.class));

            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());
            when(ragEngine.isEnabled()).thenReturn(false);

            List<LLMResponse> receivedChunks = new ArrayList<>();
            qaService.streamAsk(request, userId, receivedChunks::add);

            assertThat(receivedChunks).hasSize(4);
            assertThat(receivedChunks.get(0).getContent()).isEqualTo("微积分是");
            assertThat(receivedChunks.get(3).isFinished()).isTrue();

            // Verify history was saved (user message + assistant message)
            verify(chatHistoryRepository, atLeast(2)).save(any(ChatHistory.class));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  对话历史管理
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("对话历史管理")
    class HistoryManagementTests {

        @Test
        @DisplayName("获取会话历史")
        void getHistory() {
            ChatHistory msg = createHistoryMessage(ChatRole.USER, "你好");
            ChatHistory reply = createHistoryMessage(ChatRole.ASSISTANT, "你好！有什么可以帮助你的？");

            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of(msg, reply));

            List<ChatHistoryDto> result = qaService.getHistory(sessionId);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("获取用户特定会话的历史")
        void getUserHistory() {
            ChatHistory msg = createHistoryMessage(ChatRole.USER, "问题");
            when(chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of(msg));

            List<ChatHistoryDto> result = qaService.getHistory(sessionId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("获取用户的会话列表")
        void getSessions() {
            when(chatHistoryRepository.findDistinctSessionIdByUserId(eq(userId), any(Pageable.class)))
                    .thenReturn(List.of(sessionId, "session2"));

            List<String> result = qaService.getSessions(userId);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("获取最近消息")
        void getRecentMessages() {
            ChatHistory msg1 = createHistoryMessage(ChatRole.USER, "问题1");
            ChatHistory msg2 = createHistoryMessage(ChatRole.ASSISTANT, "回答1");

            when(chatHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                    .thenReturn(List.of(msg2, msg1));

            List<ChatHistoryDto> result = qaService.getRecentMessages(userId, 10);

            // Should be reversed to chronological order
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("删除会话")
        void deleteSession() {
            qaService.deleteSession(sessionId);

            verify(chatHistoryRepository).deleteBySessionId(sessionId);
        }

        }

    // ══════════════════════════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════════════════════════

    private RAGEngine.SourceInfo createMockSourceInfo(String title, double score) {
        RAGEngine.SourceInfo info = new RAGEngine.SourceInfo();
        info.setTitle(title);
        info.setSnippet(title + "的摘要内容");
        info.setScore(score);
        info.setSourceType("textbook");
        return info;
    }

    private ChatHistory createHistoryMessage(ChatRole role, String content) {
        ChatHistory msg = new ChatHistory();
        msg.setId(UUID.randomUUID());
        msg.setUserId(userId);
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setMessageType(MessageType.TEXT);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        return msg;
    }
}
