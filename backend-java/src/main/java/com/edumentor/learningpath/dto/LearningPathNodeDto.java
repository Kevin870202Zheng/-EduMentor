package com.edumentor.learningpath.dto;

import com.edumentor.learningpath.entity.LearningPathNode;
import com.edumentor.learningpath.entity.PathNodeStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习路径节点 DTO — 用于路径节点信息展示。
 * <p>
 * 包含节点状态、所属知识点、排序信息等。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class LearningPathNodeDto {

    private UUID id;
    private UUID learningPathId;
    private UUID knowledgePointId;
    private String knowledgePointName;
    private Integer orderIndex;
    private PathNodeStatus status;
    private Boolean isRecommended;
    private Integer estimatedMinutes;
    private Integer actualMinutes;
    private Double masteryThreshold;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将 LearningPathNode 实体转换为 DTO。
     *
     * @param entity 路径节点实体
     * @return DTO 对象
     */
    public static LearningPathNodeDto fromEntity(LearningPathNode entity) {
        if (entity == null) {
            return null;
        }
        LearningPathNodeDto dto = new LearningPathNodeDto();
        dto.setId(entity.getId());
        dto.setLearningPathId(entity.getLearningPath() != null ? entity.getLearningPath().getId() : null);
        dto.setKnowledgePointId(entity.getKnowledgePointId());
        dto.setKnowledgePointName(entity.getKnowledgePointName());
        dto.setOrderIndex(entity.getOrderIndex());
        dto.setStatus(entity.getStatus());
        dto.setIsRecommended(entity.getIsRecommended());
        dto.setEstimatedMinutes(entity.getEstimatedMinutes());
        dto.setActualMinutes(entity.getActualMinutes());
        dto.setMasteryThreshold(entity.getMasteryThreshold());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
