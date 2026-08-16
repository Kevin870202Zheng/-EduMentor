package com.edumentor.learningpath.repository;

import com.edumentor.learningpath.entity.PathTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 路径模板 Repository — 提供预设模板的数据访问操作。
 *
 * @author EduMentor Team
 */
@Repository
public interface PathTemplateRepository extends JpaRepository<PathTemplate, UUID> {

    /**
     * 查询课程下可见的模板（按展示顺序排序）。
     *
     * @param courseId 课程 ID
     * @return 可见模板列表
     */
    List<PathTemplate> findByCourseIdAndIsVisibleTrueOrderBySortOrderAsc(UUID courseId);

    /**
     * 查询课程下所有模板。
     *
     * @param courseId 课程 ID
     * @return 模板列表
     */
    List<PathTemplate> findByCourseIdOrderBySortOrderAsc(UUID courseId);

    /**
     * 按课程与模板代码查询模板。
     *
     * @param courseId 课程 ID
     * @param code     模板代码
     * @return 模板（可能为空）
     */
    Optional<PathTemplate> findByCourseIdAndCode(UUID courseId, String code);
}
