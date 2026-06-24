package com.edumentor.websocket;

import com.edumentor.config.WebSocketConfigProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 聊天/协作处理器 — 处理实时消息通信。
 * <p>
 * 支持的消息类型 (messageType)：
 * <ul>
 *   <li><b>CHAT_SEND</b> — 发送聊天消息</li>
 *   <li><b>STUDY_SESSION_START</b> — 开始学习会话</li>
 *   <li><b>STUDY_SESSION_END</b> — 结束学习会话</li>
 *   <li><b>STUDY_SESSION_UPDATE</b> — 更新学习会话进度</li>
 *   <li><b>HEARTBEAT</b> — 心跳检测</li>
 *   <li><b>GET_HISTORY</b> — 获取历史消息</li>
 *   <li><b>GET_ONLINE</b> — 获取在线用户列表</li>
 * </ul>
 * </p>
 *
 * @author EduMentor Team
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final WebSocketSessionManager sessionManager;
    private final WebSocketConfigProperties configProperties;
    private final ObjectMapper objectMapper;

    /** 消息历史缓存（最多保留 200 条） */
    private final List<Map<String, Object>> messageHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY = 200;

    /** 消息 ID 生成器 */
    private final AtomicInteger messageIdCounter = new AtomicInteger(0);

    /** 心跳超时（毫秒），默认 30 秒无心跳视为断开 */
    private static final long HEARTBEAT_TIMEOUT_MS = 30000;

    public ChatWebSocketHandler(WebSocketSessionManager sessionManager,
                                WebSocketConfigProperties configProperties,
                                ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.configProperties = configProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = getUserIdFromSession(session);
        if (userId != null) {
            sessionManager.register(userId, session);
            log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());

            // 发送连接成功消息
            sendMessage(session, createResponse("SYSTEM", "连接成功", Map.of(
                    "userId", userId.toString(),
                    "sessionId", session.getId(),
                    "onlineCount", sessionManager.getOnlineCount()
            )));
        } else {
            log.warn("WebSocket connected without userId, closing: sessionId={}", session.getId());
            closeSessionQuietly(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID userId = getUserIdFromSession(session);
        if (userId == null) {
            sendError(session, "未认证的用户");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String messageType = root.has("messageType") ? root.get("messageType").asText() : "";
            String payload = root.has("payload") ? root.get("payload").toString() : "{}";

            log.debug("Received message: type={}, userId={}", messageType, userId);

            switch (messageType.toUpperCase()) {
                case "CHAT_SEND" -> handleChatSend(userId, session, payload);
                case "STUDY_SESSION_START" -> handleStudySessionStart(userId, session, payload);
                case "STUDY_SESSION_END" -> handleStudySessionEnd(userId, session, payload);
                case "STUDY_SESSION_UPDATE" -> handleStudySessionUpdate(userId, session, payload);
                case "HEARTBEAT" -> handleHeartbeat(userId, session);
                case "GET_HISTORY" -> handleGetHistory(userId, session, payload);
                case "GET_ONLINE" -> handleGetOnline(userId, session);
                default -> sendError(session, "未知消息类型: " + messageType);
            }
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON message from userId={}: {}", userId, e.getMessage());
            sendError(session, "消息格式错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error handling message from userId={}: {}", userId, e.getMessage(), e);
            sendError(session, "服务器内部错误");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = getUserIdFromSession(session);
        sessionManager.remove(session.getId());

        if (userId != null) {
            log.info("WebSocket disconnected: userId={}, sessionId={}, status={}",
                    userId, session.getId(), status);

            // 广播用户下线通知
            broadcastToAll(createResponse("SYSTEM", "用户下线", Map.of(
                    "userId", userId.toString(),
                    "onlineCount", sessionManager.getOnlineCount()
            )));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        UUID userId = getUserIdFromSession(session);
        log.error("WebSocket transport error: userId={}, sessionId={}, error={}",
                userId, session.getId(), exception.getMessage());
        sessionManager.remove(session.getId());
        closeSessionQuietly(session);
    }

    // ══════════════════════════════════════════════════════════════
    //  消息处理
    // ══════════════════════════════════════════════════════════════

    private void handleChatSend(UUID userId, WebSocketSession session, String payload) {
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            String content = payloadNode.has("content") ? payloadNode.get("content").asText() : "";
            String targetUserId = payloadNode.has("targetUserId") ? payloadNode.get("targetUserId").asText() : null;

            if (content.isBlank()) {
                sendError(session, "消息内容不能为空");
                return;
            }

            int messageId = messageIdCounter.incrementAndGet();
            String role = getRoleFromSession(session);

            Map<String, Object> chatMessage = new LinkedHashMap<>();
            chatMessage.put("messageId", messageId);
            chatMessage.put("fromUserId", userId.toString());
            chatMessage.put("role", role);
            chatMessage.put("content", content);
            chatMessage.put("timestamp", OffsetDateTime.now().toString());

            // 保存到历史
            addToHistory(chatMessage);

            if (targetUserId != null && !targetUserId.isEmpty()) {
                // 点对点发送
                sessionManager.sendToUser(UUID.fromString(targetUserId),
                        objectMapper.writeValueAsString(createResponse("CHAT", "收到消息", chatMessage)));
            } else {
                // 广播给所有在线用户
                broadcastToAll(createResponse("CHAT", "收到消息", chatMessage));
            }

            log.info("Chat sent: userId={}, messageId={}, target={}", userId, messageId, targetUserId);

        } catch (Exception e) {
            log.error("Error handling CHAT_SEND: {}", e.getMessage(), e);
            sendError(session, "发送消息失败");
        }
    }

    private void handleStudySessionStart(UUID userId, WebSocketSession session, String payload) {
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            String courseId = payloadNode.has("courseId") ? payloadNode.get("courseId").asText() : "";
            String sessionType = payloadNode.has("sessionType") ? payloadNode.get("sessionType").asText() : "study";

            Map<String, Object> sessionData = new LinkedHashMap<>();
            sessionData.put("userId", userId.toString());
            sessionData.put("courseId", courseId);
            sessionData.put("sessionType", sessionType);
            sessionData.put("startTime", OffsetDateTime.now().toString());
            sessionData.put("status", "active");

            // 广播学习状态变更
            broadcastToAll(createResponse("STUDY_SESSION_START", "学习会话开始", sessionData));
            log.info("Study session started: userId={}, courseId={}", userId, courseId);

        } catch (Exception e) {
            log.error("Error handling STUDY_SESSION_START: {}", e.getMessage(), e);
            sendError(session, "开始学习会话失败");
        }
    }

    private void handleStudySessionEnd(UUID userId, WebSocketSession session, String payload) {
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            String courseId = payloadNode.has("courseId") ? payloadNode.get("courseId").asText() : "";
            int durationMinutes = payloadNode.has("durationMinutes") ? payloadNode.get("durationMinutes").asInt() : 0;

            Map<String, Object> sessionData = new LinkedHashMap<>();
            sessionData.put("userId", userId.toString());
            sessionData.put("courseId", courseId);
            sessionData.put("durationMinutes", durationMinutes);
            sessionData.put("endTime", OffsetDateTime.now().toString());
            sessionData.put("status", "completed");

            broadcastToAll(createResponse("STUDY_SESSION_END", "学习会话结束", sessionData));
            log.info("Study session ended: userId={}, duration={}min", userId, durationMinutes);

        } catch (Exception e) {
            log.error("Error handling STUDY_SESSION_END: {}", e.getMessage(), e);
        }
    }

    private void handleStudySessionUpdate(UUID userId, WebSocketSession session, String payload) {
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            int progress = payloadNode.has("progress") ? payloadNode.get("progress").asInt() : 0;

            Map<String, Object> updateData = new LinkedHashMap<>();
            updateData.put("userId", userId.toString());
            updateData.put("progress", progress);

            sendMessage(session, createResponse("STUDY_SESSION_UPDATE", "进度已更新", updateData));

        } catch (Exception e) {
            log.error("Error handling STUDY_SESSION_UPDATE: {}", e.getMessage(), e);
        }
    }

    private void handleHeartbeat(UUID userId, WebSocketSession session) {
        sendMessage(session, createResponse("HEARTBEAT", "pong", Map.of(
                "timestamp", OffsetDateTime.now().toString()
        )));
    }

    private void handleGetHistory(UUID userId, WebSocketSession session, String payload) {
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            int limit = payloadNode.has("limit") ? payloadNode.get("limit").asInt() : 50;

            List<Map<String, Object>> history = messageHistory.size() > limit
                    ? messageHistory.subList(messageHistory.size() - limit, messageHistory.size())
                    : new ArrayList<>(messageHistory);

            sendMessage(session, createResponse("HISTORY", "历史消息", Map.of(
                    "messages", history,
                    "total", messageHistory.size()
            )));

        } catch (Exception e) {
            log.error("Error handling GET_HISTORY: {}", e.getMessage(), e);
        }
    }

    private void handleGetOnline(UUID userId, WebSocketSession session) {
        Set<UUID> onlineUsers = sessionManager.getOnlineUserIds();
        sendMessage(session, createResponse("ONLINE_USERS", "在线用户列表", Map.of(
                "onlineCount", onlineUsers.size(),
                "userIds", onlineUsers.stream().map(UUID::toString).toList()
        )));
    }

    // ══════════════════════════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════════════════════════

    private UUID getUserIdFromSession(WebSocketSession session) {
        Object attr = session.getAttributes().get(WebSocketAuthInterceptor.ATTR_USER_ID);
        return attr instanceof UUID ? (UUID) attr : null;
    }

    private String getRoleFromSession(WebSocketSession session) {
        Object attr = session.getAttributes().get(WebSocketAuthInterceptor.ATTR_ROLE);
        return attr instanceof String ? (String) attr : "STUDENT";
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                }
            }
        } catch (IOException e) {
            log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String errorMsg) {
        sendMessage(session, createResponse("ERROR", errorMsg, null));
    }

    private void broadcastToAll(Map<String, Object> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            sessionManager.broadcast(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize broadcast message: {}", e.getMessage());
        }
    }

    private Map<String, Object> createResponse(String messageType, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", messageType);
        response.put("message", message);
        response.put("timestamp", OffsetDateTime.now().toString());
        if (data != null) {
            response.put("data", data);
        }
        return response;
    }

    private void addToHistory(Map<String, Object> chatMessage) {
        messageHistory.add(chatMessage);
        if (messageHistory.size() > MAX_HISTORY) {
            messageHistory.remove(0);
        }
    }

    private void closeSessionQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.debug("Error closing session: {}", e.getMessage());
        }
    }
}
