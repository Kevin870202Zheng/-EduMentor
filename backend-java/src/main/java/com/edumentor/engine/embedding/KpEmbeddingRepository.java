package com.edumentor.engine.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 知识点向量嵌入 Repository。
 *
 * @author EduMentor Team
 */
@Repository
public interface KpEmbeddingRepository extends JpaRepository<KpEmbedding, UUID> {

    List<KpEmbedding> findByCourseId(UUID courseId);

    List<KpEmbedding> findByKpId(UUID kpId);

    void deleteByCourseId(UUID courseId);
}
