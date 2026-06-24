package com.edumentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "websocket")
public class WebSocketConfigProperties {
    private boolean enabled = true;
    private String endpoint = "/ws/chat";
    private long heartbeatInterval = 10000;
    private int maxTextMessageSize = 65536;
    private long maxSessionIdleTimeout = 1800000;
    private String allowedOrigins = "http://localhost:5173,http://localhost:3000";
}
