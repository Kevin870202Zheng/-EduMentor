package com.edumentor.arbitration.repository;

import com.edumentor.arbitration.entity.ArbitrationSession;
import com.edumentor.arbitration.entity.enums.ArbitrationPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 仲裁会话仓库。
 */
public interface ArbitrationSessionRepository extends JpaRepository<ArbitrationSession, UUID> {

    Optional<ArbitrationSession> findByKnowledgePointIdAndStudentIdAndPhase(
            UUID knowledgePointId, UUID studentId, ArbitrationPhase phase);

    List<ArbitrationSession> findByKnowledgePointIdAndStudentIdOrderByCreatedAtAsc(
            UUID knowledgePointId, UUID studentId);

    /** 查找该知识点已存在的 PRE 案件（用于 POST 复用） */
    Optional<ArbitrationSession> findFirstByKnowledgePointIdAndStudentIdAndPhaseOrderByCreatedAtDesc(
            UUID knowledgePointId, UUID studentId, ArbitrationPhase phase);
}
