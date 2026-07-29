package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.ClassroomProgress;
import com.edumentor.classroom.entity.enums.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 课堂进度 Repository。
 *
 * @author EduMentor Team
 */
@Repository
public interface ClassroomProgressRepository extends JpaRepository<ClassroomProgress, UUID> {

    /**
     * 查询学生某个课堂的进度。
     *
     * @param studentId   学生 ID
     * @param classroomId 课堂 ID
     * @return 可选进度记录
     */
    Optional<ClassroomProgress> findByStudentIdAndClassroomId(UUID studentId, UUID classroomId);

    /**
     * 查询学生所有课堂进度。
     *
     * @param studentId 学生 ID
     * @return 进度列表
     */
    List<ClassroomProgress> findByStudentIdOrderByLastAccessedAtDesc(UUID studentId);

    /**
     * 按状态查询学生课堂进度。
     *
     * @param studentId 学生 ID
     * @param status    进度状态
     * @return 进度列表
     */
    List<ClassroomProgress> findByStudentIdAndStatus(UUID studentId, ProgressStatus status);

    /**
     * 查询课堂的所有学生进度。
     *
     * @param classroomId 课堂 ID
     * @return 进度列表
     */
    List<ClassroomProgress> findByClassroomId(UUID classroomId);

    /**
     * 统计课堂中学生完成数量。
     *
     * @param classroomId 课堂 ID
     * @param status      进度状态
     * @return 学生数量
     */
    long countByClassroomIdAndStatus(UUID classroomId, ProgressStatus status);
}
