package com.edumentor.peer.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 考核任务响应 DTO。
 *
 * @param id               考核 ID
 * @param creatorId        出题学生 ID
 * @param creatorName      出题学生姓名
 * @param courseId         课程 ID
 * @param knowledgePointId 知识点 ID
 * @param title            标题
 * @param deadline         截止时间
 * @param status           状态（OPEN / CLOSED）
 * @param participantCount 参与学生数
 * @param completedCount   已完成学生数
 * @param questionCount    题目数
 * @param createdAt        创建时间
 */
public record PeerQuizDto(
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
        LocalDateTime createdAt
) {}
