package com.edumentor.peer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 创建考核题目请求。
 *
 * @param content       题目内容
 * @param questionType  题目类型（SINGLE_CHOICE / MULTIPLE_CHOICE / TRUE_FALSE / FILL_BLANK / SHORT_ANSWER）
 * @param options       选项（JSON 结构：{"A":"内容","B":"内容",...}）
 * @param correctAnswer 正确答案
 * @param explanation   解析
 */
public record PeerQuizQuestionCreateRequest(
        @NotBlank String content,
        @NotBlank String questionType,
        Map<String, String> options,
        @NotBlank String correctAnswer,
        String explanation
) {}
