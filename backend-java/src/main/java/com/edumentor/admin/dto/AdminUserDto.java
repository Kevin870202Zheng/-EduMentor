package com.edumentor.admin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 管理员端用户视图 DTO。
 */
public record AdminUserDto(
        UUID id,
        String username,
        String displayName,
        String email,
        String role,
        boolean isActive,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {}
