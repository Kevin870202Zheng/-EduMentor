package com.edumentor.question.dto;

import com.edumentor.entity.enums.QuestionType;
import com.edumentor.record.entity.Question;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 题目响应 DTO。
 *
 * @param id              题目 ID
 * @param knowledgePointId 所属知识点 ID
 * @param courseId         所属课程 ID
 * @param questionType     题目类型
 * @param content          题目内容
 * @param options          选项（JSON 字符串）
 * @param correctAnswer    正确答案
 * @param explanation      解析
 * @param difficulty       难度（1-5）
 * @param isPublished      是否已发布
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record QuestionDto(
        UUID id,
        UUID knowledgePointId,
        UUID courseId,
        QuestionType questionType,
        String content,
        String options,
        String correctAnswer,
        String explanation,
        Integer difficulty,
        boolean isPublished,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static QuestionDto fromEntity(Question entity) {
        return new QuestionDto(
                entity.getId(),
                entity.getKnowledgePointId(),
                entity.getCourseId(),
                entity.getQuestionType(),
                entity.getContent(),
                entity.getOptions(),
                entity.getCorrectAnswer(),
                entity.getExplanation(),
                entity.getDifficulty(),
                entity.getIsPublished(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
