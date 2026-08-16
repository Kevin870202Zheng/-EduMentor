package com.edumentor.timemachine.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.course.entity.SubjectTheme;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.course.repository.SubjectThemeRepository;
import com.edumentor.diagnosis.repository.AnswerRecordRepository;
import com.edumentor.diagnosis.repository.StudentProfileRepository;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.student.entity.StudentProfile;
import com.edumentor.timemachine.dto.ArchiveRequest;
import com.edumentor.timemachine.dto.TimeMachineLetterRequest;
import com.edumentor.timemachine.entity.GrowthArchiveSnapshot;
import com.edumentor.timemachine.entity.TimeMachineLetter;
import com.edumentor.timemachine.repository.GrowthArchiveSnapshotRepository;
import com.edumentor.timemachine.repository.TimeMachineLetterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 成长时光机服务 — 跨学段自我对话与成长记录。
 * <p>
 * 提供：跃迁曲线数据（成长档案快照聚合）、成长档案归档、
 * 来自过去的信（AI 生成提问 + 回信）、学段晋升自动归档。
 * </p>
 */
@Service
public class TimeMachineService {

    private static final Logger log = LoggerFactory.getLogger(TimeMachineService.class);

    /** 薄弱知识点掌握度阈值 */
    private static final BigDecimal WEAK_THRESHOLD = BigDecimal.valueOf(0.6);

    /** 学段中文名 */
    private static final Map<String, String> STAGE_NAMES = Map.of(
            "PRIMARY", "小学", "JUNIOR", "初中", "SENIOR", "高中", "UNIVERSITY", "大学");

    private final TimeMachineLetterRepository letterRepository;
    private final GrowthArchiveSnapshotRepository archiveRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final SubjectThemeRepository subjectThemeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public TimeMachineService(TimeMachineLetterRepository letterRepository,
                              GrowthArchiveSnapshotRepository archiveRepository,
                              AnswerRecordRepository answerRecordRepository,
                              KnowledgePointRepository knowledgePointRepository,
                              SubjectThemeRepository subjectThemeRepository,
                              StudentProfileRepository studentProfileRepository,
                              LLMService llmService,
                              ObjectMapper objectMapper) {
        this.letterRepository = letterRepository;
        this.archiveRepository = archiveRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.subjectThemeRepository = subjectThemeRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    // ════════════════════════════════════════════
    // 总览
    // ════════════════════════════════════════════

    /**
     * 时光机总览：成长档案（按时间升序）+ 信件列表 + 当前学段。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(UUID studentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<GrowthArchiveSnapshot> archives = archiveRepository.findByStudentIdOrderByCreatedAtAsc(studentId);
        result.put("archives", archives.stream().map(GrowthArchiveSnapshot::toDto).toList());
        result.put("letters", listLetters(studentId));
        result.put("currentStage", studentProfileRepository.findByUserId(studentId)
                .map(StudentProfile::getStage).orElse(null));
        return result;
    }

    // ════════════════════════════════════════════
    // 来自过去的信
    // ════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listLetters(UUID studentId) {
        return letterRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream().map(TimeMachineLetter::toDto).toList();
    }

    /**
     * 创建信件。question 留空时由 AI 基于学生历史薄弱点生成提问。
     * generateOnly=true 时仅生成提问并返回（不落库），供前端预览。
     */
    @Transactional
    public Map<String, Object> createLetter(TimeMachineLetterRequest request) {
        String question = request.getQuestion();
        boolean aiGenerated = false;
        if (question == null || question.isBlank()) {
            question = generateQuestion(request.getStudentId(), request.getStage());
            aiGenerated = true;
        }
        if (Boolean.TRUE.equals(request.getGenerateOnly())) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("question", question);
            preview.put("aiGenerated", aiGenerated);
            preview.put("generateOnly", true);
            return preview;
        }

        TimeMachineLetter letter = new TimeMachineLetter();
        letter.setStudentId(request.getStudentId());
        letter.setStage(request.getStage());
        letter.setCourseId(request.getCourseId());
        if (request.getDirection() != null) letter.setDirection(request.getDirection());
        letter.setQuestion(question);
        letter.setAiGenerated(aiGenerated);
        letterRepository.save(letter);
        log.info("时光机信件已创建: studentId={}, aiGenerated={}", request.getStudentId(), aiGenerated);
        return letter.toDto();
    }

