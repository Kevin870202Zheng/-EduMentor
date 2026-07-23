package com.edumentor.peer.repository;

import com.edumentor.peer.entity.PeerQuizParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 考核参与学生数据访问层。
 */
@Repository
public interface PeerQuizParticipantRepository extends JpaRepository<PeerQuizParticipant, UUID> {

    List<PeerQuizParticipant> findByQuizId(UUID quizId);

    List<PeerQuizParticipant> findByStudentIdAndStatusOrderByCreatedAtDesc(UUID studentId, String status);

    List<PeerQuizParticipant> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    Optional<PeerQuizParticipant> findByQuizIdAndStudentId(UUID quizId, UUID studentId);

    long countByQuizIdAndStatus(UUID quizId, String status);
}
