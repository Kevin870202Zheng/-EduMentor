package com.edumentor.learningpath.dto;

import com.edumentor.learningpath.entity.PathTemplateNode;
import lombok.Data;

import java.util.UUID;

/**
 * 路径模板节点 DTO。
 * <p>
 * 动态模板（师范生备课）预览时，lessonIndex/lessonTitle 标识
 * 该知识点所属的"一课"分组（一课=240 分钟）。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class PathTemplateNodeDto {

    private UUID id;
    private UUID templateId;
    private UUID knowledgePointId;
    private String knowledgePointName;
    private Integer orderIndex;
    private Integer estimatedMinutes;
    private Integer lessonIndex;
    private String lessonTitle;

    public static PathTemplateNodeDto fromEntity(PathTemplateNode entity) {
        if (entity == null) {
            return null;
        }
        PathTemplateNodeDto dto = new PathTemplateNodeDto();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setKnowledgePointId(entity.getKnowledgePointId());
        dto.setKnowledgePointName(entity.getKnowledgePointName());
        dto.setOrderIndex(entity.getOrderIndex());
        dto.setEstimatedMinutes(entity.getEstimatedMinutes());
        return dto;
    }

    /** 从动态计算结果构建（无持久化节点实体） */
    public static PathTemplateNodeDto of(UUID knowledgePointId, String knowledgePointName,
                                         int orderIndex, int estimatedMinutes) {
        PathTemplateNodeDto dto = new PathTemplateNodeDto();
        dto.setKnowledgePointId(knowledgePointId);
        dto.setKnowledgePointName(knowledgePointName);
        dto.setOrderIndex(orderIndex);
        dto.setEstimatedMinutes(estimatedMinutes);
        return dto;
    }
}
