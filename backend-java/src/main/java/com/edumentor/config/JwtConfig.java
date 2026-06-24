package com.edumentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    private String secretKey;
    private long accessTokenExpiration = 1800;
    private long refreshTokenExpiration = 604800;
    private String issuer = "edumentor";
}
