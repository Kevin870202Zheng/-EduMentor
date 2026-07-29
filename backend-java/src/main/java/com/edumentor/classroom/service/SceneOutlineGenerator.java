package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.OutlineResponse;
import com.edumentor.classroom.dto.SceneOutline;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.engine.llm.LLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 场景大纲生成器 — 课堂生成管线第一阶段。
 * <p>
 * 输入：知识点信息 + 难度等级
 * 输出：SceneOutline[] 教学场景大纲列表
 * </p>
 * <p>
 * 使用高质量的 prompt 模板（源自 OpenMAIC 的设计理念），
 * 包含详细的课程设计原则、场景类型说明、格式约束和完整示例，
 * 确保 LLM 输出结构化的、高教学质量的场景大纲。
 * </p>
 */
@Component
public class SceneOutlineGenerator {

    private static final Logger log = LoggerFactory.getLogger(SceneOutlineGenerator.class);

    private final LLMService llmService;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptLoader;

    public SceneOutlineGenerator(LLMService llmService,
                                 KnowledgePointRepository knowledgePointRepository,
                                 ObjectMapper objectMapper,
                                 PromptTemplateLoader promptLoader) {
        this.llmService = llmService;
        this.knowledgePointRepository = knowledgePointRepository;
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
    }

    /**
     * 根据知识点生成场景大纲。
     */
    public List<SceneOutline> generate(UUID knowledgePointId, int difficulty) {
        KnowledgePoint kp = knowledgePointRepository.findById(knowledgePointId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + knowledgePointId));
        return generate(kp, difficulty);
    }

    /**
     * 根据知识点实体生成场景大纲。
     */
    public List<SceneOutline> generate(KnowledgePoint kp, int difficulty) {
        return generate(kp, difficulty, null);
    }

    /**
     * 根据知识点实体生成场景大纲（带增强上下文）。
     *
     * @param kp           知识点实体
     * @param difficulty   难度等级
     * @param extraContext 额外上下文（聚合内容、教材原文、参考习题等），可为 null
     */
    public List<SceneOutline> generate(KnowledgePoint kp, int difficulty, Map<String, String> extraContext) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(kp, difficulty, extraContext);

        try {
            String raw = llmService.askStructured(systemPrompt, userPrompt, String.class, "outline-response");
            OutlineResponse response = objectMapper.readValue(raw, OutlineResponse.class);

            List<SceneOutline> outlines = response.getScenes();
            if (outlines == null || outlines.isEmpty()) {
                log.warn("LLM returned empty outlines for KP {}, using defaults", kp.getId());
                return createDefaultOutlines(kp, difficulty);
            }

            for (int i = 0; i < outlines.size(); i++) {
                SceneOutline o = outlines.get(i);
                o.setOrder(i + 1);
            }

            log.info("Generated {} outlines for KP '{}' with title: {}",
                    outlines.size(), kp.getName(), response.getClassroomTitle());
            return outlines;

        } catch (Exception e) {
            log.error("Failed to generate scene outlines for KP {}: {}", kp.getId(), e.getMessage());
            return createDefaultOutlines(kp, difficulty);
        }
    }

    private String buildSystemPrompt() {
        return promptLoader.loadRaw("outline-system.md");
    }

    private String buildUserPrompt(KnowledgePoint kp, int difficulty) {
        return buildUserPrompt(kp, difficulty, null);
    }

    private String buildUserPrompt(KnowledgePoint kp, int difficulty, Map<String, String> extraContext) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("knowledgePointName", kp.getName() != null ? kp.getName() : "");
        vars.put("knowledgePointDescription", kp.getDescription() != null ? kp.getDescription() : "");
        vars.put("knowledgePointContent", kp.getContent() != null ? kp.getContent() : "");
        vars.put("difficulty", String.valueOf(difficulty));

        // 课程名称（修复 UUID 问题：优先使用 extraContext 中的 courseName）
        String courseName = kp.getCourseId() != null ? kp.getCourseId().toString() : "";
        if (extraContext != null && extraContext.containsKey("courseName")
                && extraContext.get("courseName") != null
                && !extraContext.get("courseName").isEmpty()
                && !extraContext.get("courseName").equals("未知课程")) {
            courseName = extraContext.get("courseName");
        }
        vars.put("courseName", courseName);

        // 增强上下文：聚合内容、教材原文、参考习题、章节结构
        vars.put("aggregatedContent", extraContext != null && extraContext.containsKey("aggregatedContent")
                ? extraContext.get("aggregatedContent") : "");
        vars.put("textbookExcerpt", extraContext != null && extraContext.containsKey("textbookExcerpt")
                ? extraContext.get("textbookExcerpt") : "");
        vars.put("referenceQuestions", extraContext != null && extraContext.containsKey("referenceQuestions")
                ? extraContext.get("referenceQuestions") : "");
        vars.put("chapterStructure", extraContext != null && extraContext.containsKey("chapterStructure")
                ? extraContext.get("chapterStructure") : "");

        return promptLoader.load("outline-user.md", vars);
    }

    /**
     * 默认大纲（LLM调用失败时的降级方案）。
     */
    private List<SceneOutline> createDefaultOutlines(KnowledgePoint kp, int difficulty) {
        List<SceneOutline> outlines = new ArrayList<>();
        var slideType = com.edumentor.classroom.entity.enums.SceneType.slide;
        var quizType = com.edumentor.classroom.entity.enums.SceneType.quiz;
        var reviewType = com.edumentor.classroom.entity.enums.SceneType.review;

        outlines.add(SceneOutline.builder().type(slideType)
                .title("问题引入：" + kp.getName())
                .description("通过一个实际问题引入" + kp.getName() + "的概念")
                .keyPoints(List.of("实际问题展示", "概念初步认识"))
                .teachingObjective("激发学习兴趣，建立初步认知")
                .estimatedDurationSeconds(90).order(1).build());

        outlines.add(SceneOutline.builder().type(slideType)
                .title("核心概念讲解")
                .description("系统讲解" + kp.getName() + "的核心概念和原理")
                .keyPoints(List.of("定义与公式", "原理推导", "关键要点"))
                .teachingObjective("掌握核心概念")
                .estimatedDurationSeconds(120).order(2).build());

        outlines.add(SceneOutline.builder().type(quizType)
                .title("随堂练习")
                .description("通过练习检验对" + kp.getName() + "的理解")
                .keyPoints(List.of("基础练习", "应用练习"))
                .teachingObjective("验证理解程度")
                .estimatedDurationSeconds(90).order(3).build());

        outlines.add(SceneOutline.builder().type(slideType)
                .title("常见错误与注意事项")
                .description("分析学习" + kp.getName() + "时的常见错误")
                .keyPoints(List.of("易错点分析", "注意事项"))
                .teachingObjective("避免常见错误")
                .estimatedDurationSeconds(100).order(4).build());

        outlines.add(SceneOutline.builder().type(reviewType)
                .title("总结与回顾")
                .description("总结本节课的核心内容，建立知识联系")
                .keyPoints(List.of("知识回顾", "前置知识链接"))
                .teachingObjective("巩固所学，建立知识网络")
                .estimatedDurationSeconds(60).order(5).build());

        return outlines;
    }
}
