package com.edumentor.learningpath.repository;

import com.edumentor.learningpath.entity.LearningPath;
import com.edumentor.learningpath.entity.PathStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学习路径 Repository — 提供学习路径实体的数据访问操作。
 * <p>
 * 核心查询：按学生查询路径列表、按状态筛选、获取活跃路径等。
 * </p>
 *
 * @author EduMentor Team
 */
@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {

    /**
     * 按学生 ID 分页查询其所有学习路径（按创建时间降序）。
     *
     * @param studentId 学生用户 ID
     * @param pageable  分页参数
     * @return 学习路径分页数据
     */
    Page<LearningPath> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    /**
     * 按学生 ID 和路径状态查询。
     *
     * @param studentId 学生用户 ID
     * @param status    路径状态
     * @return 匹配的学习路径列表
     */
    List<LearningPath> findByStudentIdAndStatus(UUID studentId, PathStatus status);

    /**
     * 查询学生在指定状态下的最新一条路径（通常用于获取活跃路径）。
     *
     * @param studentId 学生用户 ID
     * @param status    路径状态
     * @return 学习路径（可能为空）
     */
    Optional<LearningPath> findTopByStudentIdAndStatusOrderByCreatedAtDesc(UUID studentId, PathStatus status);

    /**
     * 按课程 ID 查询所有关联的学习路径。
     *
     * @param courseId 课程 ID
     * @return 学习路径列表
     */
    List<LearningPath> findByCourseId(UUID courseId);

    /**
     * 查询学生在指定课程下的学习路径。
     *
     * @param studentId 学生用户 ID
     * @param courseId  课程 ID
     * @return 学习路径列表
     */
    List<LearningPath> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    /**
     * 统计学生在某状态下的路径数量。
     *
     * @param studentId 学生用户 ID
     * @param status    路径状态
     * @return 路径数量
     */
    long countByStudentIdAndStatus(UUID studentId, PathStatus status);
}
