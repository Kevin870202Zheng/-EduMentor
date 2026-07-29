package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 教学场景 Repository。
 *
 * @author EduMentor Team
 */
@Repository
public interface SceneRepository extends JpaRepository<Scene, UUID> {

    /**
     * 按课堂 ID 查询所有场景（按顺序排列）。
     *
     * @param classroomId 课堂 ID
     * @return 场景列表
     */
    List<Scene> findByClassroomIdOrderByOrderIndexAsc(UUID classroomId);

    /**
     * 按课堂 ID 查询场景数量。
     *
     * @param classroomId 课堂 ID
     * @return 场景数量
     */
    long countByClassroomId(UUID classroomId);

    /**
     * 按课堂 ID 删除所有场景。
     *
     * @param classroomId 课堂 ID
     */
    void deleteByClassroomId(UUID classroomId);
}
