package com.edumentor.websocket;

import com.edumentor.classroom.service.ClassroomEventService;
import com.edumentor.classroom.service.DirectorService;
import com.edumentor.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;

/**
 * 课堂 WebSocket 处理器。
 * <p>
 * 处理课堂播放过程中的实时交互事件：
 * - 流式推送教学动作
 * - 处理学生举手打断
 * - 处理学生消息（讨论模式）
 * - 处理心跳保活
 * </p>
 *
 * 消息协议与服务端推送事件类型：
 * - classroom.action         服务端→客户端：教学动作
 * - classroom.scene_change   服务端→客户端：场景切换
 * - classroom.agent_start    服务端→客户端：Agent开始发言
 * - classroom.agent_end      服务端→客户端：Agent发言结束
 * - classroom.agent_thinking 服务端→客户端：Agent思考中
 * - classroom.quiz_feedback  服务端→客户端：Quiz反馈
 * - classroom.live_mode      服务端→客户端：讨论模式切换
 *
 * 客户端→服务端事件：
 * - classroom.interrupt      学生举手打断
 * - classroom.message        学生发送消息
 * - classroom.resume         请求恢复播放
 * - classroom.heartbeat      心跳保活
 */
@Component
public class ClassroomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ClassroomWebSocketHandler.class);

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final ClassroomEventService eventService;

    public ClassroomWebSocketHandler(WebSocketSessionManager sessionManager,
                                     ObjectMapper objectMapper,
                                     ClassroomEventService eventService) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.eventService = eventService;

        // 注册事件服务的消息发送器
        this.eventService.setMessageSender(new ClassroomEventService.MessageSender() {
            @Override
            public void sendToUser(UUID userId, String message) {
                sessionManager.sendToUser(userId, message);
            }

            @Override
            public void broadcast(String message) {
                sessionManager.broadcast(message);
            }
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = getUserIdFromSession(session);
        if (userId != null) {
            sessionManager.register(userId, session);
            log.info("Classroom WS connected: userId={}, sessionId={}", userId, session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID userId = getUserIdFromSession(session);
        if (userId == null) {
            sendError(session, "未认证用户");
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    message.getPayload(), new TypeReference<Map<String, Object>>() {});
            String type = (String) payload.getOrDefault("type", "");
            Map<String, Object> data = (Map<String, Object>) payload.getOrDefault("data", Map.of());

            switch (type) {
                case "classroom.interrupt" -> handleInterrupt(userId, data);
                case "classroom.message" -> handleMessage(userId, data);
                case "classroom.resume" -> handleResume(userId, data);
                case "classroom.heartbeat" -> handleHeartbeat(userId, data);
                default -> log.warn("Unknown classroom WS message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to handle classroom WS message: {}", e.getMessage());
            sendError(session, "消息格式错误");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = getUserIdFromSession(session);
        if (userId != null) {
            sessionManager.remove(session.getId());
            log.info("Classroom WS disconnected: userId={}, sessionId={}", userId, session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        UUID userId = getUserIdFromSession(session);
        log.error("Classroom WS transport error: userId={}, error={}", userId, exception.getMessage());
        sessionManager.remove(session.getId());
    }

    // ── 事件处理方法 ──

    private void handleInterrupt(UUID userId, Map<String, Object> data) {
        String classroomId = (String) data.getOrDefault("classroomId", "");
        log.info("Student {} interrupted classroom {}", userId, classroomId);

        // 推送讨论模式切换事件
        eventService.pushLiveMode(userId, classroomId, true);
    }

    private void handleMessage(UUID userId, Map<String, Object> data) {
        String classroomId = (String) data.getOrDefault("classroomId", "");
        String content = (String) data.getOrDefault("content", "");
        log.info("Student {} message in classroom {}: {}", userId, classroomId, content);

        // 将消息转发给 DirectorService 处理
        // eventService.pushMessage(userId, classroomId, content);
    }

    private void handleResume(UUID userId, Map<String, Object> data) {
        String classroomId = (String) data.getOrDefault("classroomId", "");
        log.info("Student {} resumed classroom {}", userId, classroomId);

        // 推送恢复播放事件
        eventService.pushLiveMode(userId, classroomId, false);
    }

    private void handleHeartbeat(UUID userId, Map<String, Object> data) {
        // 心跳保活，无需处理
    }

    // ── 内部方法 ──

    private UUID getUserIdFromSession(WebSocketSession session) {
        Object attr = session.getAttributes().get(WebSocketAuthInterceptor.ATTR_USER_ID);
        return attr instanceof UUID ? (UUID) attr : null;
    }

    private void sendError(WebSocketSession session, String error) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "error",
                    "message", error
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.warn("Failed to send error message: {}", e.getMessage());
        }
    }
}
