package com.edumentor.diagnosis.repository;

import com.edumentor.record.entity.AnswerRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnswerRecordRepository extends JpaRepository<AnswerRecord, UUID> {

    List<AnswerRecord> findByStudentIdAndKnowledgePointIdAndAttemptedAtBetween(
        UUID studentId, UUID kpId, LocalDateTime start, LocalDateTime end);

    /** 查询某学生对某知识点的全部作答记录（用于仲裁准入：掌握度 ≥ 0.5） */
    List<AnswerRecord> findByStudentIdAndKnowledgePointId(UUID studentId, UUID kpId);

    List<AnswerRecord> findByStudentIdAndAttemptedAtBetween(
        UUID studentId, LocalDateTime start, LocalDateTime end);

    List<AnswerRecord> findByStudentIdAndCourseIdAndAttemptedAtBetween(
        UUID studentId, UUID courseId, LocalDateTime start, LocalDateTime end);

    long countByStudentIdAndAttemptedAtBetween(
        UUID studentId, LocalDateTime start, LocalDateTime end);

    long countByStudentIdAndIsCorrectTrueAndAttemptedAtBetween(
        UUID studentId, LocalDateTime start, LocalDateTime end);

    @Query(value = "SELECT a.knowledge_point_id, COUNT(*), " +
           "SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END), " +
           "COALESCE(kp.name, '未知知识点') " +
           "FROM answer_records a " +
           "LEFT JOIN knowledge_points kp ON kp.id = a.knowledge_point_id " +
           "WHERE a.student_id = :studentId AND a.attempted_at BETWEEN :start AND :end " +
           "GROUP BY a.knowledge_point_id, kp.name", nativeQuery = true)
    List<Object[]> aggregateByKnowledgePoint(
        @Param("studentId") UUID studentId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query(value = "SELECT a.knowledge_point_id, COUNT(*), " +
           "SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END), " +
           "COALESCE(kp.name, '未知知识点') " +
           "FROM answer_records a " +
           "LEFT JOIN knowledge_points kp ON kp.id = a.knowledge_point_id " +
           "WHERE a.student_id = :studentId AND a.course_id = :courseId " +
           "AND a.attempted_at BETWEEN :start AND :end " +
           "GROUP BY a.knowledge_point_id, kp.name", nativeQuery = true)
    List<Object[]> aggregateByKnowledgePointAndCourse(
        @Param("studentId") UUID studentId,
        @Param("courseId") UUID courseId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query(value = "SELECT a.knowledge_point_id, COUNT(*), " +
           "SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END), " +
           "COALESCE(kp.name, '未知知识点') " +
           "FROM answer_records a " +
           "LEFT JOIN knowledge_points kp ON kp.id = a.knowledge_point_id " +
           "WHERE a.student_id = :studentId " +
           "GROUP BY a.knowledge_point_id, kp.name", nativeQuery = true)
    List<Object[]> aggregateByKnowledgePointAll(
        @Param("studentId") UUID studentId);

    @Query(value = "SELECT a.knowledge_point_id, COUNT(*), " +
           "SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END), " +
           "COALESCE(kp.name, '未知知识点') " +
           "FROM answer_records a " +
           "LEFT JOIN knowledge_points kp ON kp.id = a.knowledge_point_id " +
           "WHERE a.student_id = :studentId AND a.course_id = :courseId " +
           "GROUP BY a.knowledge_point_id, kp.name", nativeQuery = true)
    List<Object[]> aggregateByKnowledgePointAllAndCourse(
        @Param("studentId") UUID studentId,
        @Param("courseId") UUID courseId);

    @Query("SELECT FUNCTION('DATE', a.attemptedAt), COUNT(a), SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END) " +
           "FROM AnswerRecord a WHERE a.studentId = :studentId AND a.attemptedAt BETWEEN :start AND :end " +
           "GROUP BY FUNCTION('DATE', a.attemptedAt) ORDER BY FUNCTION('DATE', a.attemptedAt)")
    List<Object[]> dailyAggregate(
        @Param("studentId") UUID studentId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT FUNCTION('DATE', a.attemptedAt), COUNT(a), SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END) " +
           "FROM AnswerRecord a WHERE a.studentId = :studentId AND a.courseId = :courseId " +
           "AND a.attemptedAt BETWEEN :start AND :end " +
           "GROUP BY FUNCTION('DATE', a.attemptedAt) ORDER BY FUNCTION('DATE', a.attemptedAt)")
    List<Object[]> dailyAggregateByCourse(
        @Param("studentId") UUID studentId,
        @Param("courseId") UUID courseId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query(value = "SELECT COUNT(DISTINCT a.knowledge_point_id) FROM answer_records a " +
           "WHERE a.student_id = :studentId AND a.course_id = :courseId", nativeQuery = true)
    long countDistinctKnowledgePointIdByStudentIdAndCourseId(
        @Param("studentId") UUID studentId,
        @Param("courseId") UUID courseId);

    Page<AnswerRecord> findByStudentIdOrderByAttemptedAtDesc(UUID studentId, Pageable pageable);

    long countByStudentIdAndCourseId(UUID studentId, UUID courseId);

    /**
     * 按学生和题目查询答题记录（用于出题考核的结果统计）。
     */
    List<AnswerRecord> findByStudentIdAndQuestionIdOrderByAttemptedAtDesc(
            UUID studentId, UUID questionId);
}
