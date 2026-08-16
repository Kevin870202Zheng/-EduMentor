package com.edumentor.learningpath.dto;

import com.edumentor.learningpath.entity.PathTemplate;
import lombok.Data;

import java.util.UUID;

/**
 * 路径模板 DTO — 用于推荐卡片展示。
 *
 * @author EduMentor Team
 */
@Data
public class PathTemplateDto {

    private UUID id;
    private UUID courseId;
    private String code;
    private String name;
    private String description;
    private String icon;
    private Integer totalMinutes;
    private Integer nodeCount;
    private Boolean isVisible;
    private String templateType;
    private Integer sortOrder;

    public static PathTemplateDto fromEntity(PathTemplate entity) {
        if (entity == null) {
            return null;
        }
        PathTemplateDto dto = new PathTemplateDto();
        dto.setId(entity.getId());
        dto.setCourseId(entity.getCourseId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setIcon(entity.getIcon());
        dto.setTotalMinutes(entity.getTotalMinutes());
        dto.setNodeCount(entity.getNodeCount());
        dto.setIsVisible(entity.getIsVisible());
        dto.setTemplateType(entity.getTemplateType());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }
}
