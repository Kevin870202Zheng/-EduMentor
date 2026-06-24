package com.edumentor.question.dto;

import com.edumentor.entity.enums.QuestionType;
import lombok.Data;

import java.util.UUID;

/**
 * 更新题目请求 DTO（全部可选）。
 */
@Data
public class QuestionUpdateRequest {

    private UUID knowledgePointId;
    private QuestionType questionType;
    private String content;
    private String options;
    private String correctAnswer;
    private String explanation;
    private Integer difficulty;
    private Boolean isPublished;
}
