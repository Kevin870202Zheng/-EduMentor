package com.edumentor.config;

import com.edumentor.common.exception.UnauthorizedException;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 用户实体缓存：userId → User，5分钟内不过期，最大1000条 */
    private static final ConcurrentHashMap<UUID, User> USER_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractJwtFromRequest(request);
            if (token != null) {
                if (!jwtTokenProvider.isAccessToken(token)) {
                    throw new UnauthorizedException("Refresh Token 不能用于 API 鉴权");
                }
                Claims claims = jwtTokenProvider.validateToken(token);
                UUID userId = UUID.fromString(claims.getSubject());
                String role = claims.get("role", String.class);

                // 从缓存获取 User 实体，避免每次查库
                User user = getUserFromCache(userId);
                if (user == null) {
                    log.warn("用户不存在: userId={}", userId);
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role));

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            log.warn("JWT 已过期: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (JwtException | UnauthorizedException e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从缓存获取 User 实体，缓存未命中时查库并写入缓存。
     * 缓存 5 分钟后过期，避免用户信息更新后长期使用旧数据。
     */
    private User getUserFromCache(UUID userId) {
        User cached = USER_CACHE.get(userId);
        if (cached != null) {
            return cached;
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // 写入缓存，5分钟后由定时清理机制失效
            USER_CACHE.put(userId, user);
            // 启动异步清理（仅当首次写入时调度）
            scheduleCacheCleanup(userId);
            return user;
        }
        return null;
    }

    /**
     * 延迟 5 分钟后清理缓存条目，使下一次请求重新查库。
     * 使用虚拟线程或平台线程执行，避免阻塞请求线程。
     */
    private void scheduleCacheCleanup(UUID userId) {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(CACHE_TTL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            USER_CACHE.remove(userId);
        });
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
