package com.edumentor.auth.service;

import com.edumentor.auth.dto.LoginRequest;
import com.edumentor.auth.dto.RefreshTokenRequest;
import com.edumentor.auth.dto.RegisterRequest;
import com.edumentor.auth.dto.TokenResponse;
import com.edumentor.common.exception.DuplicateResourceException;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.UnauthorizedException;
import com.edumentor.config.JwtTokenProvider;
import com.edumentor.entity.enums.UserRole;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("用户名", request.getUsername());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
            && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("邮箱", request.getEmail());
        }

        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.edumentor.common.exception.ValidationException("无效的角色: " + request.getRole());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setRole(role);
        user.setIsActive(true);
        user = userRepository.save(user);

        log.info("用户注册成功: userId={}, username={}, role={}", user.getId(), user.getUsername(), role);
        return buildTokenResponse(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("用户名或密码错误");
        } catch (DisabledException e) {
            throw new UnauthorizedException("账号已被禁用，请联系管理员");
        }

        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("用户", request.getUsername()));

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return buildTokenResponse(user);
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtTokenProvider.validateToken(request.getRefreshToken());
        } catch (Exception e) {
            throw new UnauthorizedException("Refresh Token 无效或已过期");
        }

        if (!jwtTokenProvider.isRefreshToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Token 类型不是 Refresh Token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("用户不存在"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("账号已被禁用");
        }

        log.info("Token 刷新成功: userId={}", userId);
        return buildTokenResponse(user);
    }

    public User getCurrentUser(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("未认证");
        }
        UUID userId = (UUID) authentication.getPrincipal();
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("用户", userId));
    }

    private TokenResponse buildTokenResponse(User user) {
        JwtTokenProvider.TokenPair pair = jwtTokenProvider.generateTokenPair(
            user.getId(), user.getRole().name());
        return TokenResponse.builder()
            .accessToken(pair.accessToken())
            .refreshToken(pair.refreshToken())
            .expiresIn(pair.expiresIn())
            .tokenType("Bearer")
            .build();
    }
}
