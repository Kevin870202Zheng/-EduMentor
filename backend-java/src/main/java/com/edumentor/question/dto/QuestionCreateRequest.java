package com.edumentor.question.dto;

import com.edumentor.entity.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 创建题目请求 DTO。
 */
@Data
public class QuestionCreateRequest {

    @NotNull(message = "知识点 ID 不能为空")
    private UUID knowledgePointId;

    @NotNull(message = "课程 ID 不能为空")
    private UUID courseId;

    @NotNull(message = "题目类型不能为空")
    private QuestionType questionType;

    @NotBlank(message = "题目内容不能为空")
    private String content;

    private String options;

    @NotBlank(message = "正确答案不能为空")
    private String correctAnswer;

    private String explanation;

    private Integer difficulty = 3;
}
