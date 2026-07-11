package com.edumentor.qa.repository;

import com.edumentor.qa.entity.ChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 对话历史数据访问层。
 *
 * <p>提供按用户、会话、知识点维度查询聊天历史的能力。
 * 分页查询统一使用 {@link Pageable} 控制返回条数。</p>
 */
@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, UUID> {

    /**
     * 查询指定用户的最近消息（按创建时间降序）。
     *
     * @param userId   用户 ID
     * @param pageable 分页参数（用于 limit）
     * @return 最近消息列表
     */
    List<ChatHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * 查询指定会话的所有消息（按创建时间升序，用于还原对话顺序）。
     *
     * @param sessionId 会话 ID
     * @return 按时间升序排列的消息列表
     */
    List<ChatHistory> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 查询指定会话的消息（按创建时间降序，用于预览最近消息）。
     *
     * @param sessionId 会话 ID
     * @param pageable  分页参数
     * @return 按时间降序排列的消息列表
     */
    List<ChatHistory> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    /**
     * 统计指定用户在某会话中的消息数量。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 消息数量
     */
    long countBySessionIdAndUserId(String sessionId, UUID userId);

    /**
     * 删除指定会话的所有消息。
     *
     * @param sessionId 会话 ID
     */
    void deleteBySessionId(String sessionId);

    /**
     * 查询指定用户在指定课程下的最近消息。
     *
     * @param userId   用户 ID
     * @param courseId 课程 ID
     * @param pageable 分页参数
     * @return 最近消息列表（按创建时间降序）
     */
    List<ChatHistory> findByUserIdAndCourseIdOrderByCreatedAtDesc(UUID userId, UUID courseId, Pageable pageable);

    /**
     * 查询指定用户的会话 ID 列表（按最新消息时间降序排列）。
     *
     * @param userId   用户 ID
     * @param pageable 分页参数
     * @return 会话 ID 列表
     */
    @Query("SELECT ch.sessionId FROM ChatHistory ch WHERE ch.userId = :userId " +
           "GROUP BY ch.sessionId ORDER BY MAX(ch.createdAt) DESC")
    List<String> findDistinctSessionIdByUserId(@Param("userId") UUID userId, Pageable pageable);
}
