package com.edumentor.moment.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 同学圈评论。
 */
@Getter
@Setter
@Entity
@Table(name = "moment_comments", indexes = {
    @Index(name = "idx_mc_moment", columnList = "moment_id, created_at")
})
public class MomentComment extends BaseEntity {

    @Column(name = "moment_id", nullable = false)
    private UUID momentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 评论内容（限 200 字） */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("momentId", momentId);
        dto.put("userId", userId);
        dto.put("content", content);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
