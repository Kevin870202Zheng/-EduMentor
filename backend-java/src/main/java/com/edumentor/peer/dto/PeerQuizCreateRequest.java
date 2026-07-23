package com.edumentor.peer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 创建考核任务请求。
 *
 * @param title            考核标题
 * @param courseId         所属课程 ID
 * @param knowledgePointId 关联知识点 ID（可选）
 * @param participantIds   被考核学生 ID 列表
 * @param deadline          截止时间（可选）
 */
public record PeerQuizCreateRequest(
        @NotBlank String title,
        @NotNull UUID courseId,
        UUID knowledgePointId,
        @NotEmpty List<@NotNull UUID> participantIds,
        LocalDateTime deadline
) {}
