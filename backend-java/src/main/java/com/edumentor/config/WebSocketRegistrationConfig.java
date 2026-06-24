package com.edumentor.config;

import com.edumentor.websocket.ChatWebSocketHandler;
import com.edumentor.websocket.WebSocketAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 注册配置 — 注册 WebSocket 端点、拦截器。
 * <p>
 * 与 {@link WebSocketConfig}（STOMP）互补，提供原生 WebSocket 支持。
 * 端点在 {@code /ws/chat}，通过 JWT token 查询参数认证。
 * </p>
 *
 * @author EduMentor Team
 */
@Configuration
@EnableWebSocket
public class WebSocketRegistrationConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketRegistrationConfig.class);

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final WebSocketConfigProperties configProperties;

    public WebSocketRegistrationConfig(ChatWebSocketHandler chatWebSocketHandler,
                                       WebSocketAuthInterceptor webSocketAuthInterceptor,
                                       WebSocketConfigProperties configProperties) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.configProperties = configProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String endpoint = configProperties.getEndpoint();

        // 注册主端点（支持 SockJS 回退）
        registry.addHandler(chatWebSocketHandler, endpoint)
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setDisconnectDelay(30_000)        // 断开延迟 30 秒
                .setHeartbeatTime(25_000)           // 心跳 25 秒
                .setSessionCookieNeeded(false);     // 不依赖 session cookie

        // 注册原生 WebSocket 端点（无 SockJS）
        registry.addHandler(chatWebSocketHandler, endpoint + "/raw")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");

        log.info("WebSocket handlers registered: endpoint={}, maxTextSize={}",
                endpoint, configProperties.getMaxTextMessageSize());
        log.info("WebSocket config: heartbeatInterval={}ms, sessionIdleTimeout={}ms",
                configProperties.getHeartbeatInterval(),
                configProperties.getMaxSessionIdleTimeout());
    }
}
