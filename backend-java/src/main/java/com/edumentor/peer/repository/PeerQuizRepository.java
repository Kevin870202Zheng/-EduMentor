package com.edumentor.peer.repository;

import com.edumentor.peer.entity.PeerQuiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 考核任务数据访问层。
 */
@Repository
public interface PeerQuizRepository extends JpaRepository<PeerQuiz, UUID> {

    List<PeerQuiz> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    List<PeerQuiz> findByCourseIdAndStatusOrderByCreatedAtDesc(UUID courseId, String status);

    List<PeerQuiz> findByCourseIdOrderByCreatedAtDesc(UUID courseId);
}
