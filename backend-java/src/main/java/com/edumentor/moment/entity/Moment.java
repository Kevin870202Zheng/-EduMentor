package com.edumentor.moment.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 同学圈动态。
 * 发布时 AI 对 content 做法律风险检测（aiReview）；不涉及法律问题时为 null。
 * 设计文档: .youcoder/plans/moments-legal-review-design.html (v1.0) §5
 */
@Getter
@Setter
@Entity
@Table(name = "moments", indexes = {
    @Index(name = "idx_moments_author", columnList = "author_id")
})
public class Moment extends BaseEntity {

    /** 作者（users.id） */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** 动态正文（限 500 字） */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 图片 URL 数组（JSONB，如 ["/uploads/moments/xxx.jpg"]） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> images = new ArrayList<>();

    /** AI 法律检测结果（JSONB，LegalReviewResult；不涉及=null） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_review", columnDefinition = "jsonb")
    private String aiReview;

    /** 冗余点赞数 */
    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    /** 冗余评论数 */
    @Column(name = "comment_count", nullable = false)
    private Integer commentCount = 0;

    /** 软删除标记 */
    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted = false;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("authorId", authorId);
        dto.put("content", content);
        dto.put("images", images);
        dto.put("aiReview", aiReview);
        dto.put("likeCount", likeCount);
        dto.put("commentCount", commentCount);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
