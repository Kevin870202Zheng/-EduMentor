package com.edumentor.course.dto;

import com.edumentor.course.entity.KnowledgeRelation;
import com.edumentor.course.entity.enums.RelationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识点关系响应 DTO。
 * <p>
 * 用于返回知识点之间关系的标准格式。
 * 可从 {@link KnowledgeRelation} 实体通过静态工厂方法转换。
 * </p>
 *
 * @param id           关系 ID
 * @param sourceKpId   源知识点 ID
 * @param targetKpId   目标知识点 ID
 * @param relationType 关系类型
 * @param weight       关系权重
 * @param description  关系描述
 * @param createdAt    创建时间
 *
 * @author EduMentor Team
 * @version 1.0
 */
public record KnowledgeRelationDto(
        UUID id,
        UUID sourceKpId,
        UUID targetKpId,
        RelationType relationType,
        BigDecimal weight,
        String description,
        LocalDateTime createdAt
) {
    /**
     * 从 {@link KnowledgeRelation} 实体转换为 DTO。
     *
     * @param entity 知识点关系实体
     * @return 知识点关系 DTO
     */
    public static KnowledgeRelationDto fromEntity(KnowledgeRelation entity) {
        return new KnowledgeRelationDto(
                entity.getId(),
                entity.getSourceKpId(),
                entity.getTargetKpId(),
                entity.getRelationType(),
                entity.getWeight(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }
}
