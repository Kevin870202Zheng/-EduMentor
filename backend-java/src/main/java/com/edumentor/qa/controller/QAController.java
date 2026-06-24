package com.edumentor.qa.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.engine.llm.LLMResponse;
import com.edumentor.qa.dto.ChatHistoryDto;
import com.edumentor.qa.dto.ChatRequest;
import com.edumentor.qa.dto.ChatResponse;
import com.edumentor.qa.service.QAService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 智能答疑 REST Controller。
 *
 * <p>提供基于 LLM 的智能问答和对话历史管理功能。
 * 支持同步问答和 SSE 流式问答两种模式。</p>
 *
 * <h3>端点列表</h3>
 * <ul>
 *   <li>{@code POST /api/qa/ask} — 同步问答</li>
 *   <li>{@code GET /api/qa/ask/stream} — SSE 流式问答</li>
 *   <li>{@code GET /api/qa/history} — 获取指定会话的历史</li>
 *   <li>{@code GET /api/qa/sessions} — 获取用户会话列表</li>
 *   <li>{@code DELETE /api/qa/sessions/{sessionId}} — 删除会话</li>
 *   <li>{@code GET /api/qa/recent} — 获取最近消息</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/qa")
public class QAController {

    private static final Logger log = LoggerFactory.getLogger(QAController.class);

    /** SSE 超时时间（5 分钟） */
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final QAService qaService;

    public QAController(QAService qaService) {
        this.qaService = qaService;
    }

    /**
     * 同步问答 — 发送问题并获取完整回答。
     *
     * @param request 提问请求体
     * @param auth    当前认证用户
     * @return 包含回答内容和会话信息的响应
     */
    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ApiResponse<ChatResponse> ask(@Valid @RequestBody ChatRequest request,
                                         Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("QA ask - user: {}, session: {}", userId, request.getSessionId());

        ChatResponse response = qaService.ask(request, userId);
        return ApiResponse.success(response, "回答生成成功");
    }

    /**
     * SSE 流式问答 — 通过 Server-Sent Events 逐块返回生成内容。
     *
     * <h3>事件格式</h3>
     * <pre>{@code
     * event: chunk
     * data: {"content":"回答片段"}
     *
     * event: done
     * data: {"sessionId":"xxx","totalTokens":123}
     *
     * event: error
     * data: {"message":"错误描述"}
     * }</pre>
     *
     * @param request 提问请求（字段作为查询参数）
     * @param auth    当前认证用户
     * @return SseEmitter 流式响应
     */
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public SseEmitter streamAsk(@Valid ChatRequest request, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("QA stream ask - user: {}, session: {}", userId, request.getSessionId());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        qaService.streamAsk(request, userId, (LLMResponse response) -> {
            try {
                if (response.isFinished()) {
                    int totalTokens = response.getTokenUsage() != null
                            ? response.getTokenUsage().getTotalTokens() : 0;
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("{\"sessionId\":\"" + request.getSessionId()
                                    + "\",\"totalTokens\":" + totalTokens + "}"));
                    emitter.complete();
                } else if (response.getContent() != null && !response.getContent().isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data("{\"content\":\"" + escapeJson(response.getContent()) + "\"}"));
                }
            } catch (IOException e) {
                log.warn("SSE send failed (client disconnected)", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.debug("SSE completed for user: {}", userId));
        emitter.onTimeout(() -> log.warn("SSE timeout for user: {}", userId));

        return emitter;
    }

    /**
     * 获取指定会话的对话历史。
     *
     * @param sessionId 会话 ID
     * @param auth      当前认证用户
     * @return 对话历史消息列表（按时间升序）
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ApiResponse<List<ChatHistoryDto>> getHistory(
            @RequestParam String sessionId, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("QA getHistory - user: {}, session: {}", userId, sessionId);

        List<ChatHistoryDto> history = qaService.getHistory(sessionId);
        return ApiResponse.success(history);
    }

    /**
     * 获取当前用户的所有会话列表。
     *
     * @param auth 当前认证用户
     * @return 会话 ID 列表
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ApiResponse<List<String>> getSessions(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        List<String> sessions = qaService.getSessions(userId);
        return ApiResponse.success(sessions);
    }

    /**
     * 删除指定会话。
     *
     * @param sessionId 要删除的会话 ID
     * @param auth      当前认证用户
     * @return 操作结果
     */
    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        qaService.deleteSession(sessionId);
        log.info("Deleted session: {} by user: {}", sessionId, userId);
        return ApiResponse.success(null, "会话已删除");
    }

    /**
     * 获取最近对话消息（用于快速恢复上下文）。
     *
     * @param limit 限制条数（默认 5，最大 50）
     * @param auth  当前认证用户
     * @return 最近消息列表（按时间升序）
     */
    @GetMapping("/recent")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ApiResponse<List<ChatHistoryDto>> getRecentMessages(
            @RequestParam(defaultValue = "5") int limit, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        List<ChatHistoryDto> messages = qaService.getRecentMessages(userId, Math.min(limit, 50));
        return ApiResponse.success(messages);
    }

    // ──── 私有辅助方法 ────

    /**
     * 从 Authentication 中提取当前用户 ID。
     *
     * <p>支持 principal 为 UUID 对象或 {@code "userId:role"} / {@code "userId"} 格式字符串。</p>
     */
    private UUID getCurrentUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new com.edumentor.common.exception.UnauthorizedException("未认证的用户");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        if (principal instanceof String) {
            String principalStr = (String) principal;
            // 格式: "userId:role" 或 "userId"
            String userIdStr = principalStr.contains(":")
                    ? principalStr.split(":")[0]
                    : principalStr;
            try {
                return UUID.fromString(userIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("Cannot parse user ID from principal: {}", principalStr);
                throw new com.edumentor.common.exception.UnauthorizedException("无效的用户标识");
            }
        }
        throw new com.edumentor.common.exception.UnauthorizedException("无法识别的认证信息");
    }

    /**
     * 转义 JSON 字符串中的特殊字符（用于 SSE data 字段）。
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
