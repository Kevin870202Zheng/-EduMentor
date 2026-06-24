package com.edumentor.diagnosis.repository;

import com.edumentor.session.entity.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    List<StudySession> findByStudentIdAndStartTimeBetween(
        UUID studentId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT FUNCTION('DATE', s.startTime), SUM(s.durationSeconds), COUNT(s), AVG(s.focusScore) " +
           "FROM StudySession s WHERE s.studentId = :studentId " +
           "AND s.startTime BETWEEN :start AND :end " +
           "GROUP BY FUNCTION('DATE', s.startTime)")
    List<Object[]> dailySessionAggregate(
        @Param("studentId") UUID studentId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    long countByStudentIdAndStartTimeAfter(UUID studentId, LocalDateTime start);
}
