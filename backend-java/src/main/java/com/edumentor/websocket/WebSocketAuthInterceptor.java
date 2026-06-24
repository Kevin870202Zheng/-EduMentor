package com.edumentor.websocket;

import com.edumentor.config.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket 认证拦截器 — 从查询参数 "token" 中提取 JWT 进行认证。
 * <p>
 * 握手阶段验证 JWT 有效性，从 token 中提取 userId 和 role，
 * 存入 WebSocket session 的 attributes 中，供后续业务处理使用。
 * </p>
 *
 * @author EduMentor Team
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    /** Session attribute 键名 */
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_TOKEN = "token";

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 从查询参数中提取 token
        String query = request.getURI().getQuery();
        if (query == null || query.isEmpty()) {
            log.warn("WebSocket handshake rejected: no query parameters");
            return false;
        }

        String token = extractTokenFromQuery(query);
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket handshake rejected: no token in query params");
            return false;
        }

        // 验证 JWT
        try {
            Claims claims = jwtTokenProvider.validateToken(token);
            if (claims == null) {
                log.warn("WebSocket handshake rejected: invalid token");
                return false;
            }

            String userIdStr = claims.getSubject();
            String role = claims.get("role", String.class);

            if (userIdStr == null || role == null) {
                log.warn("WebSocket handshake rejected: missing userId or role in token");
                return false;
            }

            UUID userId = UUID.fromString(userIdStr);

            // 将用户信息存入 session attributes
            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_ROLE, role);
            attributes.put(ATTR_TOKEN, token);

            log.debug("WebSocket handshake success: userId={}, role={}", userId, role);
            return true;

        } catch (JwtException e) {
            log.warn("WebSocket handshake rejected: JWT validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("WebSocket handshake error: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外操作
    }

    /**
     * 从查询字符串中提取 token 参数的值。
     * 格式: token=xxx&other=yyy
     */
    private String extractTokenFromQuery(String query) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0 && "token".equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }
}
