package com.edumentor.learningpath.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 规划会话响应 DTO。
 * <p>
 * path 在 generatePath=true 且 LLM 成功输出路径后返回。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class AiPlanResponse {

    private String sessionId;

    /** LLM 回复文本 */
    private String reply;

    /** 生成路径成功后的路径（DRAFT） */
    private LearningPathDto path;

    /** 候选知识点（首轮可附带） */
    private List<PathTemplateNodeDto> candidates;
}
