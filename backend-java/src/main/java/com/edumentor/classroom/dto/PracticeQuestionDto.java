package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课后练习题 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeQuestionDto {
    private String id;
    private String questionContent;
    private String[] options;
    private Integer correctIndex;
    private String explanation;
    private String knowledgePointId;
    private String knowledgePointName;
    private String relatedSceneId;
    private String relatedSceneTitle;
    private String difficulty;
}
