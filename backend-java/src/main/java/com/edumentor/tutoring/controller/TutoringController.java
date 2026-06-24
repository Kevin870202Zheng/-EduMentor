package com.edumentor.tutoring.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.tutoring.dto.SessionDto;
import com.edumentor.tutoring.dto.TutoringRequest;
import com.edumentor.tutoring.dto.TutoringResponse;
import com.edumentor.tutoring.service.TutoringService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 智能辅导 REST API。
 *
 * <p>提供基于 RAG 增强的深度智能答疑辅导能力。</p>
 *
 * @author EduMentor Team
 */
@RestController
@RequestMapping("/api/v1/tutoring")
public class TutoringController {

    private final TutoringService tutoringService;

    public TutoringController(TutoringService tutoringService) {
        this.tutoringService = tutoringService;
    }

    /**
     * 智能答疑（非流式）。
     *
     * @param userId 用户 ID（当前暂从请求参数获取）
     * @param request 提问请求
     * @return 回答响应
     */
    @PostMapping("/ask")
    public ApiResponse<TutoringResponse> ask(
            @RequestParam UUID userId,
            @Valid @RequestBody TutoringRequest request) {
        TutoringResponse response = tutoringService.ask(request, userId);
        return ApiResponse.success(response);
    }

    /**
     * 智能答疑（流式 SSE）。
     *
     * @param userId  用户 ID
     * @param request 提问请求
     * @return 流式 SSE 响应
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter askStream(
            @RequestParam UUID userId,
            @Valid @RequestBody TutoringRequest request) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                tutoringService.askStream(request, userId, event -> {
                    try {
                        Map<String, Object> data = new java.util.LinkedHashMap<>();
                        data.put("type", event.getType());
                        data.put("sessionId", event.getSessionId());
                        if (event.getContent() != null) {
                            data.put("content", event.getContent());
                        }
                        if (event.getSources() != null) {
                            data.put("sources", event.getSources());
                        }
                        if (event.getMetadata() != null) {
                            data.put("metadata", event.getMetadata());
                        }
                        emitter.send(data);
                        if ("done".equals(event.getType()) || "error".equals(event.getType())) {
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取对话历史。
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> getHistory(@RequestParam String sessionId) {
        List<Map<String, Object>> history = tutoringService.getHistory(sessionId);
        return ApiResponse.success(history);
    }

    /**
     * 获取用户的会话列表。
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public ApiResponse<List<SessionDto>> getSessions(@RequestParam UUID userId) {
        List<SessionDto> sessions = tutoringService.getSessions(userId);
        return ApiResponse.success(sessions);
    }

    /**
     * 删除会话。
     *
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        tutoringService.deleteSession(sessionId);
        return ApiResponse.success(null, "会话已删除");
    }

    /**
     * 获取辅导级别列表。
     *
     * @return 辅导级别信息
     */
    @GetMapping("/levels")
    public ApiResponse<Map<String, Map<String, String>>> getLevels() {
        return ApiResponse.success(TutoringService.TUTORING_LEVELS);
    }
}
