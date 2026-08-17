package com.edumentor.moment.repository;

import com.edumentor.moment.entity.Moment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * 同学圈动态仓库。
 */
public interface MomentRepository extends JpaRepository<Moment, UUID> {

    /** 分页查询未删除动态（按时间倒序） */
    @Query("SELECT m FROM Moment m WHERE m.deleted = false ORDER BY m.createdAt DESC")
    List<Moment> findActive(Pageable pageable);

    /** 更新点赞数 */
    @Modifying
    @Query("UPDATE Moment m SET m.likeCount = :count WHERE m.id = :id")
    void updateLikeCount(@Param("id") UUID id, @Param("count") int count);

    /** 更新评论数 */
    @Modifying
    @Query("UPDATE Moment m SET m.commentCount = :count WHERE m.id = :id")
    void updateCommentCount(@Param("id") UUID id, @Param("count") int count);
}
