package com.edumentor.engine.embedding;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识点向量嵌入实体 — 映射 kp_embeddings 表。
 *
 * <p>
 * 存储知识点内容和习题的向量嵌入，用于 RAG 相似度检索。
 * embedding 以文本形式存储（JSON 数组字符串），实际向量检索使用 PGVector 的 vector 类型。
 * </p>
 *
 * @author EduMentor Team
 */
@Getter
@Setter
@Entity
@Table(name = "kp_embeddings", indexes = {
    @Index(name = "idx_kpe_course_id", columnList = "course_id"),
    @Index(name = "idx_kpe_course_code", columnList = "course_code"),
    @Index(name = "idx_kpe_content_type", columnList = "content_type"),
    @Index(name = "idx_kpe_kp_id", columnList = "kp_id")
})
public class KpEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kp_id")
    private UUID kpId;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    private String chunkText;

    /** 向量嵌入（JSON 数组文本，如 "[0.001, 0.002, ...]"） */
    @Column(columnDefinition = "text")
    private String embedding;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "course_code", nullable = false, length = 32)
    private String courseCode;

    @Column(columnDefinition = "jsonb", insertable = false, updatable = false)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
