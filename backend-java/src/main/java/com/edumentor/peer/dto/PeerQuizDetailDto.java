package com.edumentor.peer.dto;

import com.edumentor.question.dto.QuestionDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 考核详情 DTO（含题目列表）。
 */
public record PeerQuizDetailDto(
        UUID id,
        UUID creatorId,
        String creatorName,
        UUID courseId,
        UUID knowledgePointId,
        String title,
        LocalDateTime deadline,
        String status,
        int participantCount,
        int completedCount,
        int questionCount,
        LocalDateTime createdAt,
        List<QuestionDto> questions
) {}
