package com.edumentor.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        byte[] keyBytes = Decoders.BASE64.decode(
            java.util.Base64.getEncoder().encodeToString(jwtConfig.getSecretKey().getBytes()));
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getAccessTokenExpiration() * 1000);

        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .claim("type", "access")
            .issuer(jwtConfig.getIssuer())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact();
    }

    public String generateRefreshToken(UUID userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getRefreshTokenExpiration() * 1000);

        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .claim("type", "refresh")
            .issuer(jwtConfig.getIssuer())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact();
    }

    public TokenPair generateTokenPair(UUID userId, String role) {
        return new TokenPair(
            generateAccessToken(userId, role),
            generateRefreshToken(userId, role),
            jwtConfig.getAccessTokenExpiration()
        );
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(jwtConfig.getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(validateToken(token).getSubject());
    }

    public String getRoleFromToken(String token) {
        return validateToken(token).get("role", String.class);
    }

    public boolean isAccessToken(String token) {
        return "access".equals(validateToken(token).get("type", String.class));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(validateToken(token).get("type", String.class));
    }

    public long getRemainingValiditySeconds(String token) {
        Date expiration = validateToken(token).getExpiration();
        long diff = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0, diff / 1000);
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}
}
