package com.edumentor.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket 会话管理器 — 管理所有活跃的 WebSocket 连接。
 * <p>
 * 使用 ConcurrentHashMap 保证线程安全，支持按用户 ID、角色查询会话。
 * 提供广播、点对点消息发送功能。
 * </p>
 *
 * @author EduMentor Team
 */
@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    /** userId → WebSocketSession 映射（每个用户最多一个连接） */
    private final ConcurrentHashMap<UUID, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    /** sessionId → userId 反向映射 */
    private final ConcurrentHashMap<String, UUID> sessionToUser = new ConcurrentHashMap<>();

    /**
     * 注册会话。
     *
     * @param userId  用户 ID
     * @param session WebSocket 会话
     */
    public void register(UUID userId, WebSocketSession session) {
        // 如果该用户已有旧连接，关闭旧连接
        WebSocketSession oldSession = userSessions.put(userId, session);
        if (oldSession != null && oldSession.isOpen() && !oldSession.getId().equals(session.getId())) {
            closeSessionQuietly(oldSession);
            sessionToUser.remove(oldSession.getId());
            log.info("Replaced old session for userId={}", userId);
        }

        sessionToUser.put(session.getId(), userId);
        log.info("WebSocket session registered: userId={}, sessionId={}, total={}",
                userId, session.getId(), userSessions.size());
    }

    /**
     * 移除会话。
     *
     * @param sessionId 会话 ID
     */
    public void remove(String sessionId) {
        UUID userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            WebSocketSession session = userSessions.get(userId);
            if (session != null && session.getId().equals(sessionId)) {
                userSessions.remove(userId);
            }
            log.info("WebSocket session removed: sessionId={}, userId={}, total={}",
                    sessionId, userId, userSessions.size());
        }
    }

    /**
     * 根据用户 ID 获取会话。
     *
     * @param userId 用户 ID
     * @return WebSocketSession，可能为 null
     */
    public WebSocketSession getSession(UUID userId) {
        return userSessions.get(userId);
    }

    /**
     * 判断用户是否在线。
     *
     * @param userId 用户 ID
     * @return true 如果在线
     */
    public boolean isOnline(UUID userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 获取所有在线用户 ID。
     *
     * @return 在线用户 ID 集合
     */
    public Set<UUID> getOnlineUserIds() {
        return userSessions.keySet().stream()
                .filter(uid -> {
                    WebSocketSession s = userSessions.get(uid);
                    return s != null && s.isOpen();
                })
                .collect(Collectors.toSet());
    }

    /**
     * 获取在线用户数量。
     *
     * @return 在线用户数
     */
    public int getOnlineCount() {
        return (int) userSessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .count();
    }

    /**
     * 向指定用户发送消息。
     *
     * @param userId  目标用户 ID
     * @param message 消息文本
     * @return true 如果发送成功
     */
    public boolean sendToUser(UUID userId, String message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(
                            new org.springframework.web.socket.TextMessage(message));
                }
                return true;
            } catch (IOException e) {
                log.error("Failed to send message to userId={}: {}", userId, e.getMessage());
                remove(session.getId());
            }
        }
        return false;
    }

    /**
     * 向所有在线用户广播消息。
     *
     * @param message 消息文本
     */
    public void broadcast(String message) {
        List<UUID> disconnected = new ArrayList<>();
        for (Map.Entry<UUID, WebSocketSession> entry : userSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(
                                new org.springframework.web.socket.TextMessage(message));
                    }
                } catch (IOException e) {
                    log.error("Broadcast failed for userId={}: {}", entry.getKey(), e.getMessage());
                    disconnected.add(entry.getKey());
                }
            } else {
                disconnected.add(entry.getKey());
            }
        }
        // 清理断开的连接
        for (UUID userId : disconnected) {
            WebSocketSession session = userSessions.remove(userId);
            if (session != null) {
                sessionToUser.remove(session.getId());
            }
        }
    }

    /**
     * 关闭所有会话（应用关闭时调用）。
     */
    public void closeAll() {
        log.info("Closing all WebSocket sessions...");
        for (Map.Entry<UUID, WebSocketSession> entry : userSessions.entrySet()) {
            closeSessionQuietly(entry.getValue());
        }
        userSessions.clear();
        sessionToUser.clear();
        log.info("All WebSocket sessions closed");
    }

    /**
     * 获取当前总的会话统计信息。
     *
     * @return 统计信息映射
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSessions", userSessions.size());
        stats.put("onlineCount", getOnlineCount());
        stats.put("onlineUsers", getOnlineUserIds().size());
        return stats;
    }

    private void closeSessionQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.debug("Error closing session {}: {}", session.getId(), e.getMessage());
        }
    }
}
