package com.edumentor.review.repository;

import com.edumentor.review.entity.ErrorRecord;
import com.edumentor.entity.enums.ErrorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 错题记录 Repository — 提供错题数据的持久化操作和统计分析。
 * <p>
 * 支持按学生、复习状态、知识点等维度查询错题记录，
 * 并提供高频错题知识点、错因类型分布、每日趋势等分析查询。
 * </p>
 *
 * @author EduMentor Team
 */
@Repository
public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, UUID> {

    // ══════════════════════════════════════════════════════════════
    //  基本查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询某学生的所有错题记录。
     *
     * @param studentId 学生 ID
     * @return 错题记录列表
     */
    List<ErrorRecord> findByStudentId(UUID studentId);

    /**
     * 按 studentId 和 courseId 查询错题记录。
     *
     * @param studentId 学生 ID
     * @param courseId  课程 ID
     * @return 错题记录列表
     */
    List<ErrorRecord> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    /**
     * 按复习状态查询某学生的错题记录。
     *
     * @param studentId  学生 ID
     * @param isReviewed 是否已复习
     * @return 错题记录列表
     */
    List<ErrorRecord> findByStudentIdAndIsReviewed(UUID studentId, Boolean isReviewed);

    /**
     * 查询某学生未复习的错题记录。
     *
     * @param studentId 学生 ID
     * @return 未复习的错题记录列表
     */
    List<ErrorRecord> findByStudentIdAndIsReviewedFalse(UUID studentId);

    /**
     * 查询某学生在某知识点下的错题记录。
     *
     * @param studentId        学生 ID
     * @param knowledgePointId 知识点 ID
     * @return 错题记录列表
     */
    List<ErrorRecord> findByStudentIdAndKnowledgePointId(UUID studentId, UUID knowledgePointId);

    /**
     * 统计某学生的错题总数。
     *
     * @param studentId 学生 ID
     * @return 错题总数
     */
    long countByStudentId(UUID studentId);

    /**
     * 统计某学生已复习或未复习的错题数量。
     *
     * @param studentId  学生 ID
     * @param isReviewed 是否已复习
     * @return 错题数量
     */
    long countByStudentIdAndIsReviewed(UUID studentId, Boolean isReviewed);

    // ══════════════════════════════════════════════════════════════
    //  统计分析查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 统计某学生各知识点的错题频率（按频次降序排列）。
     * <p>
     * 用于识别高频错题知识点，帮助定位薄弱环节。
     * </p>
     *
     * @param studentId 学生 ID
     * @return 对象数组列表，每个元素为 [knowledgePointId, errorCount]
     */
    @Query("SELECT e.knowledgePointId, COUNT(e) AS cnt " +
           "FROM ErrorRecord e WHERE e.studentId = :studentId " +
           "GROUP BY e.knowledgePointId ORDER BY cnt DESC")
    List<Object[]> findTopErrorKnowledgePointsByFrequency(@Param("studentId") UUID studentId);

    /**
     * 统计某学生各错因类型（ErrorType）的分布情况。
     *
     * @param studentId 学生 ID
     * @return 对象数组列表，每个元素为 [errorType, count]
     */
    @Query("SELECT e.errorType, COUNT(e) " +
           "FROM ErrorRecord e WHERE e.studentId = :studentId AND e.errorType IS NOT NULL " +
           "GROUP BY e.errorType ORDER BY COUNT(e) DESC")
    List<Object[]> findErrorTypeDistribution(@Param("studentId") UUID studentId);

    /**
     * 统计某学生每日新增错题数量趋势（最近 N 天）。
     *
     * @param studentId 学生 ID
     * @param days      回溯天数
     * @return 对象数组列表，每个元素为 [dateStr, count]
     */
    @Query(value = "SELECT TO_CHAR(e.created_at::date, 'YYYY-MM-DD') AS day, COUNT(*) " +
                   "FROM error_records e WHERE e.student_id = :studentId " +
                   "AND e.created_at >= CURRENT_DATE - (:days || ' days')::interval " +
                   "GROUP BY day ORDER BY day ASC",
           nativeQuery = true)
    List<Object[]> findDailyErrorTrend(@Param("studentId") UUID studentId,
                                       @Param("days") int days);

    /**
     * 统计某学生在指定知识点上的错因类型分布。
     *
     * @param studentId        学生 ID
     * @param knowledgePointId 知识点 ID
     * @return 对象数组列表，每个元素为 [errorType, count]
     */
    @Query("SELECT e.errorType, COUNT(e) FROM ErrorRecord e " +
           "WHERE e.studentId = :studentId AND e.knowledgePointId = :knowledgePointId " +
           "AND e.errorType IS NOT NULL " +
           "GROUP BY e.errorType ORDER BY COUNT(e) DESC")
    List<Object[]> findErrorTypeDistributionByKp(
            @Param("studentId") UUID studentId,
            @Param("knowledgePointId") UUID knowledgePointId);
}
