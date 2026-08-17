package com.edumentor.arbitration.repository;

import com.edumentor.arbitration.entity.ArbitrationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 仲裁消息仓库。
 */
public interface ArbitrationMessageRepository extends JpaRepository<ArbitrationMessage, UUID> {

    List<ArbitrationMessage> findBySessionIdOrderByRoundSeqAscCreatedAtAsc(UUID sessionId);

    List<ArbitrationMessage> findTop30BySessionIdOrderByRoundSeqDescCreatedAtDesc(UUID sessionId);
}
