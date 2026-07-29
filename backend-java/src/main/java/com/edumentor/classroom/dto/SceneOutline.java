package com.edumentor.classroom.dto;

import com.edumentor.classroom.entity.enums.SceneType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 课堂大纲 — LLM 结构化输出的第一段产物。
 * 描述一个教学场景的概要信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneOutline {
    private SceneType type;
    private String title;
    private String description;
    private List<String> keyPoints;
    private String teachingObjective;
    private Integer estimatedDurationSeconds;
    private Integer order;
}
