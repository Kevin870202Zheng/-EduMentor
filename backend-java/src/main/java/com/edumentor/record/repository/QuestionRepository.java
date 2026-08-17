package com.edumentor.record.repository;

import com.edumentor.record.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 题目数据访问层。
 *
 * @author EduMentor Team
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    /**
     * 检查是否存在特定知识点下的相同题目。
     *
     * @param content          题目内容
     * @param knowledgePointId 知识点 ID
     * @return true 如果已存在
     */
    boolean existsByContentAndKnowledgePointId(String content, UUID knowledgePointId);

    /**
     * 按课程 ID 查询所有题目。
     *
     * @param courseId 课程 ID
     * @return 题目列表
     */
    List<Question> findByCourseId(UUID courseId);

    /**
     * 按知识点 ID 查询所有题目。
     *
     * @param knowledgePointId 知识点 ID
     * @return 题目列表
     */
    List<Question> findByKnowledgePointId(UUID knowledgePointId);

    /** 统计某知识点下的题目数（用于课后仲裁准入：无练习题直接放行） */
    long countByKnowledgePointId(UUID knowledgePointId);
}
