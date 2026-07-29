package com.edumentor.classroom.service;

import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 习题检索器 — 从 questions 表中检索与指定章节相关的练习题。
 * <p>
 * 用于在课堂生成时作为 prompt 上下文中的参考习题，帮助 LLM 了解
 * 题目难度和风格，生成与课程题库一致的课堂测验题。
 * </p>
 */
@Component
public class QuestionProvider {

    private static final Logger log = LoggerFactory.getLogger(QuestionProvider.class);

    /** 最多返回的参考习题数量 */
    private static final int MAX_REFERENCE_QUESTIONS = 10;

    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository knowledgePointRepository;

    public QuestionProvider(QuestionRepository questionRepository,
                            KnowledgePointRepository knowledgePointRepository) {
        this.questionRepository = questionRepository;
        this.knowledgePointRepository = knowledgePointRepository;
    }

    /**
     * 获取指定章节点下的参考习题列表。
     * <p>
     * 递归查找该章下的所有 LEAF 子节点，然后按知识点 ID 查询习题，
     * 随机抽样最多 MAX_REFERENCE_QUESTIONS 道。
     * </p>
     *
     * @param chapterId CHAPTER 节点的 ID
     * @return 格式化后的参考习题文本
     */
    public String getReferenceQuestions(UUID chapterId) {
        try {
            // 递归查找所有 LEAF 子节点
            List<UUID> leafIds = findAllLeafIds(chapterId);

            if (leafIds.isEmpty()) {
                log.info("No leaf knowledge points found under chapter: {}", chapterId);
                return "";
            }

            // 按知识点 ID 查询习题
            List<Question> allQuestions = new ArrayList<>();
            for (UUID leafId : leafIds) {
                List<Question> questions = questionRepository.findByKnowledgePointId(leafId);
                allQuestions.addAll(questions);
            }

            if (allQuestions.isEmpty()) {
                log.info("No questions found for chapter: {}", chapterId);
                return "";
            }

            // 随机抽样（但确保每次生成一样 —— 用 ID 排序后取前 N 个）
            Collections.sort(allQuestions, (a, b) -> a.getId().compareTo(b.getId()));
            List<Question> sampled = allQuestions.subList(0, Math.min(allQuestions.size(), MAX_REFERENCE_QUESTIONS));

            // 格式化为可读文本
            StringBuilder sb = new StringBuilder();
            sb.append("## 本章参考习题\n\n");
            sb.append("以下习题来自本章的题库，供你参考题目难度和风格：\n\n");

            char[] labels = {'A', 'B', 'C', 'D', 'E', 'F'};
            for (int i = 0; i < sampled.size(); i++) {
                Question q = sampled.get(i);
                sb.append(i + 1).append(". ").append(q.getContent()).append("\n");

                // 格式化选项
                JsonNode options = q.getOptions();
                if (options != null && options.isArray()) {
                    for (int j = 0; j < options.size() && j < labels.length; j++) {
                        JsonNode opt = options.get(j);
                        String optText = opt.isTextual() ? opt.asText()
                                : (opt.has("label") ? opt.get("label").asText() : opt.toString());
                        sb.append("   ").append(labels[j]).append(". ").append(optText).append("\n");
                    }
                }

                sb.append("   [正确答案: ").append(q.getCorrectAnswer()).append("]\n");
                if (q.getExplanation() != null && !q.getExplanation().isEmpty()) {
                    sb.append("   解析：").append(q.getExplanation()).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.error("Failed to retrieve reference questions for chapter: {}", chapterId, e);
            return "";
        }
    }

    /**
     * 递归查找所有 LEAF 类型的子节点 ID。
     */
    private List<UUID> findAllLeafIds(UUID parentId) {
        List<UUID> result = new ArrayList<>();
        List<KnowledgePoint> children = knowledgePointRepository.findByParentKpId(parentId);
        for (KnowledgePoint child : children) {
            if ("LEAF".equals(child.getType())) {
                result.add(child.getId());
            }
            // 递归查找（SECTION 下可能还有 LEAF）
            result.addAll(findAllLeafIds(child.getId()));
        }
        return result;
    }
}
