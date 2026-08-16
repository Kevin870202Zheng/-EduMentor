package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.MootCourtMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MootCourtMessageRepository extends JpaRepository<MootCourtMessage, UUID> {

    /** 某会话的全部庭审消息（按轮次、时间升序） */
    List<MootCourtMessage> findBySessionIdOrderByRoundSeqAscCreatedAtAsc(UUID sessionId);

    /** 某会话的最近 N 条消息（取 last N 条用于 LLM 上下文） */
    List<MootCourtMessage> findTop30BySessionIdOrderByRoundSeqDescCreatedAtDesc(UUID sessionId);

    long countBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
