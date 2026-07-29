package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.PracticeQuestionDto;
import com.edumentor.classroom.entity.Scene;
import com.edumentor.classroom.entity.SceneAction;
import com.edumentor.classroom.entity.SceneQuizRecord;
import com.edumentor.classroom.entity.enums.ActionType;
import com.edumentor.classroom.repository.SceneActionRepository;
import com.edumentor.classroom.repository.SceneQuizRecordRepository;
import com.edumentor.classroom.repository.SceneRepository;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.engine.llm.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 课后练习服务 — 基于课堂Quiz结果生成变体练习题。
 * <p>
 * 流程：获取课堂中答错的Quiz → LLM生成同类变体题 → 关联课堂讲解片段
 * </p>
 */
@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final SceneQuizRecordRepository quizRecordRepository;
    private final SceneRepository sceneRepository;
    private final SceneActionRepository sceneActionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public PracticeService(SceneQuizRecordRepository quizRecordRepository,
                           SceneRepository sceneRepository,
                           SceneActionRepository sceneActionRepository,
                           KnowledgePointRepository knowledgePointRepository,
                           LLMService llmService,
                           ObjectMapper objectMapper) {
        this.quizRecordRepository = quizRecordRepository;
        this.sceneRepository = sceneRepository;
        this.sceneActionRepository = sceneActionRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    /**
     * 基于课堂Quiz结果生成课后练习。
     *
     * @param studentId     学生ID
     * @param classroomId   课堂ID
     * @param questionCount 期望的题目数量
     * @return 练习题列表
     */
    public List<PracticeQuestionDto> generatePractice(
            UUID studentId, UUID classroomId, int questionCount) {

        // 1. 获取课堂内所有场景
        List<Scene> scenes = sceneRepository.findByClassroomIdOrderByOrderIndexAsc(classroomId);
        List<UUID> sceneIds = scenes.stream().map(Scene::getId).toList();

        // 2. 获取学生在该课堂的所有Quiz记录
        List<SceneQuizRecord> allRecords = quizRecordRepository
                .findByStudentIdAndSceneIdIn(studentId, sceneIds);

        // 3. 分离答对和答错的记录
        List<SceneQuizRecord> wrongRecords = allRecords.stream()
                .filter(r -> Boolean.FALSE.equals(r.getIsCorrect()))
                .collect(Collectors.toList());
        List<SceneQuizRecord> correctRecords = allRecords.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsCorrect()))
                .collect(Collectors.toList());

        List<PracticeQuestionDto> questions = new ArrayList<>();

        // 4. 优先从答错的Quiz生成变体题
        for (SceneQuizRecord wrong : wrongRecords) {
            if (questions.size() >= questionCount) break;
            PracticeQuestionDto q = generateVariantQuestion(wrong);
            if (q != null) {
                questions.add(q);
            }
        }

        // 5. 如果题目不够，从所有场景中抽取未答过的Quiz或补充题
        if (questions.size() < questionCount) {
            for (Scene scene : scenes) {
                if (questions.size() >= questionCount) break;

                // 检查该场景是否已有答题记录
                boolean hasRecord = allRecords.stream()
                        .anyMatch(r -> r.getSceneId().equals(scene.getId()));
                if (hasRecord) continue;

                // 从场景内容中提取Quiz数据生成练习题
                PracticeQuestionDto q = extractQuizFromScene(scene, studentId);
                if (q != null) {
                    questions.add(q);
                }
            }
        }

        // 6. 如果还是不够，从答对的题目生成类似的巩固题
        if (questions.size() < questionCount && !correctRecords.isEmpty()) {
            for (SceneQuizRecord correct : correctRecords) {
                if (questions.size() >= questionCount) break;
                PracticeQuestionDto q = generateVariantQuestion(correct);
                if (q != null) {
                    questions.add(q);
                }
            }
        }

        log.info("Generated {} practice questions for student={}, classroom={}",
                questions.size(), studentId, classroomId);
        return questions;
    }

    /**
     * 基于Quiz记录生成变体题。
     */
    private PracticeQuestionDto generateVariantQuestion(SceneQuizRecord record) {
        try {
            // 获取场景信息
            Scene scene = sceneRepository.findById(record.getSceneId()).orElse(null);
            if (scene == null) return null;

            // 解析原始Quiz数据
            Map<String, Object> quizData = objectMapper.readValue(
                    record.getQuizData(), new TypeReference<Map<String, Object>>() {});

            // 使用LLM生成变体题
            String prompt = buildVariantPrompt(quizData, scene.getTitle());
            String systemPrompt = "你是一位教育出题专家，请基于原始题目生成一道同知识点、同难度但不同形式的变体练习题。"
                    + "请严格按照JSON格式返回，不要包含markdown代码块标记。";

            String raw = llmService.askStructured(systemPrompt, prompt, String.class, "practice-question");
            Map<String, Object> variant = objectMapper.readValue(raw, new TypeReference<>() {});

            // 获取关联的知识点
            String kpId = null;
            String kpName = null;
            if (record.getKnowledgePointId() != null) {
                KnowledgePoint kp = knowledgePointRepository.findById(record.getKnowledgePointId()).orElse(null);
                if (kp != null) {
                    kpId = kp.getId().toString();
                    kpName = kp.getName();
                }
            }

            return PracticeQuestionDto.builder()
                    .id(UUID.randomUUID().toString())
                    .questionContent((String) variant.getOrDefault("question", ""))
                    .options(objectMapper.convertValue(variant.getOrDefault("options", new String[0]), String[].class))
                    .correctIndex((Integer) variant.getOrDefault("correctIndex", 0))
                    .explanation((String) variant.getOrDefault("explanation", ""))
                    .knowledgePointId(kpId)
                    .knowledgePointName(kpName)
                    .relatedSceneId(scene.getId().toString())
                    .relatedSceneTitle(scene.getTitle())
                    .difficulty(String.valueOf(scene.getEstimatedDurationSeconds() != null
                            && scene.getEstimatedDurationSeconds() > 120 ? "medium" : "easy"))
                    .build();

        } catch (Exception e) {
            log.warn("Failed to generate variant question: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从场景中提取Quiz数据生成练习题。
     */
    private PracticeQuestionDto extractQuizFromScene(Scene scene, UUID studentId) {
        try {
            Map<String, Object> content = objectMapper.readValue(
                    scene.getContentJson(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actions = (List<Map<String, Object>>) content.get("actions");
            if (actions == null) return null;

            for (Map<String, Object> action : actions) {
                String type = (String) action.get("type");
                if ("quiz".equals(type)) {
                    String[] options = objectMapper.convertValue(
                            action.get("options"), String[].class);

                    String kpId = null, kpName = null;
                    // 通过课堂获取知识点
                    com.edumentor.classroom.entity.Classroom classroom =
                            new com.edumentor.classroom.entity.Classroom();
                    // 实际上需要从repository获取，这里用简化方式
                    if (scene.getClassroomId() != null) {
                        // 知识点ID会从场景的课堂中获取，这里暂设null
                    }

                    return PracticeQuestionDto.builder()
                            .id(UUID.randomUUID().toString())
                            .questionContent((String) action.get("question"))
                            .options(options)
                            .correctIndex((Integer) action.get("correctIndex"))
                            .explanation((String) action.get("explanation"))
                            .relatedSceneId(scene.getId().toString())
                            .relatedSceneTitle(scene.getTitle())
                            .difficulty("easy")
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract quiz from scene {}: {}", scene.getId(), e.getMessage());
        }
        return null;
    }

    /**
     * 构建变体题提示词。
     */
    private String buildVariantPrompt(Map<String, Object> originalQuiz, String sceneTitle) {
        return String.format("""
                请基于以下原始题目生成一道同知识点、同难度但不同形式的变体练习题。
                
                场景：%s
                原始题目：%s
                原始选项：%s
                正确答案索引：%s
                答案解析：%s
                
                要求：
                1. 知识点相同，但题目形式不同
                2. 选项要有合理的干扰项
                3. 难度与原始题目相当
                4. 提供完整的答案解析
                
                请返回JSON格式：
                {
                  "question": "变体题目",
                  "options": ["A选项", "B选项", "C选项", "D选项"],
                  "correctIndex": 0,
                  "explanation": "答案解析"
                }
                """,
                sceneTitle,
                originalQuiz.getOrDefault("question", ""),
                originalQuiz.getOrDefault("options", "[]"),
                originalQuiz.getOrDefault("correctIndex", 0),
                originalQuiz.getOrDefault("explanation", ""));
    }
}
