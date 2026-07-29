package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 课堂生成请求 DTO。
 * 根据知识点 ID 和学习画像，触发 AI 课堂生成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomGenerateRequest {
    private String courseCode;
    private List<String> knowledgePointIds;
    private Integer difficulty;
    private Boolean generateFull;

    /** 生成策略: on_demand(按需) | full(全量) | remedial(补强) */
    private String strategy;
}
