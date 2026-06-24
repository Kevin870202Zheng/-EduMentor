package com.edumentor.course.repository;

import com.edumentor.course.entity.KnowledgeRelation;
import com.edumentor.course.entity.enums.RelationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 知识点关系 Repository — 提供知识点关系实体的数据访问操作。
 * <p>
 * 核心查询包括：按源/目标知识点查询关系、关系存在性校验等。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Repository
public interface KnowledgeRelationRepository extends JpaRepository<KnowledgeRelation, UUID> {

    /**
     * 查询以指定知识点为源或目标的所有关系。
     *
     * @param sourceKpId 源知识点 ID
     * @param targetKpId 目标知识点 ID
     * @return 关系列表
     */
    List<KnowledgeRelation> findBySourceKpIdOrTargetKpId(UUID sourceKpId, UUID targetKpId);

    /**
     * 查询以指定知识点为源节点的所有关系。
     *
     * @param sourceKpId 源知识点 ID
     * @return 关系列表
     */
    List<KnowledgeRelation> findBySourceKpId(UUID sourceKpId);

    /**
     * 查询以指定知识点为目标节点的所有关系。
     *
     * @param targetKpId 目标知识点 ID
     * @return 关系列表
     */
    List<KnowledgeRelation> findByTargetKpId(UUID targetKpId);

    /**
     * 判断指定类型的关系是否已存在。
     *
     * @param sourceKpId   源知识点 ID
     * @param targetKpId   目标知识点 ID
     * @param relationType 关系类型
     * @return true 如果已存在
     */
    boolean existsBySourceKpIdAndTargetKpIdAndRelationType(
            UUID sourceKpId, UUID targetKpId, RelationType relationType);
}
