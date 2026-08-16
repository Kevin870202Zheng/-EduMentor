package com.edumentor.learningpath.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * AI 路径规划结构化输出 — LLM 返回的路径方案 JSON。
 * <p>
 * 由 {@code askStructured} 解析；后端校验 knowledgePointId 均存在于课程知识库后落库。
 * ignoreUnknown=true 容错 LLM 输出的额外字段（如 name）。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiPathPlanResult {

    private String name;
    private String description;
    private Integer estimatedMinutes;
    private List<AiPathNode> nodes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiPathNode {
        private UUID knowledgePointId;
        private Integer estimatedMinutes;
        /** AI 选择理由（展示给学生的依据） */
        private String reason;
    }
}
