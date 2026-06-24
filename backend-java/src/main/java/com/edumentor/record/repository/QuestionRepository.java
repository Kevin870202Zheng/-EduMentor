package com.edumentor.record.repository;

import com.edumentor.record.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
