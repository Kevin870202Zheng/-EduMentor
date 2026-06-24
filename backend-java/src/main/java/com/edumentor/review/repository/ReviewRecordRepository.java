package com.edumentor.review.repository;

import com.edumentor.review.entity.ReviewRecord;
import com.edumentor.review.entity.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 复习记录 Repository — 提供复习计划数据的持久化操作和统计分析。
 * <p>
 * 支持按学生、复习状态、排程日期、知识点等维度查询复习记录，
 * 并提供每日复习量、知识点掌握统计等分析查询。
 * </p>
 *
 * @author EduMentor Team
 */
@Repository
public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, UUID> {

    // ══════════════════════════════════════════════════════════════
    //  基本查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询某学生的所有复习记录。
     *
     * @param studentId 学生 ID
     * @return 复习记录列表
     */
    List<ReviewRecord> findByStudentId(UUID studentId);

    /**
     * 统计某学生的复习记录总数。
     *
     * @param studentId 学生 ID
     * @return 复习记录总数
     */
    long countByStudentId(UUID studentId);

    /**
     * 按复习状态查询某学生的复习记录。
     *
     * @param studentId 学生 ID
     * @param status    复习状态
     * @return 复习记录列表
     */
    List<ReviewRecord> findByStudentIdAndStatus(UUID studentId, ReviewStatus status);

    /**
     * 查询某学生在指定日期之前（含当日）且处于指定状态的复习记录。
     *
     * @param studentId     学生 ID
     * @param status        复习状态
     * @param scheduledDate 排程截止日期
     * @return 复习记录列表
     */
    List<ReviewRecord> findByStudentIdAndStatusAndScheduledDateLessThanEqual(
            UUID studentId, ReviewStatus status, LocalDate scheduledDate);

    /**
     * 查询某学生在指定日期的复习记录。
     *
     * @param studentId     学生 ID
     * @param scheduledDate 排程日期
     * @return 复习记录列表
     */
    List<ReviewRecord> findByStudentIdAndScheduledDate(UUID studentId, LocalDate scheduledDate);

    /**
     * 查询某学生在指定日期之后的复习记录。
     *
     * @param studentId     学生 ID
     * @param scheduledDate 排程起始日期
     * @return 复习记录列表
     */
    List<ReviewRecord> findByStudentIdAndScheduledDateAfter(UUID studentId, LocalDate scheduledDate);

    /**
     * 查询某学生下次复习日期早于或等于指定日期的记录（即已逾期或今日需复习）。
     *
     * @param studentId      学生 ID
     * @param nextReviewDate 下次复习截止日期
     * @return 复习记录列表
     */
    List<ReviewRecord> findByStudentIdAndNextReviewDateLessThanEqual(
            UUID studentId, LocalDate nextReviewDate);

    /**
     * 查询某学生在某知识点下指定周期的复习记录。
     *
     * @param studentId        学生 ID
     * @param knowledgePointId 知识点 ID
     * @param reviewCycle      复习周期
     * @return 复习记录列表
     */
    List<ReviewRecord> findByStudentIdAndKnowledgePointIdAndReviewCycle(
            UUID studentId, UUID knowledgePointId, Integer reviewCycle);

    /**
     * 统计某学生指定状态的复习记录数量。
     *
     * @param studentId 学生 ID
     * @param status    复习状态
     * @return 复习记录数量
     */
    long countByStudentIdAndStatus(UUID studentId, ReviewStatus status);

    // ══════════════════════════════════════════════════════════════
    //  统计分析查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 统计某学生最近 N 天每日完成的复习数量。
     *
     * @param studentId 学生 ID
     * @param days      回溯天数
     * @return 对象数组列表，每个元素为 [dateStr, completedCount]
     */
    @Query(value = "SELECT TO_CHAR(r.completed_date::date, 'YYYY-MM-DD') AS day, COUNT(*) " +
                   "FROM review_records r WHERE r.student_id = :studentId " +
                   "AND r.status = 'COMPLETED' " +
                   "AND r.completed_date >= CURRENT_DATE - (:days || ' days')::interval " +
                   "GROUP BY day ORDER BY day ASC",
           nativeQuery = true)
    List<Object[]> findDailyReviewCounts(@Param("studentId") UUID studentId,
                                         @Param("days") int days);

    /**
     * 统计各知识点的复习相关数据：总复习次数、平均正确率、最新复习周期。
     *
     * @param studentId 学生 ID
     * @return 对象数组列表，每个元素为 [knowledgePointId, totalReviews, avgAccuracy, maxCycle]
     */
    @Query(value = "SELECT r.knowledge_point_id, " +
                   "       COUNT(*) AS total_reviews, " +
                   "       COALESCE(AVG(r.accuracy), 0) AS avg_accuracy, " +
                   "       COALESCE(MAX(r.review_cycle), 0) AS max_cycle " +
                   "FROM review_records r WHERE r.student_id = :studentId " +
                   "AND r.status = 'COMPLETED' " +
                   "GROUP BY r.knowledge_point_id ORDER BY total_reviews DESC",
           nativeQuery = true)
    List<Object[]> findKnowledgePointReviewStats(@Param("studentId") UUID studentId);

    /**
     * 统计某学生在指定日期范围内的每日排程与完成情况。
     *
     * @param studentId 学生 ID
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @return 对象数组列表，每个元素为 [scheduledDate, totalCount, completedCount]
     */
    @Query(value = "SELECT r.scheduled_date::date AS day, " +
                   "       COUNT(*) AS total, " +
                   "       COUNT(*) FILTER (WHERE r.status = 'COMPLETED') AS completed " +
                   "FROM review_records r WHERE r.student_id = :studentId " +
                   "AND r.scheduled_date BETWEEN :startDate AND :endDate " +
                   "GROUP BY day ORDER BY day ASC",
           nativeQuery = true)
    List<Object[]> findDailyScheduleStats(
            @Param("studentId") UUID studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
