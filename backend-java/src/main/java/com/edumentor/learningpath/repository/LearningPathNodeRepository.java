package com.edumentor.learningpath.repository;

import com.edumentor.learningpath.entity.LearningPathNode;
import com.edumentor.learningpath.entity.PathNodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学习路径节点 Repository — 提供路径节点的数据访问操作。
 *
 * @author EduMentor Team
 */
@Repository
public interface LearningPathNodeRepository extends JpaRepository<LearningPathNode, UUID> {

    /**
     * 按学习路径 ID 查询所有节点（按排序序号升序）。
     *
     * @param learningPathId 学习路径 ID
     * @return 节点列表
     */
    List<LearningPathNode> findByLearningPathIdOrderByOrderIndexAsc(UUID learningPathId);

    /**
     * 按学习路径 ID 和节点状态查询。
     *
     * @param learningPathId 学习路径 ID
     * @param status         节点状态
     * @return 节点列表
     */
    List<LearningPathNode> findByLearningPathIdAndStatus(UUID learningPathId, PathNodeStatus status);

    /**
     * 查询某个知识点的所有关联节点。
     *
     * @param knowledgePointId 知识点 ID
     * @return 节点列表
     */
    List<LearningPathNode> findByKnowledgePointId(UUID knowledgePointId);

    /**
     * 统计某路径下特定状态的节点数量。
     *
     * @param learningPathId 学习路径 ID
     * @param status         节点状态
     * @return 节点数量
     */
    long countByLearningPathIdAndStatus(UUID learningPathId, PathNodeStatus status);

    /**
     * 查询某路径中下一个待学习的节点（第一个 PENDING 状态的节点）。
     *
     * @param learningPathId 学习路径 ID
     * @param status         目标状态（PENDING）
     * @return 下一个待学节点（可能为空）
     */
    Optional<LearningPathNode> findTopByLearningPathIdAndStatusOrderByOrderIndexAsc(
            UUID learningPathId, PathNodeStatus status);
}
