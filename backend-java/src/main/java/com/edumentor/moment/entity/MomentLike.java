package com.edumentor.moment.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 同学圈点赞（每人每动态一次，UNIQUE 约束防重复）。
 */
@Getter
@Setter
@Entity
@Table(name = "moment_likes", uniqueConstraints = {
    @UniqueConstraint(name = "uq_ml_moment_user", columnNames = {"moment_id", "user_id"})
}, indexes = {
    @Index(name = "idx_ml_moment", columnList = "moment_id")
})
public class MomentLike extends BaseEntity {

    @Column(name = "moment_id", nullable = false)
    private UUID momentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("momentId", momentId);
        dto.put("userId", userId);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
