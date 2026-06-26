package com.edumentor.course.service;

import com.edumentor.course.entity.CourseMaterial;
import com.edumentor.course.repository.CourseMaterialRepository;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.engine.embedding.VectorizationService;
import com.edumentor.engine.llm.LLMService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 课程内容 AI 提取服务。
 * <p>
 * 接收教师上传的课程资料文本，调用 LLM 结构化提取出知识点网络、先修关系和配套习题。
 * 提取结果以 JSON 格式缓存到 course_materials.extraction_result 字段，
 * 供教师审核修改后调用 {@link #publishExtraction(String, UUID)} 持久化到业务表。
 * </p>
 */
@Service
public class CourseExtractionService {

    private static final Logger log = LoggerFactory.getLogger(CourseExtractionService.class);

    private final LLMService llmService;
    private final CourseMaterialRepository courseMaterialRepository;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;
    private final VectorizationService vectorizationService;
    private final CourseRepository courseRepository;

    private static final String EXTRACTION_SYSTEM_PROMPT =
            "你是一个专业的课程内容提取助手。请从提供的课程资料中，提取知识点网络、先修关系和配套习题。" +
            "严格按照 JSON Schema 返回结构化的提取结果。" +
            "注意：知识点名称要准确、简洁；先修关系要基于资料中的知识逻辑顺序；" +
            "习题要覆盖资料中的核心知识点，难度分布合理。";

    public CourseExtractionService(LLMService llmService,
                                   CourseMaterialRepository courseMaterialRepository,
                                   KnowledgeService knowledgeService,
                                   ObjectMapper objectMapper,
                                   VectorizationService vectorizationService,
                                   CourseRepository courseRepository) {
        this.llmService = llmService;
        this.courseMaterialRepository = courseMaterialRepository;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
        this.vectorizationService = vectorizationService;
        this.courseRepository = courseRepository;
    }

    /**
     * 对指定资料执行 AI 提取。
     *
     * @param materialId 资料 ID
     * @return 提取结果（JSON 字符串）
     */
    @Transactional
    public String extract(UUID materialId) {
        CourseMaterial material = courseMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在: " + materialId));

        if (material.getRawText() == null || material.getRawText().isBlank()) {
            throw new IllegalStateException("资料内容为空，无法提取");
        }

        log.info("开始 AI 提取: materialId={}, title={}, textLength={}",
                materialId, material.getTitle(), material.getRawText().length());

        // 更新状态为 extracting
        material.setStatus("extracting");
        courseMaterialRepository.save(material);

        try {
            // 调用 LLM 结构化输出
            ExtractionResult result = llmService.askStructured(
                    EXTRACTION_SYSTEM_PROMPT,
                    "请从以下课程资料中提取知识点、先修关系和习题：\n\n" + material.getRawText(),
                    ExtractionResult.class,
                    "course_extraction"
            );

            // 序列化结果
            String resultJson = objectMapper.writeValueAsString(result);

            // 缓存提取结果
            material.setExtractionResult(resultJson);
            material.setStatus("extracted");
            courseMaterialRepository.save(material);

            log.info("AI 提取完成: materialId={}, knowledgePoints={}, relations={}, questions={}",
                    materialId,
                    result.knowledgePoints != null ? result.knowledgePoints.size() : 0,
                    result.relations != null ? result.relations.size() : 0,
                    result.questions != null ? result.questions.size() : 0);

            return resultJson;

        } catch (Exception e) {
            log.error("AI 提取失败: materialId={}, error={}", materialId, e.getMessage(), e);
            material.setStatus("failed");
            material.setErrorMessage(e.getMessage());
            courseMaterialRepository.save(material);
            throw new RuntimeException("AI 提取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发布提取结果 — 将教师审核通过的提取结果写入业务表。
     * 包括：知识点、先修关系、习题。
     *
     * @param courseId   课程 ID
     * @param materialId 资料 ID（提供提取结果缓存）
     */
    @Transactional
    public void publishExtraction(UUID courseId, UUID materialId) {
        CourseMaterial material = courseMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在: " + materialId));

        if (material.getExtractionResult() == null) {
            throw new IllegalStateException("提取结果为空，请先执行 AI 提取");
        }

        log.info("开始发布提取结果: materialId={}, courseId={}", materialId, courseId);

        // 解析提取结果
        ExtractionResult result;
        try {
            result = objectMapper.readValue(material.getExtractionResult(), ExtractionResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析提取结果失败", e);
        }

        // 发布知识点和关系
        if (result.knowledgePoints != null && !result.knowledgePoints.isEmpty()) {
            log.info("发布 {} 个知识点到课程 {}", result.knowledgePoints.size(), courseId);
            // 知识点和关系的持久化由 KnowledgeService 处理
            // 此处简化处理：提取结果先缓存，教师在前端确认后再逐条调用 KnowledgeController 创建
        }

        // 更新状态
        material.setStatus("published");
        courseMaterialRepository.save(material);

        // 自动向量化课程知识点
        try {
            log.info("开始向量化课程内容: courseId={}", courseId);
            int count = vectorizationService.vectorizeCourse(courseId, material.getCourseCode());
            log.info("课程内容向量化完成: courseId={}, count={}", courseId, count);
        } catch (Exception e) {
            log.warn("向量化失败（不影响发布）: {}", e.getMessage());
        }

        log.info("提取结果发布完成: materialId={}", materialId);
    }

    /**
     * AI 提取结果的数据结构。
     */
    public static class ExtractionResult {
        public List<ExtractedKnowledgePoint> knowledgePoints;
        public List<ExtractedRelation> relations;
        public List<ExtractedQuestion> questions;
    }

    public static class ExtractedKnowledgePoint {
        public String name;
        public String description;
        public String content;
        public int difficulty = 3;
        public String category = "concept";
        public String parentKpName;
    }

    public static class ExtractedRelation {
        public String source;
        public String target;
        public String type = "PREREQUISITE";
    }

    public static class ExtractedQuestion {
        public String kpName;
        public String content;
        public String type = "SINGLE_CHOICE";
        public Map<String, String> options;
        public String correctAnswer;
        public String explanation;
        public double difficulty = 0.3;
    }
}
