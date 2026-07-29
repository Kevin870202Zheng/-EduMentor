package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.*;
import com.edumentor.classroom.entity.*;
import com.edumentor.classroom.entity.enums.ActionType;
import com.edumentor.classroom.entity.enums.ProgressStatus;
import com.edumentor.classroom.repository.*;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.engine.bkt.BKTParams;
import com.edumentor.engine.bkt.BKTService;
import com.edumentor.engine.llm.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课堂服务 — 课堂生成、播放控制、进度管理、Quiz处理的核心服务。
 */
@Service
public class ClassroomService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomService.class);

    private final ClassroomRepository classroomRepository;
    private final SceneRepository sceneRepository;
    private final SceneActionRepository sceneActionRepository;
    private final ClassroomProgressRepository progressRepository;
    private final SceneQuizRecordRepository quizRecordRepository;
    private final ClassroomGenerator classroomGenerator;
    private final KnowledgePointRepository knowledgePointRepository;
    private final BKTService bktService;
    private final ObjectMapper objectMapper;

    public ClassroomService(ClassroomRepository classroomRepository,
                            SceneRepository sceneRepository,
                            SceneActionRepository sceneActionRepository,
                            ClassroomProgressRepository progressRepository,
                            SceneQuizRecordRepository quizRecordRepository,
                            ClassroomGenerator classroomGenerator,
                            KnowledgePointRepository knowledgePointRepository,
                            BKTService bktService,
                            ObjectMapper objectMapper) {
        this.classroomRepository = classroomRepository;
        this.sceneRepository = sceneRepository;
        this.sceneActionRepository = sceneActionRepository;
        this.progressRepository = progressRepository;
        this.quizRecordRepository = quizRecordRepository;
        this.classroomGenerator = classroomGenerator;
        this.knowledgePointRepository = knowledgePointRepository;
        this.bktService = bktService;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  课堂查询
    // ═══════════════════════════════════════════════════════════════

    /**
     * 获取课堂完整详情（含场景和教学动作）。
     */
    @Transactional(readOnly = true)
    public ClassroomDetailDto getClassroomDetail(UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("课堂不存在: " + classroomId));

        List<Scene> scenes = sceneRepository.findByClassroomIdOrderByOrderIndexAsc(classroomId);
        List<SceneDetailDto> sceneDtos = scenes.stream()
                .map(this::toSceneDetailDto)
                .collect(Collectors.toList());

        return ClassroomDetailDto.builder()
                .id(classroom.getId().toString())
                .courseId(classroom.getCourseId().toString())
                .knowledgePointId(classroom.getKnowledgePointId().toString())
                .title(classroom.getTitle())
                .description(classroom.getDescription())
                .difficulty(classroom.getDifficulty())
                .totalDurationSeconds(classroom.getTotalDurationSeconds())
                .status(classroom.getStatus() != null ? classroom.getStatus().name() : null)
                .sceneCount(classroom.getSceneCount())
                .version(classroom.getVersion())
                .scenes(sceneDtos)
                .metadata(parseJsonMap(classroom.getMetadata()))
                .createdAt(classroom.getCreatedAt() != null ? classroom.getCreatedAt().toString() : null)
                .updatedAt(classroom.getUpdatedAt() != null ? classroom.getUpdatedAt().toString() : null)
                .build();
    }

    /**
     * 获取课程下的课堂列表。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getClassroomsByCourse(UUID courseId) {
        return classroomRepository.findByCourseIdOrderByCreatedAtDesc(courseId)
                .stream()
                .map(Classroom::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 为知识点生成或获取已有课堂。
     */
    @Transactional
    public Classroom getOrCreateClassroom(UUID courseId, UUID knowledgePointId, int difficulty) {
        // 查找是否已有已发布的课堂
        Optional<Classroom> existing = classroomRepository
                .findFirstByCourseIdAndKnowledgePointIdAndStatusOrderByCreatedAtDesc(
                        courseId, knowledgePointId, com.edumentor.classroom.entity.enums.ClassroomStatus.published);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 没有则生成
        return classroomGenerator.generateClassroom(courseId, knowledgePointId, difficulty);
    }

    // ═══════════════════════════════════════════════════════════════
    //  播放控制
    // ═══════════════════════════════════════════════════════════════

    /**
     * 开始学习课堂（创建或恢复进度）。
     */
    @Transactional
    public ClassroomProgress startClassroom(UUID studentId, UUID classroomId) {
        ClassroomProgress progress = progressRepository
                .findByStudentIdAndClassroomId(studentId, classroomId)
                .orElse(null);

        if (progress == null) {
            Classroom classroom = classroomRepository.findById(classroomId)
                    .orElseThrow(() -> new IllegalArgumentException("课堂不存在: " + classroomId));

            List<Scene> scenes = sceneRepository.findByClassroomIdOrderByOrderIndexAsc(classroomId);

            progress = new ClassroomProgress();
            progress.setStudentId(studentId);
            progress.setClassroomId(classroomId);
            progress.setStatus(ProgressStatus.in_progress);
            progress.setTotalScenes(classroom.getSceneCount() != null ? classroom.getSceneCount() : scenes.size());
            progress.setScenesCompleted(0);
            progress.setCurrentActionOrder(0);
            progress.setTotalWatchSeconds(0);
            progress.setQuizCorrectCount(0);
            progress.setQuizTotalCount(0);
            progress.setStartedAt(LocalDateTime.now());
            progress.setLastAccessedAt(LocalDateTime.now());

            if (!scenes.isEmpty()) {
                progress.setCurrentSceneId(scenes.get(0).getId());
            }
        } else {
            // 恢复进度
            progress.setStatus(ProgressStatus.in_progress);
            progress.setLastAccessedAt(LocalDateTime.now());
        }

        return progressRepository.save(progress);
    }

    /**
     * 更新播放进度。
     */
    @Transactional
    public ClassroomProgress updateProgress(UUID studentId, UUID classroomId,
                                            UUID currentSceneId, int currentActionOrder) {
        ClassroomProgress progress = progressRepository
                .findByStudentIdAndClassroomId(studentId, classroomId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课堂进度"));

        progress.setCurrentSceneId(currentSceneId);
        progress.setCurrentActionOrder(currentActionOrder);
        progress.setLastAccessedAt(LocalDateTime.now());
        return progressRepository.save(progress);
    }

    /**
     * 标记场景完成。
     */
    @Transactional
    public ClassroomProgress completeScene(UUID studentId, UUID classroomId, UUID sceneId) {
        ClassroomProgress progress = progressRepository
                .findByStudentIdAndClassroomId(studentId, classroomId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课堂进度"));

        progress.setScenesCompleted(progress.getScenesCompleted() + 1);
        progress.setCurrentSceneId(findNextSceneId(classroomId, sceneId));
        progress.setCurrentActionOrder(0);
        progress.setLastAccessedAt(LocalDateTime.now());

        // 如果所有场景已完成，标记课堂完成
        if (progress.getScenesCompleted() >= progress.getTotalScenes()) {
            progress.setStatus(ProgressStatus.completed);
            progress.setCompletedAt(LocalDateTime.now());
        }

        return progressRepository.save(progress);
    }

    /**
     * 标记课堂完成。
     */
    @Transactional
    public ClassroomProgress completeClassroom(UUID studentId, UUID classroomId) {
        ClassroomProgress progress = progressRepository
                .findByStudentIdAndClassroomId(studentId, classroomId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课堂进度"));

        progress.setStatus(ProgressStatus.completed);
        progress.setCompletedAt(LocalDateTime.now());
        progress.setScenesCompleted(progress.getTotalScenes());
        progress.setLastAccessedAt(LocalDateTime.now());
        return progressRepository.save(progress);
    }

    /**
     * 暂停课堂。
     */
    @Transactional
    public ClassroomProgress pauseClassroom(UUID studentId, UUID classroomId) {
        ClassroomProgress progress = progressRepository
                .findByStudentIdAndClassroomId(studentId, classroomId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课堂进度"));
        progress.setStatus(ProgressStatus.paused);
        progress.setLastAccessedAt(LocalDateTime.now());
        return progressRepository.save(progress);
    }

    /**
     * 获取课堂进度。
     */
    @Transactional(readOnly = true)
    public ClassroomProgress getProgress(UUID studentId, UUID classroomId) {
        return progressRepository
                .findByStudentIdAndClassroomId(studentId, classroomId)
                .orElse(null);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Quiz 处理
    // ═══════════════════════════════════════════════════════════════

    /**
     * 提交课堂 Quiz 答案。
     */
    @Transactional
    public QuizSubmitResponse submitQuiz(UUID studentId, QuizSubmitRequest request) {
        UUID sceneId = UUID.fromString(request.getSceneId());
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("场景不存在: " + sceneId));

        // 解析场景内容，获取正确答案
        SceneContent content = parseSceneContent(scene.getContentJson());
        if (content == null || content.getActions() == null) {
            throw new IllegalArgumentException("场景内容缺失");
        }

        // 找到Quiz动作
        var quizAction = content.getActions().stream()
                .filter(a -> a.getType() == ActionType.quiz || "quiz".equals(a.getType()))
                .findFirst().orElse(null);

        if (quizAction == null) {
            throw new IllegalArgumentException("该场景没有Quiz");
        }

        // 判断答案
        Integer selectedIndex = request.getSelectedIndex();
        Integer correctIndex = quizAction.getCorrectIndex();
        boolean isCorrect = selectedIndex != null && selectedIndex.equals(correctIndex);

        // 确定知识点ID
        UUID knowledgePointId = findKnowledgePointForScene(scene);

        // 查询知识点名称（用于前端展示关联反馈）
        String knowledgePointName = null;
        if (knowledgePointId != null) {
            var kpOpt = knowledgePointRepository.findById(knowledgePointId);
            if (kpOpt.isPresent()) {
                knowledgePointName = kpOpt.get().getName();
            }
        }

        // 记录作答
        SceneQuizRecord record = new SceneQuizRecord();
        record.setStudentId(studentId);
        record.setSceneId(sceneId);
        record.setKnowledgePointId(knowledgePointId);
        record.setQuizData(scene.getContentJson());
        record.setStudentAnswer(toJson(request.getStudentAnswer()));
        record.setIsCorrect(isCorrect);
        record.setAnsweredAt(LocalDateTime.now());
        quizRecordRepository.save(record);

        // 更新BKT状态
        if (knowledgePointId != null) {
            bktService.recordAnswer(studentId, knowledgePointId, isCorrect, BKTParams.defaultParams());
        }

        // 生成AI反馈
        String aiFeedback = isCorrect
                ? "回答正确！" + (quizAction.getExplanation() != null ? " " + quizAction.getExplanation() : "")
                : "回答有误。正确答案是：" + quizAction.getExplanation();

        // 获取BKT更新后的掌握度
        double mastery = knowledgePointId != null
                ? bktService.getMastery(studentId, knowledgePointId)
                : 0.0;

        Map<String, Object> bktUpdate = new HashMap<>();
        bktUpdate.put("knowledgePointId", knowledgePointId != null ? knowledgePointId.toString() : null);
        bktUpdate.put("mastery", mastery);
        bktUpdate.put("isCorrect", isCorrect);

        // 更新课堂进度中的Quiz计数
        updateQuizCountInProgress(studentId, scene.getClassroomId(), isCorrect);

        return QuizSubmitResponse.builder()
                .isCorrect(isCorrect)
                .correctAnswer(correctIndex != null ? String.valueOf(correctIndex) : "")
                .explanation(quizAction.getExplanation())
                .aiFeedback(aiFeedback)
                .masteryDelta(isCorrect ? 5 : -3)
                .bktUpdate(bktUpdate)
                .knowledgePointName(knowledgePointName)
                .build();
    }

    /**
     * 获取学生的课堂Quiz记录。
     */
    @Transactional(readOnly = true)
    public List<SceneQuizRecord> getQuizRecords(UUID studentId, UUID sceneId) {
        return quizRecordRepository.findByStudentIdAndSceneIdOrderByCreatedAtAsc(studentId, sceneId);
    }

    /**
     * 获取学生的课堂历史记录（含进度）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStudentHistory(UUID studentId) {
        List<ClassroomProgress> progressList = progressRepository
                .findByStudentIdOrderByLastAccessedAtDesc(studentId);

        return progressList.stream().map(p -> {
            Map<String, Object> dto = p.toDto();
            // 额外补充课堂信息
            classroomRepository.findById(p.getClassroomId()).ifPresent(c -> {
                dto.put("classroomTitle", c.getTitle());
                dto.put("classroomDescription", c.getDescription());
                dto.put("difficulty", c.getDifficulty());
            });
            return dto;
        }).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    //  内部方法
    // ═══════════════════════════════════════════════════════════════

    private SceneDetailDto toSceneDetailDto(Scene scene) {
        List<ActionDTO> actions = loadActions(scene.getId());

        Map<String, Object> content = parseJsonMap(scene.getContentJson());

        return SceneDetailDto.builder()
                .id(scene.getId().toString())
                .classroomId(scene.getClassroomId().toString())
                .title(scene.getTitle())
                .description(scene.getDescription())
                .sceneType(scene.getSceneType() != null ? scene.getSceneType().name() : null)
                .orderIndex(scene.getOrderIndex())
                .estimatedDurationSeconds(scene.getEstimatedDurationSeconds())
                .actions(actions)
                .content(content)
                .createdAt(scene.getCreatedAt() != null ? scene.getCreatedAt().toString() : null)
                .build();
    }

    private List<ActionDTO> loadActions(UUID sceneId) {
        return sceneActionRepository.findBySceneIdOrderByOrderIndexAsc(sceneId)
                .stream()
                .map(this::toActionDto)
                .collect(Collectors.toList());
    }

    private ActionDTO toActionDto(SceneAction action) {
        Map<String, Object> params = parseJsonMap(action.getParamsJson());
        String text = params.getOrDefault("text", "").toString();
        String content = params.getOrDefault("content", "").toString();

        // 从 params 中提取 quiz 字段
        String question = params.getOrDefault("question", "").toString();
        Object optionsRaw = params.get("options");
        String[] options = null;
        if (optionsRaw instanceof List) {
            options = ((List<?>) optionsRaw).stream()
                    .map(Object::toString)
                    .toArray(String[]::new);
        } else if (optionsRaw instanceof String[]) {
            options = (String[]) optionsRaw;
        }
        Integer correctIndex = null;
        if (params.containsKey("correctIndex")) {
            Object ci = params.get("correctIndex");
            if (ci instanceof Number) {
                correctIndex = ((Number) ci).intValue();
            }
        }
        String explanation = params.getOrDefault("explanation", "").toString();

        // 提取 discussion 字段
        String topic = params.getOrDefault("topic", "").toString();
        String prompt = params.getOrDefault("prompt", "").toString();

        return ActionDTO.builder()
                .type(action.getActionType())
                .text(text)
                .content(content)
                .duration(action.getDurationMs())
                .params(params)
                .question(question.isEmpty() && !params.containsKey("question") ? null : question)
                .options(options)
                .correctIndex(correctIndex)
                .explanation(explanation.isEmpty() && !params.containsKey("explanation") ? null : explanation)
                .topic(topic.isEmpty() && !params.containsKey("topic") ? null : topic)
                .prompt(prompt.isEmpty() && !params.containsKey("prompt") ? null : prompt)
                .wbContent(params.getOrDefault("wbContent", "").toString())
                .wbStyle(params.getOrDefault("wbStyle", "").toString())
                .build();
    }

    private SceneContent parseSceneContent(String json) {
        try {
            return objectMapper.readValue(json, SceneContent.class);
        } catch (Exception e) {
            log.warn("Failed to parse scene content JSON: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private UUID findNextSceneId(UUID classroomId, UUID currentSceneId) {
        List<Scene> scenes = sceneRepository.findByClassroomIdOrderByOrderIndexAsc(classroomId);
        for (int i = 0; i < scenes.size() - 1; i++) {
            if (scenes.get(i).getId().equals(currentSceneId)) {
                return scenes.get(i + 1).getId();
            }
        }
        return null;
    }

    private UUID findKnowledgePointForScene(Scene scene) {
        Classroom classroom = classroomRepository.findById(scene.getClassroomId()).orElse(null);
        return classroom != null ? classroom.getKnowledgePointId() : null;
    }

    private void updateQuizCountInProgress(UUID studentId, UUID classroomId, boolean isCorrect) {
        ClassroomProgress progress = progressRepository
                .findByStudentIdAndClassroomId(studentId, classroomId).orElse(null);
        if (progress != null) {
            progress.setQuizTotalCount(progress.getQuizTotalCount() + 1);
            if (isCorrect) {
                progress.setQuizCorrectCount(progress.getQuizCorrectCount() + 1);
            }
            progressRepository.save(progress);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
