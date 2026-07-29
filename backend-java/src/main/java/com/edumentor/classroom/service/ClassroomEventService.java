package com.edumentor.classroom.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 课堂事件推送服务。
 * <p>
 * 通过 WebSocket 向客户端推送课堂事件：
 * - classroom.action: 教学Action（speech/白板/Quiz等）
 * - classroom.scene_change: 场景切换
 * - classroom.agent_start/agent_end: Agent发言开始/结束
 * - classroom.agent_thinking: Agent思考中
 * - classroom.quiz_feedback: Quiz反馈
 * </p>
 */
@Service
public class ClassroomEventService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomEventService.class);

    private final ObjectMapper objectMapper;

    /** WebSocket 推送接口（由 ClassroomWebSocketHandler 设置） */
    private MessageSender messageSender;

    public ClassroomEventService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 设置消息发送器（由 WebSocket Handler 注入）。
     */
    public void setMessageSender(MessageSender sender) {
        this.messageSender = sender;
    }

    // ═══════════════════════════════════════════════════════════════
    //  事件推送方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 推送教学Action事件。
     */
    public void pushAction(UUID studentId, String classroomId, String sceneId,
                           Map<String, Object> actionPayload) {
        pushEvent(studentId, classroomId, "classroom.action", Map.of(
                "classroomId", classroomId,
                "sceneId", sceneId,
                "action", actionPayload
        ));
    }

    /**
     * 推送场景切换事件。
     */
    public void pushSceneChange(UUID studentId, String classroomId, String sceneId,
                                String sceneTitle, int sceneIndex, int totalScenes) {
        pushEvent(studentId, classroomId, "classroom.scene_change", Map.of(
                "classroomId", classroomId,
                "sceneId", sceneId,
                "sceneTitle", sceneTitle,
                "sceneIndex", sceneIndex,
                "totalScenes", totalScenes
        ));
    }

    /**
     * 推送Agent开始发言事件。
     */
    public void pushAgentStart(UUID studentId, String classroomId, String agentRole, String agentName) {
        pushEvent(studentId, classroomId, "classroom.agent_start", Map.of(
                "classroomId", classroomId,
                "agentRole", agentRole,
                "agentName", agentName
        ));
    }

    /**
     * 推送Agent发言结束事件。
     */
    public void pushAgentEnd(UUID studentId, String classroomId, String agentRole) {
        pushEvent(studentId, classroomId, "classroom.agent_end", Map.of(
                "classroomId", classroomId,
                "agentRole", agentRole
        ));
    }

    /**
     * 推送Agent思考中事件。
     */
    public void pushAgentThinking(UUID studentId, String classroomId, String agentRole) {
        pushEvent(studentId, classroomId, "classroom.agent_thinking", Map.of(
                "classroomId", classroomId,
                "agentRole", agentRole
        ));
    }

    /**
     * 推送Quiz反馈事件。
     */
    public void pushQuizFeedback(UUID studentId, String classroomId, String sceneId,
                                 boolean isCorrect, String feedback) {
        pushEvent(studentId, classroomId, "classroom.quiz_feedback", Map.of(
                "classroomId", classroomId,
                "sceneId", sceneId,
                "isCorrect", isCorrect,
                "feedback", feedback
        ));
    }

    /**
     * 推送讨论模式切换事件。
     */
    public void pushLiveMode(UUID studentId, String classroomId, boolean entering) {
        pushEvent(studentId, classroomId, "classroom.live_mode", Map.of(
                "classroomId", classroomId,
                "entering", entering
        ));
    }

    /**
     * 向课堂内的所有学生广播事件。
     */
    public void broadcastToClassroom(String classroomId, String eventType, Map<String, Object> data) {
        // 简化实现：通过 session manager 发送
        String message = buildMessage(eventType, data);
        if (messageSender != null) {
            messageSender.broadcast(message);
        }
    }

    // ── 内部方法 ──

    private void pushEvent(UUID studentId, String classroomId, String eventType, Map<String, Object> data) {
        String message = buildMessage(eventType, data);
        if (messageSender != null && studentId != null) {
            messageSender.sendToUser(studentId, message);
        } else {
            log.debug("MessageSender not set or studentId is null, event={} dropped", eventType);
        }
    }

    private String buildMessage(String eventType, Map<String, Object> data) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", eventType);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to build WebSocket message: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 消息发送器接口（由 WebSocket Handler 实现）。
     */
    public interface MessageSender {
        void sendToUser(UUID userId, String message);
        void broadcast(String message);
    }
}
