package com.edumentor.peer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建考核的完整请求包装 — 同时包含考核信息和题目列表。
 *
 * @param quiz      考核信息
 * @param questions 题目列表（至少 1 题）
 */
public record PeerQuizCreateRequestWrapper(
        @NotNull @Valid PeerQuizCreateRequest quiz,
        @NotNull @Valid List<@Valid PeerQuizQuestionCreateRequest> questions
) {}
