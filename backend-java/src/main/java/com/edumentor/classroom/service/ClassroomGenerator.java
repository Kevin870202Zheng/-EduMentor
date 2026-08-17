package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.SceneContent;
import com.edumentor.classroom.dto.SceneOutline;
import com.edumentor.classroom.entity.*;
import com.edumentor.classroom.entity.enums.ActionType;
import com.edumentor.classroom.entity.enums.ClassroomStatus;
import com.edumentor.classroom.entity.enums.SceneType;
import com.edumentor.classroom.repository.ClassroomRepository;
import com.edumentor.classroom.repository.SceneActionRepository;
import com.edumentor.classroom.repository.SceneRepository;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.classroom.dto.AggregatedContent;
import com.edumentor.classroom.dto.ClassroomMaterial;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 课堂生成器 — 课堂生成管线的主编排器。
 * <p>
 * 流程：知识点 → 场景大纲LLM生成 → 每个大纲逐场景内容LLM生成 → 持久化
 * 支持：全量预生成 / 逐场景按需生成 / 补强生成
 * </p>
 */
@Service
public class ClassroomGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClassroomGenerator.class);

    private final SceneOutlineGenerator outlineGenerator;
    private final SceneContentGenerator contentGenerator;
    private final ClassroomRepository classroomRepository;
    private final SceneRepository sceneRepository;
    private final SceneActionRepository sceneActionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CourseRepository courseRepository;
    private final KnowledgeAggregator knowledgeAggregator;
    private final TextbookProvider textbookProvider;
    private final QuestionProvider questionProvider;
    private final ObjectMapper objectMapper;

    /** 生成任务状态缓存（生产环境应改用数据库/Redis） */
    private final Map<String, GenerateJob> jobCache = new ConcurrentHashMap<>();

    public ClassroomGenerator(SceneOutlineGenerator outlineGenerator,
                              SceneContentGenerator contentGenerator,
                              ClassroomRepository classroomRepository,
                              SceneRepository sceneRepository,
                              SceneActionRepository sceneActionRepository,
                              KnowledgePointRepository knowledgePointRepository,
                              CourseRepository courseRepository,
                              KnowledgeAggregator knowledgeAggregator,
                              TextbookProvider textbookProvider,
                              QuestionProvider questionProvider,
                              ObjectMapper objectMapper) {
        this.outlineGenerator = outlineGenerator;
        this.contentGenerator = contentGenerator;
        this.classroomRepository = classroomRepository;
        this.sceneRepository = sceneRepository;
        this.sceneActionRepository = sceneActionRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.courseRepository = courseRepository;
        this.knowledgeAggregator = knowledgeAggregator;
        this.textbookProvider = textbookProvider;
        this.questionProvider = questionProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步生成课堂（全量生成）。
     *
     * @param knowledgePointIds 知识点ID列表
     * @param difficulty         难度等级
     * @return 生成任务ID
     */
    public String generateFull(UUID courseId, List<UUID> knowledgePointIds, int difficulty) {
        String jobId = UUID.randomUUID().toString();
        jobCache.put(jobId, new GenerateJob(jobId, "processing", null));

        // 后台异步生成
        CompletableFuture.runAsync(() -> {
            try {
                for (UUID kpId : knowledgePointIds) {
                    generateClassroom(courseId, kpId, difficulty);
                }
                jobCache.put(jobId, new GenerateJob(jobId, "completed", null));
            } catch (Exception e) {
                log.error("Generation failed for job {}: {}", jobId, e.getMessage());
                jobCache.put(jobId, new GenerateJob(jobId, "failed", e.getMessage()));
            }
        });

        return jobId;
    }

    /**
     * 生成单个知识点的课堂（全量）。
     * 内部包装为单知识点 ClassroomMaterial 后走统一生成管线。
     */
    @Transactional
    public Classroom generateClassroom(UUID courseId, UUID knowledgePointId, int difficulty) {
        KnowledgePoint kp = knowledgePointRepository.findById(knowledgePointId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + knowledgePointId));

        // ── 构建增强上下文（层级聚合 + 教材原文 + 参考习题 + 课程名称） ──
        String courseName = textbookProvider.getCourseName(courseId);
        Map<String, String> extraContext = new LinkedHashMap<>();
        extraContext.put("courseName", courseName);

        // 如果是 CHAPTER/SECTION 层级，做层级聚合
        boolean isContainerLevel = "CHAPTER".equals(kp.getType()) || "SECTION".equals(kp.getType());
        if (isContainerLevel) {
            AggregatedContent aggregated = knowledgeAggregator.aggregate(knowledgePointId);
            extraContext.put("aggregatedContent", aggregated.toPromptText());
            extraContext.put("chapterStructure", "本章包含 " + aggregated.getSections().size()
                    + " 节，共 " + aggregated.getTotalKnowledgePoints() + " 个知识点");
        } else {
            extraContext.put("aggregatedContent", "");
            extraContext.put("chapterStructure", "");
        }

        // 教材原文（最多 8000 字）
        String textbookExcerpt = textbookProvider.getTextbookExcerpt(courseId);
        extraContext.put("textbookExcerpt", textbookExcerpt);

        // 参考习题
        String referenceQuestions = questionProvider.getReferenceQuestions(knowledgePointId);
        extraContext.put("referenceQuestions", referenceQuestions);

        String aggregatedText = extraContext.getOrDefault("aggregatedContent", "");
        ClassroomMaterial material = ClassroomMaterial.builder()
                .courseId(courseId)
                .courseName(courseName)
                .title(kp.getName() + " — AI智慧课堂")
                .description("基于知识点「" + kp.getName() + "」自动生成的AI教学课堂，难度等级" + difficulty)
                .knowledgeContext(aggregatedText != null && !aggregatedText.isEmpty()
                        ? aggregatedText : (kp.getContent() != null ? kp.getContent() : ""))
                .textbookExcerpt(textbookExcerpt)
                .referenceQuestions(referenceQuestions)
                .knowledgePointIds(List.of(knowledgePointId))
                .source("knowledge")
                .difficulty(difficulty)
                .build();

        return generateFromMaterial(material);
    }

    /**
     * 基于课堂素材生成课堂（统一生成管线入口，设计文档 §4.3）。
     * 支持：单知识点 / 多知识点聚合 / 学段协作素材。
     *
     * @param material 课堂素材
     * @return 生成的课堂
     */
    @Transactional
    public Classroom generateFromMaterial(ClassroomMaterial material) {
        log.info("Generating classroom from material: title={}, kps={}, source={}",
                material.getTitle(),
                material.getKnowledgePointIds() != null ? material.getKnowledgePointIds().size() : 0,
                material.getSource());

        // 阶段1: 生成场景大纲
        List<SceneOutline> outlines = outlineGenerator.generate(material, material.getDifficulty());

        // 创建课堂主记录
        Classroom classroom = new Classroom();
        classroom.setCourseId(material.getCourseId());
        // 单知识点课堂保留 knowledgePointId 绑定（兼容 resolve 查询）；聚合/协作课堂置空
        if (material.getKnowledgePointIds() != null && material.getKnowledgePointIds().size() == 1
                && "knowledge".equals(material.getSource())) {
            classroom.setKnowledgePointId(material.getKnowledgePointIds().get(0));
        }
        classroom.setSource(material.getSource() != null ? material.getSource() : "knowledge");
        classroom.setTitle(material.getTitle());
        classroom.setDescription(material.getDescription());
        classroom.setDifficulty(material.getDifficulty());
        classroom.setStatus(ClassroomStatus.published);
        classroom.setSceneCount(outlines.size());
        if (material.getMetadata() != null && !material.getMetadata().isEmpty()) {
            classroom.setMetadata(material.getMetadata());
        } else if (material.getKnowledgePointIds() != null && !material.getKnowledgePointIds().isEmpty()) {
            // 聚合课堂：metadata 记录关联知识点
            classroom.setMetadata(toJson(Map.of("knowledgePointIds",
                    material.getKnowledgePointIds().stream().map(UUID::toString).toList())));
        }
        classroom = classroomRepository.save(classroom);

        int totalDuration = 0;

        // 场景内容生成的增强上下文
        Map<String, String> contentExtra = new HashMap<>();
        contentExtra.put("courseName", material.getCourseName() != null ? material.getCourseName() : "");
        contentExtra.put("knowledgePointContent", material.getKnowledgeContext() != null ? material.getKnowledgeContext() : "");
        contentExtra.put("aggregatedContent", "");

        // 阶段2: 逐场景生成内容
        for (int i = 0; i < outlines.size(); i++) {
            SceneOutline outline = outlines.get(i);
            log.info("Generating content for scene {}/{}: {}", i + 1, outlines.size(), outline.getTitle());

            SceneContent content = contentGenerator.generate(outline, material.getDifficulty(), material.getTitle(), contentExtra);

            // 结构校验与降级：slides/widget 等新结构不合格时就地修复，不中断生成
            if (!SceneContentValidator.isValid(content)) {
                List<String> problems = SceneContentValidator.validate(content);
                log.warn("Scene content structure invalid, sanitizing. scene={}, problems={}",
                        outline.getTitle(), problems);
                SceneContentValidator.sanitize(content);
            }

            // 保存场景
            Scene scene = new Scene();
            scene.setClassroomId(classroom.getId());
            scene.setTitle(content.getTitle() != null ? content.getTitle() : outline.getTitle());
            scene.setDescription(content.getDescription() != null ? content.getDescription() : outline.getDescription());
            scene.setSceneType(content.getType() != null ? content.getType() : outline.getType());
            scene.setOrderIndex(i + 1);
            scene.setEstimatedDurationSeconds(content.getEstimatedDurationSeconds());
            scene.setContentJson(toJson(content));
            scene = sceneRepository.save(scene);

            // 保存教学动作
            if (content.getActions() != null) {
                for (int j = 0; j < content.getActions().size(); j++) {
                    var actionDto = content.getActions().get(j);
                    SceneAction action = new SceneAction();
                    action.setSceneId(scene.getId());
                    action.setActionType(actionDto.getType() != null ? actionDto.getType() : ActionType.speech);
                    action.setOrderIndex(j + 1);
                    action.setDurationMs(actionDto.getDuration());

                    Map<String, Object> params = new HashMap<>();
                    if (actionDto.getText() != null) params.put("text", actionDto.getText());
                    if (actionDto.getContent() != null) params.put("content", actionDto.getContent());
                    if (actionDto.getPosition() != null) params.put("position", actionDto.getPosition());
                    if (actionDto.getTopic() != null) params.put("topic", actionDto.getTopic());
                    if (actionDto.getPrompt() != null) params.put("prompt", actionDto.getPrompt());
                    if (actionDto.getQuestion() != null) {
                        params.put("question", actionDto.getQuestion());
                        params.put("options", actionDto.getOptions());
                        params.put("correctIndex", actionDto.getCorrectIndex());
                        params.put("explanation", actionDto.getExplanation());
                    }
                    if (actionDto.getWbContent() != null) params.put("wbContent", actionDto.getWbContent());
                    if (actionDto.getWbStyle() != null) params.put("wbStyle", actionDto.getWbStyle());
                    if (actionDto.getLayoutId() != null) params.put("layoutId", actionDto.getLayoutId());
                    if (actionDto.getSpeech() != null) params.put("speech", actionDto.getSpeech());
                    if (actionDto.getWidgetKey() != null) params.put("widgetKey", actionDto.getWidgetKey());
                    if (actionDto.getIntro() != null) params.put("intro", actionDto.getIntro());
                    if (actionDto.getTarget() != null) params.put("target", actionDto.getTarget());
                    if (actionDto.getState() != null) params.put("state", actionDto.getState());
                    if (actionDto.getParams() != null) params.putAll(actionDto.getParams());
                    action.setParamsJson(toJson(params));

                    sceneActionRepository.save(action);
                }
            }

            if (content.getEstimatedDurationSeconds() != null) {
                totalDuration += content.getEstimatedDurationSeconds();
            }
        }

        // 更新课堂总时长
        classroom.setTotalDurationSeconds(totalDuration);
        classroomRepository.save(classroom);

        log.info("Classroom generated: id={}, title={}, scenes={}, duration={}s, source={}",
                classroom.getId(), classroom.getTitle(), outlines.size(), totalDuration, classroom.getSource());

        return classroom;
    }

    /**
     * 基于勾选知识点/章节生成聚合课堂（场景一，设计文档 §4）。
     * 章节自动展开为叶子知识点；所有叶子知识点聚合为一个课堂。
     *
     * @param courseId   所属课程
     * @param kpIds      勾选的知识点/章节 ID（可混合）
     * @param title      课堂标题（可空，自动生成）
     * @param difficulty 难度 1-5
     * @param courseName 课程名称（可空，自动查询）
     * @return 生成的聚合课堂
     */
    @Transactional
    public Classroom generateFromSelection(UUID courseId, List<UUID> kpIds, String title, int difficulty, String courseName) {
        List<UUID> leafIds = expandToLeaves(kpIds);
        if (leafIds.isEmpty()) {
            throw new IllegalArgumentException("未选择任何知识点或章节");
        }
        List<KnowledgePoint> kps = loadOrderedBy(leafIds);
        String context = buildAggregatedContext(kps);

        String resolvedCourseName = (courseName != null && !courseName.isEmpty())
                ? courseName : textbookProvider.getCourseName(courseId);
        String resolvedTitle = (title != null && !title.isEmpty())
                ? title : "「" + kps.get(0).getName() + "」等 " + kps.size() + " 个知识点 · 聚合课堂";
        String kpNames = kps.stream().map(KnowledgePoint::getName).limit(3).collect(Collectors.joining("、"));

        ClassroomMaterial material = ClassroomMaterial.builder()
                .courseId(courseId)
                .courseName(resolvedCourseName)
                .title(resolvedTitle)
                .description("基于 " + kps.size() + " 个知识点聚合生成的AI智慧课堂，覆盖 " + kpNames + " 等")
                .knowledgeContext(context)
                .knowledgePointIds(leafIds)
                .source("multi_knowledge")
                .difficulty(difficulty)
                .build();

        return generateFromMaterial(material);
    }

    /** 章节/小节展开为叶子知识点（保持勾选顺序） */
    private List<UUID> expandToLeaves(List<UUID> kpIds) {
        Set<UUID> leaves = new LinkedHashSet<>();
        for (UUID id : kpIds) {
            KnowledgePoint kp = knowledgePointRepository.findById(id).orElse(null);
            if (kp == null) continue;
            if ("LEAF".equals(kp.getType())) {
                leaves.add(id);
            } else {
                collectLeaves(id, leaves);
            }
        }
        return new ArrayList<>(leaves);
    }

    private void collectLeaves(UUID parentId, Set<UUID> leaves) {
        List<KnowledgePoint> children = knowledgePointRepository.findByParentKpId(parentId);
        for (KnowledgePoint c : children) {
            if ("LEAF".equals(c.getType())) {
                leaves.add(c.getId());
            } else {
                collectLeaves(c.getId(), leaves);
            }
        }
    }

    /** 按传入 ID 顺序加载知识点实体 */
    private List<KnowledgePoint> loadOrderedBy(List<UUID> ids) {
        Map<UUID, KnowledgePoint> byId = knowledgePointRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, kp -> kp));
        List<KnowledgePoint> ordered = new ArrayList<>();
        for (UUID id : ids) {
            KnowledgePoint kp = byId.get(id);
            if (kp != null) ordered.add(kp);
        }
        return ordered;
    }

    /** 聚合多个知识点为课堂素材文本 */
    private String buildAggregatedContext(List<KnowledgePoint> kps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kps.size(); i++) {
            KnowledgePoint kp = kps.get(i);
            sb.append("【知识点").append(i + 1).append("】").append(kp.getName()).append("\n");
            if (kp.getDescription() != null && !kp.getDescription().isBlank()) {
                sb.append("描述：").append(kp.getDescription()).append("\n");
            }
            if (kp.getContent() != null && !kp.getContent().isBlank()) {
                sb.append("内容：").append(kp.getContent()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 逐场景按需生成（用于首屏快速加载）。
     *
     * @param classroomId 课堂ID
     * @param sceneIndex  场景序号（从0开始）
     * @return 已生成的场景
     */
    @Transactional
    public Scene generateSceneOnDemand(UUID classroomId, int sceneIndex) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("课堂不存在: " + classroomId));

        List<Scene> existingScenes = sceneRepository.findByClassroomIdOrderByOrderIndexAsc(classroomId);

        // 如果场景已生成，直接返回
        if (sceneIndex < existingScenes.size()) {
            return existingScenes.get(sceneIndex);
        }

        // 需要先生成大纲（如果是首次）
        KnowledgePoint kp = knowledgePointRepository.findById(classroom.getKnowledgePointId())
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在"));

        // 构建增强上下文
        Map<String, String> extraContext = new LinkedHashMap<>();
        extraContext.put("courseName", textbookProvider.getCourseName(classroom.getCourseId()));
        boolean isContainerLevel = "CHAPTER".equals(kp.getType()) || "SECTION".equals(kp.getType());
        if (isContainerLevel) {
            AggregatedContent aggregated = knowledgeAggregator.aggregate(kp.getId());
            extraContext.put("aggregatedContent", aggregated.toPromptText());
            extraContext.put("chapterStructure", "本章包含 " + aggregated.getSections().size()
                    + " 节，共 " + aggregated.getTotalKnowledgePoints() + " 个知识点");
        } else {
            extraContext.put("aggregatedContent", "");
            extraContext.put("chapterStructure", "");
        }
        extraContext.put("textbookExcerpt", textbookProvider.getTextbookExcerpt(classroom.getCourseId()));
        extraContext.put("referenceQuestions", questionProvider.getReferenceQuestions(kp.getId()));

        List<SceneOutline> outlines = outlineGenerator.generate(kp, classroom.getDifficulty(), extraContext);

        // 确保已生成的场景数量不超过大纲数量
        int startFrom = existingScenes.size();
        if (sceneIndex >= outlines.size()) {
            throw new IllegalArgumentException("场景序号超出范围: " + sceneIndex);
        }

        // 生成直到目标场景
        Scene targetScene = null;
        for (int i = startFrom; i <= sceneIndex && i < outlines.size(); i++) {
            SceneOutline outline = outlines.get(i);
            SceneContent content = contentGenerator.generate(outline, classroom.getDifficulty(), kp.getName(), extraContext);

            Scene scene = new Scene();
            scene.setClassroomId(classroom.getId());
            scene.setTitle(content.getTitle() != null ? content.getTitle() : outline.getTitle());
            scene.setDescription(content.getDescription());
            scene.setSceneType(content.getType() != null ? content.getType() : outline.getType());
            scene.setOrderIndex(i + 1);
            scene.setEstimatedDurationSeconds(content.getEstimatedDurationSeconds());
            scene.setContentJson(toJson(content));
            scene = sceneRepository.save(scene);

            // 保存动作
            if (content.getActions() != null) {
                for (int j = 0; j < content.getActions().size(); j++) {
                    var actionDto = content.getActions().get(j);
                    SceneAction action = new SceneAction();
                    action.setSceneId(scene.getId());
                    action.setActionType(actionDto.getType() != null ? actionDto.getType() : ActionType.speech);
                    action.setOrderIndex(j + 1);
                    action.setDurationMs(actionDto.getDuration());

                    Map<String, Object> params = new HashMap<>();
                    if (actionDto.getText() != null) params.put("text", actionDto.getText());
                    if (actionDto.getContent() != null) params.put("content", actionDto.getContent());
                    if (actionDto.getPosition() != null) params.put("position", actionDto.getPosition());
                    if (actionDto.getTopic() != null) params.put("topic", actionDto.getTopic());
                    if (actionDto.getPrompt() != null) params.put("prompt", actionDto.getPrompt());
                    if (actionDto.getQuestion() != null) {
                        params.put("question", actionDto.getQuestion());
                        params.put("options", actionDto.getOptions());
                        params.put("correctIndex", actionDto.getCorrectIndex());
                        params.put("explanation", actionDto.getExplanation());
                    }
                    action.setParamsJson(toJson(params));
                    sceneActionRepository.save(action);
                }
            }

            if (i == sceneIndex) {
                targetScene = scene;
            }
        }

        // 更新课堂场景计数
        long count = sceneRepository.countByClassroomId(classroom.getId());
        classroom.setSceneCount((int) count);
        classroomRepository.save(classroom);

        return targetScene;
    }

    /**
     * 获取生成任务状态。
     */
    public GenerateJob getJobStatus(String jobId) {
        return jobCache.get(jobId);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON serialization error: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 生成任务状态记录。
     */
    public static class GenerateJob {
        private final String jobId;
        private final String status;
        private final String error;

        public GenerateJob(String jobId, String status, String error) {
            this.jobId = jobId;
            this.status = status;
            this.error = error;
        }

        public String getJobId() { return jobId; }
        public String getStatus() { return status; }
        public String getError() { return error; }
    }
}
