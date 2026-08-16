package com.edumentor.course.dto;

import com.edumentor.course.entity.KnowledgePoint;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识点响应 DTO。
 * <p>
 * 用于返回知识点信息的标准格式。
 * 可从 {@link KnowledgePoint} 实体通过静态工厂方法转换。
 * </p>
 *
 * @param id          知识点 ID
 * @param courseId    所属课程 ID
 * @param parentKpId  父知识点 ID（顶层节点为 null）
 * @param name        知识点名称
 * @param description 知识点描述
 * @param content     知识点详细内容
 * @param difficulty  难度等级（1-5）
 * @param importance  重要程度（1-5）
 * @param subject     学科
 * @param tags        标签（JSON 字符串数组）
 * @param type         节点类型（VOLUME/PART/CHAPTER/SECTION/LEAF）
 * @param sequencePath 路径编号（如 "1.2.3"）
 * @param orderIndex  排序序号
 * @param stage       所属学段（PRIMARY/JUNIOR/SENIOR/UNIVERSITY）
 * @param depthLevel  认知深度（1-5，与 difficulty 正交）
 * @param themeId     所属跨学段主题 ID
 * @param stageOrder  学段内排序
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 *
 * @author EduMentor Team
 * @version 1.0
 */
public record KnowledgePointDto(
        UUID id,
        UUID courseId,
        UUID parentKpId,
        String name,
        String description,
        String content,
        int difficulty,
        int importance,
        String subject,
        String tags,
        String type,
        String sequencePath,
        int orderIndex,
        String stage,
        int depthLevel,
        UUID themeId,
        int stageOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 从 {@link KnowledgePoint} 实体转换为 DTO。
     *
     * @param entity 知识点实体
     * @return 知识点 DTO
     */
    public static KnowledgePointDto fromEntity(KnowledgePoint entity) {
        return new KnowledgePointDto(
                entity.getId(),
                entity.getCourseId(),
                entity.getParentKpId(),
                entity.getName(),
                entity.getDescription(),
                entity.getContent(),
                entity.getDifficulty(),
                entity.getImportance(),
                entity.getSubject(),
                entity.getTags(),
                entity.getType(),
                entity.getSequencePath(),
                entity.getOrderIndex(),
                entity.getStage(),
                entity.getDepthLevel(),
                entity.getThemeId(),
                entity.getStageOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
