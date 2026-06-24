package com.edumentor.notification.repository;

import com.edumentor.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 通知数据访问层。
 *
 * @author EduMentor Team
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * 查询用户的通知列表（按创建时间降序）。
     *
     * @param userId   用户 ID
     * @param pageable 分页参数
     * @return 通知列表
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * 查询用户的未读通知（按创建时间降序）。
     *
     * @param userId   用户 ID
     * @param pageable 分页参数
     * @return 未读通知列表
     */
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * 统计用户的未读通知数量。
     *
     * @param userId 用户 ID
     * @return 未读通知数
     */
    long countByUserIdAndIsReadFalse(UUID userId);

    /**
     * 标记用户的所有通知为已读。
     *
     * @param userId 用户 ID
     * @return 更新的记录数
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") UUID userId);

    /**
     * 统计用户的通知总数。
     *
     * @param userId 用户 ID
     * @return 通知总数
     */
    long countByUserId(UUID userId);
}
