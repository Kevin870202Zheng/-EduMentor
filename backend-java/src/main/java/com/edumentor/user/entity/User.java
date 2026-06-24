package com.edumentor.user.entity;

import com.edumentor.entity.BaseEntity;
import com.edumentor.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_username", columnList = "username", unique = true),
    @Index(name = "idx_users_email", columnList = "email", unique = true),
    @Index(name = "idx_users_role", columnList = "role"),
    @Index(name = "idx_users_active_role", columnList = "is_active, role")
})
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 64)
    private String displayName;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.STUDENT;

    @Column(length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login_at", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime lastLoginAt;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("username", username);
        dto.put("email", email);
        dto.put("displayName", displayName);
        dto.put("avatarUrl", avatarUrl);
        dto.put("role", role != null ? role.name() : null);
        dto.put("phone", phone);
        dto.put("isActive", isActive);
        dto.put("lastLoginAt", lastLoginAt);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
