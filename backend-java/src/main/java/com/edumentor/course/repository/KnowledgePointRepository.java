package com.edumentor.course.repository;

import com.edumentor.course.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 知识点 Repository — 提供知识点实体的数据访问操作。
 * <p>
 * 核心查询包括：按课程查询知识点、按父知识点查询子节点、批量查询等。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Repository
public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, UUID> {

    /**
     * 按课程 ID 查询所有知识点（按排序序号升序）。
     *
     * @param courseId 课程 ID
     * @return 知识点列表（按 orderIndex 升序）
     */
    List<KnowledgePoint> findByCourseIdOrderByOrderIndexAsc(UUID courseId);

    /**
     * 按课程 ID 查询所有知识点。
     *
     * @param courseId 课程 ID
     * @return 知识点列表
     */
    List<KnowledgePoint> findByCourseId(UUID courseId);

    /**
     * 按课程 ID 统计知识点数量。
     *
     * @param courseId 课程 ID
     * @return 知识点数量
     */
    long countByCourseId(UUID courseId);

    /**
     * 查询指定父知识点下的所有子知识点。
     *
     * @param parentKpId 父知识点 ID（使用 null 查询顶层节点时需配合派生方法）
     * @return 子知识点列表（按 orderIndex 升序）
     */
    List<KnowledgePoint> findByParentKpId(UUID parentKpId);

    /**
     * 根据 ID 列表批量查询知识点。
     *
     * @param ids 知识点 ID 集合
     * @return 匹配的知识点列表
     */
    List<KnowledgePoint> findByIdIn(List<UUID> ids);
}
