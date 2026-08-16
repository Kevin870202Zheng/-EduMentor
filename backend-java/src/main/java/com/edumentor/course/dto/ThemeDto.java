package com.edumentor.course.dto;

import java.util.UUID;

/**
 * 跨学段主题响应 DTO（PRD v4.0 §11.3）。
 *
 * @param id          主题 ID
 * @param subject     学科
 * @param code        主题代码（LAW_RULE / CONSTITUTION / ...）
 * @param name        主题名称
 * @param description 主题描述
 * @param icon        主题图标（emoji）
 * @param sortOrder   展示排序
 * @param kpCount     该主题在指定学段下的知识点数量（未指定学段时为全部）
 *
 * @author EduMentor Team
 * @version 1.0
 */
public record ThemeDto(
        UUID id,
        String subject,
        String code,
        String name,
        String description,
        String icon,
        int sortOrder,
        long kpCount
) {
}
