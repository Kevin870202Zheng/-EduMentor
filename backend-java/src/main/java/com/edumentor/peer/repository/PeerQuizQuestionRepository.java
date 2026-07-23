package com.edumentor.peer.repository;

import com.edumentor.peer.entity.PeerQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 考核题目关联数据访问层。
 */
@Repository
public interface PeerQuizQuestionRepository extends JpaRepository<PeerQuizQuestion, UUID> {

    List<PeerQuizQuestion> findByQuizIdOrderByOrderIndexAsc(UUID quizId);

    void deleteByQuizId(UUID quizId);
}
