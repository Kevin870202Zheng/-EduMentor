package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Quiz 提交响应 DTO。
 * 包含答题结果、解析、AI 反馈、掌握度变化和知识点关联信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitResponse {
    private Boolean isCorrect;
    private String correctAnswer;
    private String explanation;
    private String aiFeedback;
    private Integer masteryDelta;
    private Map<String, Object> bktUpdate;
    /** 关联的知识点名称（用于前端展示"这道题考察的是【XXX】"） */
    private String knowledgePointName;
}
