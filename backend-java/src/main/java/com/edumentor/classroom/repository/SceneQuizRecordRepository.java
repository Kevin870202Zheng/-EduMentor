package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.SceneQuizRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 课堂 Quiz 作答记录 Repository。
 *
 * @author EduMentor Team
 */
@Repository
public interface SceneQuizRecordRepository extends JpaRepository<SceneQuizRecord, UUID> {

    /**
     * 查询学生在某个场景的所有作答记录。
     *
     * @param studentId 学生 ID
     * @param sceneId   场景 ID
     * @return 作答记录列表
     */
    List<SceneQuizRecord> findByStudentIdAndSceneIdOrderByCreatedAtAsc(UUID studentId, UUID sceneId);

    /**
     * 查询学生在某个课堂的所有作答记录。
     *
     * @param studentId   学生 ID
     * @param classroomId 课堂 ID（通过场景关联）
     * @return 作答记录列表
     */
    List<SceneQuizRecord> findByStudentIdAndSceneIdIn(UUID studentId, List<UUID> sceneIds);

    /**
     * 统计学生在某个知识点的课堂 Quiz 正确率。
     *
     * @param studentId        学生 ID
     * @param knowledgePointId 知识点 ID
     * @return 作答记录列表
     */
    List<SceneQuizRecord> findByStudentIdAndKnowledgePointId(UUID studentId, UUID knowledgePointId);

    /**
     * 统计学生在某个场景的作答次数。
     *
     * @param studentId 学生 ID
     * @param sceneId   场景 ID
     * @return 作答次数
     */
    long countByStudentIdAndSceneId(UUID studentId, UUID sceneId);
}
