package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.MootCourtSession;
import com.edumentor.classroom.entity.enums.MootCourtPhase;
import com.edumentor.classroom.entity.enums.MootCourtStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MootCourtSessionRepository extends JpaRepository<MootCourtSession, UUID> {

    /** 查询某学生某课堂某阶段的法庭会话 */
    Optional<MootCourtSession> findByClassroomIdAndStudentIdAndPhase(
            UUID classroomId, UUID studentId, MootCourtPhase phase);

    /** 查询某课堂某阶段的所有会话（按创建时间倒序） */
    List<MootCourtSession> findByClassroomIdAndPhaseOrderByCreatedAtDesc(
            UUID classroomId, MootCourtPhase phase);

    /** 查询某学生的全部法庭会话（学生端列表） */
    List<MootCourtSession> findByStudentIdOrderByUpdatedAtDesc(UUID studentId);

    /** 按状态统计（用于管理端/调试） */
    long countByStatus(MootCourtStatus status);
}
