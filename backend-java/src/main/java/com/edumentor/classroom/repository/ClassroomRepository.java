package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.Classroom;
import com.edumentor.classroom.entity.enums.ClassroomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 课堂 Repository — 提供 Classroom 实体的数据访问操作。
 *
 * @author EduMentor Team
 */
@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    /**
     * 按知识点 ID 查询课堂列表。
     *
     * @param knowledgePointId 知识点 ID
     * @return 课堂列表
     */
    List<Classroom> findByKnowledgePointIdOrderByCreatedAtDesc(UUID knowledgePointId);

    /**
     * 按课程 ID 查询课堂列表。
     *
     * @param courseId 课程 ID
     * @return 课堂列表
     */
    List<Classroom> findByCourseIdOrderByCreatedAtDesc(UUID courseId);

    /**
     * 按课程 ID 和知识点 ID 查询最新已发布课堂。
     *
     * @param courseId         课程 ID
     * @param knowledgePointId 知识点 ID
     * @param status           课堂状态
     * @return 可选课堂
     */
    Optional<Classroom> findFirstByCourseIdAndKnowledgePointIdAndStatusOrderByCreatedAtDesc(
            UUID courseId, UUID knowledgePointId, ClassroomStatus status);

    /**
     * 按课程 ID 统计已发布课堂数量。
     *
     * @param courseId 课程 ID
     * @param status   课堂状态
     * @return 课堂数量
     */
    long countByCourseIdAndStatus(UUID courseId, ClassroomStatus status);

    /**
     * 按知识点 ID 列表查询已发布课堂。
     *
     * @param knowledgePointIds 知识点 ID 列表
     * @param status            课堂状态
     * @return 课堂列表
     */
    List<Classroom> findByKnowledgePointIdInAndStatus(List<UUID> knowledgePointIds, ClassroomStatus status);
}
