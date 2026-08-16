package com.edumentor.course.dto;

import java.util.List;

/**
 * 知识阶梯深度分组 DTO（PRD v4.0 §11.4）。
 * <p>
 * 主题下知识点按 {@code depth_level} 分层的返回结构，
 * KnowledgeLadder 组件按深度渲染阶梯层级。
 * </p>
 *
 * @param depthLevel      认知深度（1-5）
 * @param knowledgePoints 该深度下的知识点列表
 *
 * @author EduMentor Team
 * @version 1.0
 */
public record KnowledgePointGroupDto(
        int depthLevel,
        List<KnowledgePointDto> knowledgePoints
) {
}
