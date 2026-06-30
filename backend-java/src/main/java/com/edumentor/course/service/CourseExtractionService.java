package com.edumentor.course.service;

import com.edumentor.course.entity.CourseMaterial;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.entity.KnowledgeRelation;
import com.edumentor.course.entity.enums.RelationType;
import com.edumentor.course.repository.*;
import com.edumentor.entity.enums.QuestionType;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.edumentor.engine.embedding.VectorizationService;
import com.edumentor.engine.llm.LLMService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.nio.file.*;
import java.io.IOException;

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

    // 大文件拆分阈值：超过此字数启用硬盘暂存+章节拆分
    private static final int LARGE_FILE_THRESHOLD = 5000;

    private final LLMService llmService;
    private final CourseMaterialRepository courseMaterialRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final KnowledgeRelationRepository knowledgeRelationRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final VectorizationService vectorizationService;
    private final CourseRepository courseRepository;

    private static final String EXTRACTION_SYSTEM_PROMPT =
            "你是一个专业的课程内容提取助手。请从提供的课程资料中，提取知识点网络、先修关系和配套习题。" +
            "严格按照 JSON Schema 返回结构化的提取结果。" +
            "\n知识点要求：" +
            "\n- name: 知识点名称（精确、简洁、完整）" +
            "\n- description: 一句话概述该知识点（必填，不少于20字）" +
            "\n- content: 详细的核心知识要点说明，学生可直接据此学习（必填，不少于150字）" +
            "\n- difficulty: 难度 1-5" +
            "\n- category: 类型（concept/theory/application/case）" +
            "\n\n习题要求（每个知识点至少配1道题）：" +
            "\n- 题型必须多样：单选题(SINGLE_CHOICE)、多选题(MULTIPLE_CHOICE)、判断题(TRUE_FALSE)、填空题(FILL_BLANK)、简答题(SHORT_ANSWER)、论述题(ESSAY)" +
            "\n- 选择题的 options 格式为数组，如 [\"选项A内容\", \"选项B内容\", \"选项C内容\", \"选项D内容\"]" +
            "\n- 论述题(ESSAY)不需要 options，但 correctAnswer 需包含完整的参考答案（不少于200字）" +
            "\n- type 字段使用英文枚举值（如 SINGLE_CHOICE），不要使用中文" +
            "\n- **重要：每道题的 kpName 字段必须填写对应知识点名称，不可为空！**" +
            "\n\n先修关系要求：" +
            "\n- source 和 target 使用知识点名称引用" +
            "\n- type 使用 PREREQUISITE" +
            "\\n\\n重要：只输出JSON结果，不要任何思考过程、推理或解释。";

    /** 习题集专用 prompt：只提取习题，不生成知识点 */
    private static final String EXAM_PAPER_PROMPT =
            "你是一个考试题目解析助手。这是一份考试试卷，请提取其中的所有题目。" +
            "注意：不要生成知识点或先修关系，只提取习题。" +
            "\n每道题的 kpName 字段必须填写该题对应的知识点名称。" +
            "\n- 如果某题明确考核某个知识点（如'法的概念'），kpName 填该知识点名称" +
            "\n- 如果某题涉及多个知识点或是综合性题目，kpName 填'综合'" +
            "\n- 必须基于题目内容判断知识点，不可为空" +
            "\n\n习题要求：" +
            "\n- 题型标记：单选题(SINGLE_CHOICE)、多选题(MULTIPLE_CHOICE)、判断题(TRUE_FALSE)、填空题(FILL_BLANK)、简答题(SHORT_ANSWER)、论述题(ESSAY)" +
            "\n- 选择题的 options 格式为数组，如 [\"选项A内容\", \"选项B内容\", \"选项C内容\", \"选项D内容\"]" +
            "\n- correctAnswer 填写正确答案，论述题需包含完整参考答案" +
            "\n\n严格按以下 JSON 格式输出（knowledgePoints 和 relations 返回空数组）：" +
            "\n{\"knowledgePoints\":[],\"relations\":[],\"questions\":[{\"kpName\":\"知识点名称\",\"content\":\"题目\",\"type\":\"SINGLE_CHOICE\",\"options\":[],\"correctAnswer\":\"\",\"explanation\":\"\",\"difficulty\":3}]}" +
            "\n重要：只输出JSON结果，不要任何思考过程。";

    /**
     * 用 LLM 结构化输出判断文件名是否为试卷/习题集/练习题。
     */
    private boolean isExamPaper(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        try {
            FileClassification fc = llmService.askStructured(
                    "你是一个文件分类器。根据文件名判断是否为考试试卷、习题集或练习题（包括试卷、考题、试题、A/B卷、test、exam、exercise、quiz、单元测试、阶段测试、模拟卷等）。",
                    fileName,
                    FileClassification.class,
                    "file_classify"
            );
            boolean result = fc != null && fc.isExamPaper;
            log.debug("LLM文件名分类: {} → {}", fileName, result);
            return result;
        } catch (Exception e) {
            log.warn("LLM文件名分类失败，使用关键词兜底: {}", e.getMessage());
            String lower = fileName.toLowerCase();
            return lower.contains("试卷") || lower.contains("考题") || lower.contains("试题")
                    || lower.contains("考试") || lower.contains("test") || lower.contains("exam")
                    || lower.contains("quiz") || lower.contains("exercise");
        }
    }

    /** LLM 文件名分类的结构化输出 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileClassification {
        public boolean isExamPaper;
    }

    public CourseExtractionService(LLMService llmService,
                                   CourseMaterialRepository courseMaterialRepository,
                                   KnowledgePointRepository knowledgePointRepository,
                                   KnowledgeRelationRepository knowledgeRelationRepository,
                                   QuestionRepository questionRepository,
                                   ObjectMapper objectMapper,
                                   VectorizationService vectorizationService,
                                   CourseRepository courseRepository) {
        this.llmService = llmService;
        this.courseMaterialRepository = courseMaterialRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.knowledgeRelationRepository = knowledgeRelationRepository;
        this.questionRepository = questionRepository;
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

        ExtractionResult result = null;
        try {
            String rawText = material.getRawText();

            if (rawText.length() > LARGE_FILE_THRESHOLD) {
                log.info("大文件检测: {} 字，启用章节拆分+硬盘临时持久化", rawText.length());
                result = extractLargeFile(materialId, rawText);
            } else {
                // 判断是否为习题集
                boolean isExam = isExamPaper(material.getTitle());
                String prompt = isExam ? EXAM_PAPER_PROMPT : EXTRACTION_SYSTEM_PROMPT;
                String userMsg;
                if (isExam) {
                    log.info("检测到习题集: {}，只提取习题不生成知识点", material.getTitle());
                    userMsg = "请从以下考试试卷中提取所有题目，并为每道题标注对应的知识点名称：\n\n" + rawText;
                } else {
                    userMsg = "请从以下课程资料中提取知识点、先修关系和习题：\n\n" + rawText;
                }
                result = llmService.askStructured(
                        prompt, userMsg, ExtractionResult.class, "course_extraction"
                );
            }

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
            // 即使失败也尝试保存已有提取结果（大文件可能部分成功）
            try {
                if (result != null) {
                    String partialJson = objectMapper.writeValueAsString(result);
                    material.setExtractionResult(partialJson);
                    material.setStatus("extracted");
                    courseMaterialRepository.save(material);
                    log.warn("已保存部分提取结果: materialId={}, 知识点={}, 习题={}",
                            materialId,
                            result.knowledgePoints != null ? result.knowledgePoints.size() : 0,
                            result.questions != null ? result.questions.size() : 0);
                    return partialJson;
                }
            } catch (Exception e2) {
                log.warn("保存部分提取结果也失败: {}", e2.getMessage());
            }
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

        // 1. 增量发布知识点：已存在的不覆盖，不存在的新增
        Map<String, UUID> kpNameToId = new LinkedHashMap<>();
        if (result.knowledgePoints != null) {
            // 获取当前最大 order_index，新知识点追加到末尾
            int currentMaxOrder = (int) knowledgePointRepository.countByCourseId(courseId);
            int newCount = 0;
            int skipCount = 0;

            for (ExtractedKnowledgePoint ekp : result.knowledgePoints) {
                if (ekp.name == null || ekp.name.isBlank()) continue;
                String name = ekp.name.trim();

                // 检查是否已存在同名知识点
                Optional<KnowledgePoint> existing = knowledgePointRepository.findByNameAndCourseId(name, courseId);
                if (existing.isPresent()) {
                    // 已存在 → 保留旧版，不覆盖，记录映射
                    kpNameToId.put(name, existing.get().getId());
                    skipCount++;
                } else {
                    // 不存在 → 创建新知识点，追加到末尾
                    KnowledgePoint kp = new KnowledgePoint();
                    kp.setCourseId(courseId);
                    kp.setName(name);
                    kp.setDescription(ekp.description != null ? ekp.description : "");
                    kp.setContent(ekp.content != null ? ekp.content : "");
                    kp.setDifficulty(ekp.difficulty > 0 ? ekp.difficulty : 3);
                    kp.setOrderIndex(currentMaxOrder + newCount);
                    kp = knowledgePointRepository.save(kp);
                    kpNameToId.put(name, kp.getId());
                    newCount++;
                }
            }
            log.info("知识点发布: 新增={}, 已存在跳过={}", newCount, skipCount);
        }

        // 2. 增量发布先修关系：已存在的跳过
        if (result.relations != null) {
            int relNewCount = 0;
            int relSkipCount = 0;
            for (ExtractedRelation er : result.relations) {
                UUID sourceId = kpNameToId.get(er.source);
                UUID targetId = kpNameToId.get(er.target);
                if (sourceId == null || targetId == null) continue;
                RelationType relType;
                try {
                    relType = RelationType.valueOf(er.type);
                } catch (Exception e) {
                    relType = RelationType.PREREQUISITE;
                }
                // 检查关系是否已存在
                if (!knowledgeRelationRepository.existsBySourceKpIdAndTargetKpIdAndRelationType(sourceId, targetId, relType)) {
                    KnowledgeRelation kr = new KnowledgeRelation();
                    kr.setSourceKpId(sourceId);
                    kr.setTargetKpId(targetId);
                    kr.setRelationType(relType);
                    knowledgeRelationRepository.save(kr);
                    relNewCount++;
                } else {
                    relSkipCount++;
                }
            }
            log.info("先修关系发布: 新增={}, 已存在跳过={}", relNewCount, relSkipCount);
        }

        // 3. 增量发布习题：按内容去重，已存在的跳过
        if (result.questions != null) {
            int qNewCount = 0;
            int qSkipCount = 0;
            for (ExtractedQuestion eq : result.questions) {
                if (eq.content == null || eq.content.isBlank()) continue;
                UUID kpId = null;
                if (eq.kpName != null) kpId = kpNameToId.get(eq.kpName.trim());
                if (kpId == null && !kpNameToId.isEmpty()) {
                    kpId = kpNameToId.values().iterator().next();
                }
                // 如果仍然匹配不到知识点，检查或创建"综合"知识点
                if (kpId == null) {
                    String compName = "综合";
                    Optional<KnowledgePoint> compKp = knowledgePointRepository.findByNameAndCourseId(compName, courseId);
                    if (compKp.isPresent()) {
                        kpId = compKp.get().getId();
                    } else {
                        KnowledgePoint kp = new KnowledgePoint();
                        kp.setCourseId(courseId);
                        kp.setName(compName);
                        kp.setDescription("综合知识点，包含综合性习题和跨章节题目");
                        kp.setContent("此知识点包含综合性练习题，涉及多个章节的知识点。");
                        kp.setDifficulty(3);
                        kp.setOrderIndex((int) knowledgePointRepository.countByCourseId(courseId));
                        kp = knowledgePointRepository.save(kp);
                        kpId = kp.getId();
                        log.info("创建综合知识点: id={}", kpId);
                    }
                }
                if (kpId == null) continue;

                // 检查该知识点下是否已有相同内容的习题
                if (questionRepository.existsByContentAndKnowledgePointId(eq.content.trim(), kpId)) {
                    qSkipCount++;
                    continue;
                }

                Question q = new Question();
                q.setKnowledgePointId(kpId);
                q.setCourseId(courseId);
                q.setContent(eq.content);
                q.setCorrectAnswer(toSafeString(eq.correctAnswer, ""));
                q.setExplanation(eq.explanation != null ? eq.explanation : "");

                // 映射题目类型
                String typeStr = eq.type != null ? eq.type.trim() : "";
                QuestionType qType = parseQuestionType(typeStr);
                q.setQuestionType(qType);

                // 处理选项
                if (eq.options != null && qType != QuestionType.ESSAY && qType != QuestionType.SHORT_ANSWER) {
                    JsonNode optNode = parseOptionsToJsonNode(eq.options);
                    q.setOptions(optNode);
                }

                // 解析难度
                if (eq.difficulty != null) {
                    try {
                        int d = Integer.parseInt(eq.difficulty.replaceAll("[^0-9]", ""));
                        q.setDifficulty(Math.max(1, Math.min(5, d)));
                    } catch (Exception e) {
                        q.setDifficulty(3);
                    }
                }

                q.setIsPublished(true);
                questionRepository.save(q);
                qNewCount++;
            }
            log.info("习题发布: 新增={}, 已存在跳过={}", qNewCount, qSkipCount);
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

    // ================================================================
    // 大文件按章节拆分 + 硬盘临时文件 + 分批提取 + 合并
    // ================================================================

    /**
     * 提取大文件：按章节拆分 → 写入临时文件 → 逐批读取 → LLM 提取 → 合并 → 清理。
     */
    private ExtractionResult extractLargeFile(UUID materialId, String rawText) {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "edumentor", "chunks", materialId.toString());

        try {
            // 1. 按章节拆分
            List<ChunkInfo> chunks = splitByChapters(rawText);
            log.info("章节拆分完成: {} 段", chunks.size());

            // 2. 写入临时目录
            Files.createDirectories(tempDir);
            for (int i = 0; i < chunks.size(); i++) {
                ChunkInfo ci = chunks.get(i);
                String safeName = String.format("%03d-%s.txt", i + 1,
                        ci.title.length() > 20 ? ci.title.substring(0, 20) : ci.title);
                Path path = tempDir.resolve(sanitizeFileName(safeName));
                Files.writeString(path, ci.text, java.nio.charset.StandardCharsets.UTF_8);
                ci.path = path;
            }

            // 3. 逐批提取
            ExtractionResult merged = new ExtractionResult();
            merged.knowledgePoints = new ArrayList<>();
            merged.relations = new ArrayList<>();
            merged.questions = new ArrayList<>();

            String chunkPrompt = "这是课程资料的第 {idx}/{total} 部分「{title}」。请从以下内容中提取知识点、先修关系和习题：\n\n";

            for (int i = 0; i < chunks.size(); i++) {
                ChunkInfo ci = chunks.get(i);
                String text = Files.readString(ci.path, java.nio.charset.StandardCharsets.UTF_8);
                log.info("分批提取: {}/{} 「{}」 ({}字)", i + 1, chunks.size(), ci.title, text.length());

                ExtractionResult chunkResult = null;
                try {
                    chunkResult = llmService.askStructured(
                            EXTRACTION_SYSTEM_PROMPT,
                            chunkPrompt.replace("{idx}", String.valueOf(i + 1))
                                    .replace("{total}", String.valueOf(chunks.size()))
                                    .replace("{title}", ci.title) + text,
                            ExtractionResult.class,
                            "course_extraction"
                    );
                } catch (Exception e) {
                    log.warn("第 {}/{} 段「{}」提取失败，跳过该段继续: {}",
                            i + 1, chunks.size(), ci.title, e.getMessage());
                }

                if (chunkResult != null) {
                    merged = mergeResults(merged, chunkResult);
                    log.info("合并后累计: 知识点={}, 关系={}, 习题={}",
                            merged.knowledgePoints.size(), merged.relations.size(), merged.questions.size());
                }

                // 立即释放（让 GC 可以回收）
                text = null;
                chunkResult = null;
            }

            // 检查是否有有效结果
            boolean hasContent = (merged.knowledgePoints != null && !merged.knowledgePoints.isEmpty())
                    || (merged.questions != null && !merged.questions.isEmpty());
            if (!hasContent) {
                throw new RuntimeException("所有段落提取均失败，请检查 LLM 服务状态");
            }

            return merged;

        } catch (IOException e) {
            throw new RuntimeException("大文件处理失败: " + e.getMessage(), e);
        } finally {
            // 4. 清理临时文件
            try {
                if (Files.exists(tempDir)) {
                    try (var walk = Files.walk(tempDir)) {
                        walk.sorted(java.util.Comparator.reverseOrder())
                                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                    }
                }
                log.info("临时文件已清理: {}", tempDir);
            } catch (IOException e) {
                log.warn("清理临时文件失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 按章节结构拆分文本。
     * 优先级：第X章/第X节 > ## 标题 > 一、二、三 > 空行 > 字符数。
     */
    private List<ChunkInfo> splitByChapters(String text) {
        List<ChunkInfo> chunks = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        StringBuilder current = new StringBuilder();
        String currentTitle = "前言";
        int wordCount = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String nextTitle = null;

            // 检测章节标题
            if (line.matches("^第[一二三四五六七八九十百千零]+[章节篇].*")) {
                nextTitle = line;
            } else if (line.startsWith("#") && line.replaceAll("#", "").trim().length() > 0) {
                nextTitle = line.replaceAll("^#+\\s*", "");
            } else if (line.matches("^[一二三四五六七八九十]+[、.．].*")) {
                nextTitle = line;
            } else if (line.matches("^\\(?[一二三四五六七八九十]+\\)?[、.．].*")) {
                nextTitle = line;
            }

            if (nextTitle != null && wordCount > 500) {
                // 保存当前段落
                String content = current.toString().trim();
                if (!content.isEmpty()) {
                    chunks.add(new ChunkInfo(currentTitle, content));
                }
                current = new StringBuilder();
                currentTitle = nextTitle;
                wordCount = 0;
            }

            current.append(lines[i]).append("\n");
            wordCount += lines[i].length();

            // 如果某一段过长（超过 4000 字），强制分割
            if (wordCount > 4000 && wordCount > 0) {
                String content = current.toString().trim();
                if (!content.isEmpty()) {
                    chunks.add(new ChunkInfo(currentTitle, content));
                }
                current = new StringBuilder();
                currentTitle = currentTitle + "（续）";
                wordCount = 0;
            }
        }

        // 最后一段
        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            chunks.add(new ChunkInfo(currentTitle, remaining));
        }

        return chunks;
    }

    /** 章节块信息 */
    private static class ChunkInfo {
        String title;
        String text;
        Path path;

        ChunkInfo(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    /** 清理文件名（去除不安全字符） */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 合并两个提取结果（去重）。
     */
    private ExtractionResult mergeResults(ExtractionResult merged, ExtractionResult chunk) {
        if (chunk == null) return merged;

        // 合并知识点（按名称去重，不区分大小写）
        if (chunk.knowledgePoints != null) {
            Set<String> existingNames = new HashSet<>();
            for (ExtractedKnowledgePoint ekp : merged.knowledgePoints) {
                if (ekp.name != null) existingNames.add(ekp.name.toLowerCase().trim());
            }
            for (ExtractedKnowledgePoint ekp : chunk.knowledgePoints) {
                if (ekp.name == null || ekp.name.isBlank()) continue;
                String key = ekp.name.toLowerCase().trim();
                if (!existingNames.contains(key)) {
                    merged.knowledgePoints.add(ekp);
                    existingNames.add(key);
                }
            }
        }

        // 合并关系（按 source+target 去重）
        if (chunk.relations != null) {
            Set<String> existingRels = new HashSet<>();
            for (ExtractedRelation er : merged.relations) {
                existingRels.add((er.source + "->" + er.target).toLowerCase());
            }
            for (ExtractedRelation er : chunk.relations) {
                String key = (er.source + "->" + er.target).toLowerCase();
                if (!existingRels.contains(key)) {
                    merged.relations.add(er);
                    existingRels.add(key);
                }
            }
        }

        // 合并习题（按题目内容去重，取最长解释）
        if (chunk.questions != null) {
            Map<String, ExtractedQuestion> existingQs = new LinkedHashMap<>();
            for (ExtractedQuestion eq : merged.questions) {
                if (eq.content != null) existingQs.put(eq.content.trim(), eq);
            }
            for (ExtractedQuestion eq : chunk.questions) {
                if (eq.content == null || eq.content.isBlank()) continue;
                String key = eq.content.trim();
                ExtractedQuestion exist = existingQs.get(key);
                if (exist == null) {
                    merged.questions.add(eq);
                    existingQs.put(key, eq);
                } else if (eq.explanation != null && eq.explanation.length() > (exist.explanation != null ? exist.explanation.length() : 0)) {
                    // 保留解释更详细的版本
                    exist.explanation = eq.explanation;
                }
            }
        }

        return merged;
    }

    /**
     * AI 提取结果的数据结构。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractionResult {
        public List<ExtractedKnowledgePoint> knowledgePoints;
        public List<ExtractedRelation> relations;
        public List<ExtractedQuestion> questions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedKnowledgePoint {
        public String name;
        public String description;
        public String content;
        public int difficulty = 3;
        public String category = "concept";
        public String parentKpName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedRelation {
        public String source;
        public String target;
        public String type = "PREREQUISITE";
    }

    /**
     * 映射题型字符串为 QuestionType 枚举，兼容中文表述。
     */
    private QuestionType parseQuestionType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) return QuestionType.SINGLE_CHOICE;
        String upper = typeStr.toUpperCase();
        // 尝试直接匹配枚举名
        try {
            return QuestionType.valueOf(upper);
        } catch (IllegalArgumentException e) {
            // 兼容中文题型名称
            if (typeStr.contains("单选")) return QuestionType.SINGLE_CHOICE;
            if (typeStr.contains("多选")) return QuestionType.MULTIPLE_CHOICE;
            if (typeStr.contains("判断") || typeStr.contains("对错")) return QuestionType.TRUE_FALSE;
            if (typeStr.contains("填空")) return QuestionType.FILL_BLANK;
            if (typeStr.contains("简答")) return QuestionType.SHORT_ANSWER;
            if (typeStr.contains("论述") || typeStr.contains("作文") || typeStr.contains("问答")) return QuestionType.ESSAY;
            return QuestionType.SINGLE_CHOICE;
        }
    }

    /**
     * 将选项（数组或 Map）统一解析为 JsonNode。
     */
    @SuppressWarnings("unchecked")
    private JsonNode parseOptionsToJsonNode(Object options) {
        Map<String, String> optMap = new LinkedHashMap<>();
        if (options instanceof List) {
            // 数组格式 ["A. 内容", "B. 内容", ...]
            List<Object> list = (List<Object>) options;
            for (int i = 0; i < list.size(); i++) {
                String label = String.valueOf((char) ('A' + i));
                String opt = list.get(i) != null ? list.get(i).toString().trim() : "";
                // 去掉前缀如 "A. " 或 "A、" 或 "A）"
                if (opt.length() > 2 && opt.charAt(1) == '.') opt = opt.substring(2).trim();
                else if (opt.length() > 2 && (opt.charAt(1) == '、' || opt.charAt(1) == '）' || opt.charAt(1) == ')')) opt = opt.substring(2).trim();
                optMap.put(label, opt);
            }
        } else if (options instanceof Map) {
            // Map 格式 {"A": "内容", "B": "内容"}
            Map<Object, Object> map = (Map<Object, Object>) options;
            for (var entry : map.entrySet()) {
                String key = entry.getKey().toString().trim();
                String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                optMap.put(key, val);
            }
        }
        if (optMap.isEmpty()) return null;
        return objectMapper.valueToTree(optMap);
    }

    /**
     * 将 Object（String 或 List）统一转为 String。
     * List 会拼接为 "A,B,C" 格式。
     */
    private String toSafeString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof String) return (String) value;
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));
        }
        return value.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedQuestion {
        public String kpName;
        public String content;
        public String type = "SINGLE_CHOICE";
        public Object options;  // List<String>（数组）或 Map<String,String>，兼容两种格式
        public Object correctAnswer;  // String 或 List<String>，兼容两种格式
        public String explanation;
        public String difficulty = "0.3";
    }
}