    /**
     * 回答信件。
     */
    @Transactional
    public Map<String, Object> answerLetter(UUID letterId, String answer) {
        TimeMachineLetter letter = letterRepository.findById(letterId)
                .orElseThrow(() -> new ResourceNotFoundException("时光机信件", letterId));
        letter.setAnswer(answer);
        letter.setAnsweredAt(LocalDateTime.now());
        letterRepository.save(letter);
        return letter.toDto();
    }

    // ════════════════════════════════════════════
    // 成长档案快照
    // ════════════════════════════════════════════

    /**
     * 手动归档快照（晋升时也会自动调用）。
     */
    @Transactional
    public Map<String, Object> archive(ArchiveRequest request) {
        String stage = request.getStage();
        if (stage == null || stage.isBlank()) {
            stage = studentProfileRepository.findByUserId(request.getStudentId())
                    .map(StudentProfile::getStage).orElse(null);
        }
        GrowthArchiveSnapshot snapshot = new GrowthArchiveSnapshot();
        snapshot.setStudentId(request.getStudentId());
        snapshot.setStage(stage);
        snapshot.setCourseId(request.getCourseId());
        snapshot.setSummary(buildSnapshotSummary(request.getStudentId(), request.getCourseId()));
        archiveRepository.save(snapshot);
        log.info("成长档案归档完成: studentId={}, stage={}", request.getStudentId(), stage);
        return snapshot.toDto();
    }

    /**
     * 学段晋升时自动归档（由 StudentProfileService 调用）。
     */
    @Transactional
    public Map<String, Object> archiveOnPromotion(UUID studentId, String oldStage, String newStage) {
        log.info("检测到学段变更，自动归档成长档案: studentId={}, {} → {}",
                studentId, oldStage, newStage);
        ArchiveRequest request = new ArchiveRequest();
        request.setStudentId(studentId);
        request.setStage(oldStage);
        return archive(request);
    }

    // ════════════════════════════════════════════
    // 学段学习报告（AI 生成）
    // ════════════════════════════════════════════

