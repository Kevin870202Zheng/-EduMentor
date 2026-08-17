package com.edumentor.moment.repository;

import com.edumentor.moment.entity.MomentComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 同学圈评论仓库。
 */
public interface MomentCommentRepository extends JpaRepository<MomentComment, UUID> {

    List<MomentComment> findByMomentIdOrderByCreatedAtAsc(UUID momentId);

    long countByMomentId(UUID momentId);
}
