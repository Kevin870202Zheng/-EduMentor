package com.edumentor.alert.repository;

import com.edumentor.alert.entity.AlertRecord;
import com.edumentor.entity.enums.AlertSeverity;
import com.edumentor.entity.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 预警记录 Repository — 提供预警记录的数据访问操作。
 * <p>
 * 支持按学生、教师、预警类型、级别、处理状态等多维度查询，
 * 以及用于 Dashboard 的聚合统计查询。
 * </p>
 *
 * @author EduMentor Team
 */
@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, UUID> {

    // ══════════════════════════════════════════════════════════════
    //  按学生查询
    // ══════════════════════════════════════════════════════════════

    /** 按学生 ID 查询所有预警（按创建时间降序） */
    Page<AlertRecord> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    /** 按学生 ID 查询未处理的预警 */
    List<AlertRecord> findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(UUID studentId);

    /** 按学生 ID 和预警类型查询 */
    Page<AlertRecord> findByStudentIdAndAlertTypeOrderByCreatedAtDesc(
            UUID studentId, AlertType alertType, Pageable pageable);

    /** 按学生 ID 和预警级别查询 */
    Page<AlertRecord> findByStudentIdAndSeverityOrderByCreatedAtDesc(
            UUID studentId, AlertSeverity severity, Pageable pageable);

    /** 按学生 ID 查询指定时间范围内的预警 */
    @Query("SELECT a FROM AlertRecord a WHERE a.studentId = :studentId " +
           "AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AlertRecord> findByStudentIdAndDateRange(
            @Param("studentId") UUID studentId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ══════════════════════════════════════════════════════════════
    //  按教师/处理人查询
    // ══════════════════════════════════════════════════════════════

    /** 按负责教师 ID 查询其名下学生的所有预警 */
    Page<AlertRecord> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);

    /** 按教师 ID 查询未处理的预警 */
    List<AlertRecord> findByTeacherIdAndIsResolvedFalseOrderByCreatedAtDesc(UUID teacherId);

    /** 按处理人 ID 查询已处理的预警 */
    Page<AlertRecord> findByResolvedByOrderByResolvedAtDesc(UUID resolvedBy, Pageable pageable);

    // ══════════════════════════════════════════════════════════════
    //  按状态查询
    // ══════════════════════════════════════════════════════════════

    /** 查询所有未处理的预警（按级别降序、创建时间降序） */
    @Query("SELECT a FROM AlertRecord a WHERE a.isResolved = false " +
           "ORDER BY CASE a.severity " +
           "  WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 " +
           "  WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 END, a.createdAt DESC")
    List<AlertRecord> findUnresolvedAlertsOrderedBySeverity();

    /** 查询未处理且未过期的预警 */
    @Query("SELECT a FROM AlertRecord a WHERE a.isResolved = false " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > :now) " +
           "ORDER BY a.createdAt DESC")
    List<AlertRecord> findActiveAlerts(@Param("now") LocalDateTime now);

    /** 查询已处理预警 */
    Page<AlertRecord> findByIsResolvedTrueOrderByResolvedAtDesc(Pageable pageable);

    // ══════════════════════════════════════════════════════════════
    //  按预警类型/级别查询
    // ══════════════════════════════════════════════════════════════

    /** 按预警类型查询 */
    Page<AlertRecord> findByAlertTypeOrderByCreatedAtDesc(AlertType alertType, Pageable pageable);

    /** 按预警级别查询 */
    Page<AlertRecord> findBySeverityOrderByCreatedAtDesc(AlertSeverity severity, Pageable pageable);

    /** 查询指定级别以上的未处理预警 */
    @Query("SELECT a FROM AlertRecord a WHERE a.severity IN :severities " +
           "AND a.isResolved = false ORDER BY a.createdAt DESC")
    List<AlertRecord> findBySeveritiesAndUnresolved(
            @Param("severities") List<AlertSeverity> severities);

    // ══════════════════════════════════════════════════════════════
    //  聚合统计
    // ══════════════════════════════════════════════════════════════

    /** 按预警级别统计未处理预警数量 */
    @Query("SELECT a.severity, COUNT(a) FROM AlertRecord a " +
           "WHERE a.isResolved = false GROUP BY a.severity")
    List<Object[]> countUnresolvedBySeverity();

    /** 按预警类型统计未处理预警数量 */
    @Query("SELECT a.alertType, COUNT(a) FROM AlertRecord a " +
           "WHERE a.isResolved = false GROUP BY a.alertType")
    List<Object[]> countUnresolvedByType();

    /** 统计未处理预警总数 */
    long countByIsResolvedFalse();

    /** 统计某个学生的未处理预警数 */
    long countByStudentIdAndIsResolvedFalse(UUID studentId);

    /** 统计某个学生的预警总数 */
    long countByStudentId(UUID studentId);

    /** 统计某个级别的预警数量 */
    long countBySeverity(AlertSeverity severity);

    /** 统计某时间范围内的预警数量 */
    @Query("SELECT COUNT(a) FROM AlertRecord a WHERE a.createdAt BETWEEN :start AND :end")
    long countByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 统计某时间范围内各级别的预警数量 */
    @Query("SELECT a.severity, COUNT(a) FROM AlertRecord a " +
           "WHERE a.createdAt BETWEEN :start AND :end GROUP BY a.severity")
    List<Object[]> countBySeverityInDateRange(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 获取最近 N 条预警 */
    List<AlertRecord> findTopByOrderByCreatedAtDesc(Pageable pageable);
}
