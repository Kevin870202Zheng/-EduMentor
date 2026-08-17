package com.edumentor.moment.repository;

import com.edumentor.moment.entity.MomentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 同学圈点赞仓库。
 */
public interface MomentLikeRepository extends JpaRepository<MomentLike, UUID> {

    Optional<MomentLike> findByMomentIdAndUserId(UUID momentId, UUID userId);

    boolean existsByMomentIdAndUserId(UUID momentId, UUID userId);

    long countByMomentId(UUID momentId);
}
