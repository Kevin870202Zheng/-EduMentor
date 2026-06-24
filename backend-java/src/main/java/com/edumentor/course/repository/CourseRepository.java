package com.edumentor.course.repository;

import com.edumentor.course.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 课程 Repository — 提供课程实体的数据访问操作。
 * <p>
 * 继承 {@link JpaRepository} 获得基础 CRUD 功能。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    /**
     * 按学科分类分页查询课程。
     *
     * @param subject  学科名称
     * @param pageable 分页参数
     * @return 分页课程结果
     */
    Page<Course> findBySubject(String subject, Pageable pageable);

    /**
     * 按课程名称模糊搜索（不区分大小写）。
     *
     * @param name     课程名称关键字
     * @param pageable 分页参数
     * @return 分页匹配课程
     */
    Page<Course> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * 查询所有已发布的课程（对学生可见）。
     *
     * @param pageable 分页参数
     * @return 分页已发布课程
     */
    Page<Course> findByIsPublishedTrue(Pageable pageable);

    /**
     * 统计指定用户创建的课程数量。
     *
     * @param createdBy 创建人 UUID
     * @return 课程数量
     */
    long countByCreatedBy(UUID createdBy);

    /**
     * 判断是否已存在同名课程（用于创建时校验）。
     *
     * @param name 课程名称
     * @return true 如果已存在
     */
    boolean existsByName(String name);
}
