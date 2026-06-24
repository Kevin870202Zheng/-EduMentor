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

    List<AnswerRecord> findByStudentIdAndAttemptedAtBetween(
        UUID studentId, LocalDateTime start, LocalDateTime end);

    List<AnswerRecord> findByStudentIdAndCourseIdAndAttemptedAtBetween(
        UUID studentId, UUID courseId, LocalDateTime start, LocalDateTime end);

    long countByStudentIdAndAttemptedAtBetween(
        UUID studentId, LocalDateTime start, LocalDateTime end);

    long countByStudentIdAndIsCorrectTrueAndAttemptedAtBetween(
        UUID studentId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT a.knowledgePointId, COUNT(a), SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END) " +
           "FROM AnswerRecord a WHERE a.studentId = :studentId AND a.attemptedAt BETWEEN :start AND :end " +
           "GROUP BY a.knowledgePointId")
    List<Object[]> aggregateByKnowledgePoint(
        @Param("studentId") UUID studentId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT a.knowledgePointId, COUNT(a), SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END) " +
           "FROM AnswerRecord a WHERE a.studentId = :studentId AND a.courseId = :courseId " +
           "AND a.attemptedAt BETWEEN :start AND :end GROUP BY a.knowledgePointId")
    List<Object[]> aggregateByKnowledgePointAndCourse(
        @Param("studentId") UUID studentId,
        @Param("courseId") UUID courseId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT a.knowledgePointId, COUNT(a), SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END) " +
           "FROM AnswerRecord a WHERE a.studentId = :studentId " +
           "GROUP BY a.knowledgePointId")
    List<Object[]> aggregateByKnowledgePointAll(
        @Param("studentId") UUID studentId);

    @Query("SELECT FUNCTION('DATE', a.attemptedAt), COUNT(a), SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END) " +
           "FROM AnswerRecord a WHERE a.studentId = :studentId AND a.attemptedAt BETWEEN :start AND :end " +
           "GROUP BY FUNCTION('DATE', a.attemptedAt) ORDER BY FUNCTION('DATE', a.attemptedAt)")
    List<Object[]> dailyAggregate(
        @Param("studentId") UUID studentId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query(value = "SELECT COUNT(DISTINCT a.knowledge_point_id) FROM answer_records a " +
           "WHERE a.student_id = :studentId AND a.course_id = :courseId", nativeQuery = true)
    long countDistinctKnowledgePointIdByStudentIdAndCourseId(
        @Param("studentId") UUID studentId,
        @Param("courseId") UUID courseId);

    Page<AnswerRecord> findByStudentIdOrderByAttemptedAtDesc(UUID studentId, Pageable pageable);

    long countByStudentIdAndCourseId(UUID studentId, UUID courseId);
}
