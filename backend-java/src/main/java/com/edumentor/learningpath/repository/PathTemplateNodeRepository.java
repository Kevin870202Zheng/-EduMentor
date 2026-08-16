package com.edumentor.learningpath.repository;

import com.edumentor.learningpath.entity.PathTemplateNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 路径模板节点 Repository — 提供静态模板节点的数据访问操作。
 *
 * @author EduMentor Team
 */
@Repository
public interface PathTemplateNodeRepository extends JpaRepository<PathTemplateNode, UUID> {

    /**
     * 按模板 ID 查询节点（按顺序排序）。
     *
     * @param templateId 模板 ID
     * @return 模板节点列表
     */
    List<PathTemplateNode> findByTemplateIdOrderByOrderIndexAsc(UUID templateId);

    /**
     * 删除模板的全部节点。
     *
     * @param templateId 模板 ID
     */
    void deleteByTemplateId(UUID templateId);

    /**
     * 统计模板节点数。
     *
     * @param templateId 模板 ID
     * @return 节点数
     */
    long countByTemplateId(UUID templateId);
}