    /**
     * 生成指定学段（或全部）的学习报告：掌握情况总结 + 复习建议。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> stageReport(UUID studentId, String stage) {
        List<GrowthArchiveSnapshot> archives = archiveRepository.findByStudentIdOrderByCreatedAtAsc(studentId);
        List<Map<String, Object>> scopeArchives = new ArrayList<>();
        for (GrowthArchiveSnapshot a : archives) {
            if (stage == null || stage.isBlank() || stage.equals(a.getStage())) {
                scopeArchives.add(a.toDto());
            }
        }

        String report;
        try {
            report = generateStageReport(scopeArchives, studentId);
        } catch (Exception e) {
            log.warn("AI 生成学习报告失败，降级为本地摘要: {}", e.getMessage());
            report = buildLocalFallbackReport(scopeArchives);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("stage", stage);
        result.put("report", report);
        result.put("archives", scopeArchives);
        return result;
    }

    // ════════════════════════════════════════════
    // 私有辅助方法
    // ════════════════════════════════════════════

    /**
     * 构建归档摘要 JSON：答题统计 + 主题掌握度 + 薄弱知识点。
     */
    private String buildSnapshotSummary(UUID studentId, UUID courseId) {
        List<Object[]> rows = courseId != null
                ? answerRecordRepository.aggregateByKnowledgePointAllAndCourse(studentId, courseId)
                : answerRecordRepository.aggregateByKnowledgePointAll(studentId);

        long totalQuestions = 0, correctCount = 0;
        Map<UUID, long[]> kpStats = new HashMap<>(); // kpId -> [total, correct]
        for (Object[] row : rows) {
            UUID kpId = (UUID) row[0];
            long total = ((Number) row[1]).longValue();
            long correct = ((Number) row[2]).longValue();
            totalQuestions += total;
            correctCount += correct;
            kpStats.put(kpId, new long[]{total, correct});
        }

        BigDecimal accuracyRate = totalQuestions > 0
                ? BigDecimal.valueOf(correctCount).divide(BigDecimal.valueOf(totalQuestions), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 主题掌握度聚合
        List<KnowledgePoint> kps = knowledgePointRepository.findByIdIn(new ArrayList<>(kpStats.keySet()));
        Map<UUID, long[]> themeStats = new HashMap<>(); // themeId -> [total, correct]
        Map<UUID, String> themeNames = new HashMap<>();
        for (KnowledgePoint kp : kps) {
            UUID themeId = kp.getThemeId();
            if (themeId == null) continue;
            long[] st = kpStats.get(kp.getId());
            if (st == null) continue;
            long[] ts = themeStats.computeIfAbsent(themeId, k -> new long[]{0, 0});
            ts[0] += st[0];
            ts[1] += st[1];
        }
        if (!themeStats.isEmpty()) {
            List<SubjectTheme> themes = subjectThemeRepository.findAllById(themeStats.keySet());
            for (SubjectTheme t : themes) themeNames.put(t.getId(), t.getName());
        }

        List<Map<String, Object>> themeMastery = new ArrayList<>();
        for (Map.Entry<UUID, long[]> e : themeStats.entrySet()) {
            long[] ts = e.getValue();
            BigDecimal mastery = ts[0] > 0
                    ? BigDecimal.valueOf(ts[1]).divide(BigDecimal.valueOf(ts[0]), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("themeId", e.getKey());
            tm.put("themeName", themeNames.getOrDefault(e.getKey(), "未命名主题"));
            tm.put("mastery", mastery);
            tm.put("total", ts[0]);
            themeMastery.add(tm);
        }
        themeMastery.sort(Comparator.comparing(m -> (Comparable) ((Map<String, Object>) m).get("themeName")));

        // 薄弱知识点（掌握度 < 0.6，取前 5）
        List<Map<String, Object>> weakKpsCollector = new ArrayList<>();
        kpStats.forEach((kpId, st) -> {
            BigDecimal mastery = st[0] > 0
                    ? BigDecimal.valueOf(st[1]).divide(BigDecimal.valueOf(st[0]), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            if (mastery.compareTo(WEAK_THRESHOLD) < 0) {
                String name = kps.stream().filter(k -> k.getId().equals(kpId))
                        .map(KnowledgePoint::getName).findFirst().orElse("未知知识点");
                Map<String, Object> wk = new LinkedHashMap<>();
                wk.put("kpId", kpId);
                wk.put("kpName", name);
                wk.put("mastery", mastery);
                weakKpsCollector.add(wk);
            }
        });
        weakKpsCollector.sort((a, b) -> ((BigDecimal) a.get("mastery")).compareTo((BigDecimal) b.get("mastery")));
        List<Map<String, Object>> weakKps = weakKpsCollector.size() > 5
                ? new ArrayList<>(weakKpsCollector.subList(0, 5))
                : weakKpsCollector;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalQuestions", totalQuestions);
        summary.put("correctCount", correctCount);
        summary.put("accuracyRate", accuracyRate);
        summary.put("themeMastery", themeMastery);
        summary.put("weakKps", weakKps);
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.warn("归档摘要序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * AI 生成「来自过去的信」的提问（基于学生薄弱知识点 + 学段认知水平）。
     */
    private String generateQuestion(UUID studentId, String stage) {
        // 取学生最近薄弱知识点作为提问素材
        List<Object[]> rows = answerRecordRepository.aggregateByKnowledgePointAll(studentId);
        List<String> weakNames = new ArrayList<>();
        for (Object[] row : rows) {
            UUID kpId = (UUID) row[0];
            long total = ((Number) row[1]).longValue();
            long correct = ((Number) row[2]).longValue();
            if (total > 0 && BigDecimal.valueOf(correct).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    .compareTo(WEAK_THRESHOLD) < 0) {
                weakNames.add((String) row[3]);
            }
        }
        String stageName = STAGE_NAMES.getOrDefault(stage, "学生");
        String weakContext = weakNames.isEmpty() ? "（暂无特定薄弱知识点）" : "他/她最近在以下知识点上掌握得不太好：" + String.join("、", weakNames);

        String systemPrompt = "你是一个学生成长档案助手。请以一位" + stageName + "学生的口吻，写一句 TA 在学习中真实困惑的问题。" +
                "要求：口语化、符合该学段学生的认知水平和语言习惯、不超过 60 字、只输出问题本身，不要任何解释、引号或前缀。";
        String userMessage = "请基于以下学习情况生成这位" + stageName + "学生的困惑提问：" + weakContext;
        try {
            String response = llmService.ask(systemPrompt, userMessage).getContent();
            String cleaned = response == null ? "" : response.replace("\"", "").replace("'", "").trim();
            if (cleaned.isEmpty()) throw new IllegalStateException("LLM 返回空提问");
            if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80);
            return cleaned;
        } catch (Exception e) {
            log.warn("AI 生成提问失败，使用默认提问: {}", e.getMessage());
            return "我最近学习有点吃力，怎么才能把" + (weakNames.isEmpty() ? "这些知识" : weakNames.get(0)) + "学得更好呢？";
        }
    }

    /**
     * AI 生成学段学习报告（Markdown）。
     */
    private String generateStageReport(List<Map<String, Object>> archives, UUID studentId) {
        // 组装数据摘要
        StringBuilder dataSb = new StringBuilder();
        int idx = 0;
        for (Map<String, Object> a : archives) {
            idx++;
            String stage = String.valueOf(a.getOrDefault("stage", "未知学段"));
            String stageName = STAGE_NAMES.getOrDefault(stage, stage);
            dataSb.append(idx).append(". ").append(stageName).append("学段：").append(a.get("summary")).append("\n");
        }
        if (archives.isEmpty()) {
            dataSb.append("暂无已归档的成长数据。");
        }

        String systemPrompt = "你是一个专业的教育成长分析师。请基于学生的成长档案快照，生成一份「跨学段学习成长报告」。" +
                "报告用 Markdown 格式，包含三部分：\n" +
                "## 一、成长轨迹\n## 二、主题掌握情况\n## 三、下一阶段学习建议\n" +
                "语言要亲切、具体，直接输出报告正文，不要额外说明。";
        String userMessage = "以下是该学生的成长档案数据（JSON）：\n" + dataSb;
        String response = llmService.ask(systemPrompt, userMessage).getContent();
        return response == null || response.isBlank() ? buildLocalFallbackReport(archives) : response;
    }

    /**
     * 本地兜底报告（LLM 不可用时）。
     */
    private String buildLocalFallbackReport(List<Map<String, Object>> archives) {
        if (archives.isEmpty()) {
            return "## 成长报告\n暂无已归档数据。完成学习并归档后，这里将展示你的跨学段成长轨迹。";
        }
        StringBuilder sb = new StringBuilder("## 一、成长轨迹\n");
        int idx = 0;
        BigDecimal best = BigDecimal.ZERO;
        String bestStage = "";
        for (Map<String, Object> a : archives) {
            idx++;
            String stage = String.valueOf(a.getOrDefault("stage", "?"));
            String stageName = STAGE_NAMES.getOrDefault(stage, stage);
            sb.append(idx).append(". ").append(stageName).append("学段：完成学习并已归档\n");
            Object raw = a.get("summary");
            try {
                Map<String, Object> summary = objectMapper.readValue(String.valueOf(raw), Map.class);
                Object acc = summary.get("accuracyRate");
                if (acc instanceof Number n) {
                    BigDecimal rate = BigDecimal.valueOf(n.doubleValue());
                    if (rate.compareTo(best) > 0) {
                        best = rate;
                        bestStage = stageName;
                    }
                }
            } catch (Exception ignored) { }
        }
        sb.append("\n## 二、主题掌握情况\n请完成更多练习后，AI 将为你生成详细的主题掌握分析。\n");
        sb.append("\n## 三、下一阶段学习建议\n").append(bestStage.isEmpty() ? "继续保持学习节奏，及时归档成长数据。" : "你在 " + bestStage + " 学段表现最佳，建议保持学习节奏，并针对薄弱主题做针对性复习。");
        return sb.toString();
    }
}
