package com.edumentor.peer.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 考核结果 DTO — 出题者视角查看每位学生的答题情况。
 */
public record PeerQuizResultDto(
        UUID quizId,
        String title,
        List<ParticipantResult> participants
) {
    public record ParticipantResult(
            UUID id,
            UUID studentId,
            String studentName,
            String status,
            Integer score,
            Integer totalQuestions,
            LocalDateTime completedAt
    ) {}
}
