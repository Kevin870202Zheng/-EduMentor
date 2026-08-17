package com.edumentor.arbitration.service;

import com.edumentor.arbitration.dto.ArbitrationAwardRequest;
import com.edumentor.arbitration.dto.ArbitrationCase;
import com.edumentor.arbitration.entity.ArbitrationMessage;
import com.edumentor.arbitration.entity.ArbitrationSession;
import com.edumentor.arbitration.entity.enums.ArbitrationPhase;
import com.edumentor.arbitration.entity.enums.ArbitrationRole;
import com.edumentor.arbitration.entity.enums.ArbitrationStatus;
import com.edumentor.arbitration.repository.ArbitrationMessageRepository;
import com.edumentor.arbitration.repository.ArbitrationSessionRepository;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.diagnosis.repository.AnswerRecordRepository;
import com.edumentor.engine.llm.ChatMessage;
import com.edumentor.engine.llm.LLMResponse;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.record.entity.AnswerRecord;
import com.edumentor.record.repository.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 仲裁人案例分析服务（模拟仲裁）。
 * <p>
 * 每个知识点（一课）两个阶段：PRE 课前仲裁 / POST 课后仲裁，共用同一案件。
 * 学生扮演仲裁人，AI 扮演无法律基础的普通老百姓原/被告（降智人设）；
 * 双裁决齐全后生成 AI 分析报告（认知变化轨迹 / 知识掌握评估 / 学习建议）。
 * </p>
 * 设计文档: .youcoder/plans/learning-directory-arbitration-design.html (v1.0) §4
 */
@Service
public class ArbitrationService {

    private static final Logger log = LoggerFactory.getLogger(ArbitrationService.class);

    /** 仲裁环节名称 */
    public static final String[] STAGE_NAMES = {"陈述", "答辩", "举证质证", "辩论", "裁决"};

    /** 课后仲裁准入：掌握度阈值 */
    private static final double MASTERY_THRESHOLD = 0.5;

    private final ArbitrationSessionRepository sessionRepository;
    private final ArbitrationMessageRepository messageRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CourseRepository courseRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public ArbitrationService(ArbitrationSessionRepository sessionRepository,
                              ArbitrationMessageRepository messageRepository,
                              KnowledgePointRepository knowledgePointRepository,
                              CourseRepository courseRepository,
                              AnswerRecordRepository answerRecordRepository,
                              QuestionRepository questionRepository,
                              LLMService llmService,
                              ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.courseRepository = courseRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.questionRepository = questionRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════
    //  会话与案例
    // ═══════════════════════════════════════════════════════════

    /**
     * 启动（或获取）仲裁会话。
     * <p>PRE：首次进入生成案件 + 开庭；POST：复用 PRE 案件 + 掌握度准入校验。</p>
     */
    @Transactional
    public Map<String, Object> start(UUID kpId, UUID studentId, ArbitrationPhase phase) {
        KnowledgePoint kp = knowledgePointRepository.findById(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + kpId));

        if (phase == ArbitrationPhase.POST) {
            validatePostEligible(kpId, studentId);
        }

        // 已有会话则直接返回
        ArbitrationSession existing = sessionRepository
                .findByKnowledgePointIdAndStudentIdAndPhase(kpId, studentId, phase)
                .orElse(null);
        if (existing != null) {
            return sessionDetail(existing);
        }

        // 新建会话
        ArbitrationSession session = new ArbitrationSession();
        session.setCourseId(kp.getCourseId());
        session.setKnowledgePointId(kpId);
        session.setStudentId(studentId);
        session.setPhase(phase);
        session.setStatus(ArbitrationStatus.CASE_GENERATING);
        session.setStageIndex(0);

        // 案件：POST 复用 PRE；PRE 新生成
        ArbitrationCase caseData = loadOrGenerateCase(kpId, studentId);
        if (caseData != null) {
            session.setCaseContent(toJson(caseData));
        }
        session = sessionRepository.save(session);

        // 开庭：记录员播报 + 原告陈述
        session.setStatus(ArbitrationStatus.OPENING);
        sessionRepository.save(session);
        addMessage(session, ArbitrationRole.CLERK, buildOpeningSpeech(caseData), 0);
        String plaintiffSpeech = speak(session, ArbitrationRole.PLAINTIFF_AI,
                "请作为申请人（原告）进行开场陈述：讲讲事情经过、自己受了什么委屈、想要什么结果（60-180 字，大白话）。");
        addMessage(session, ArbitrationRole.PLAINTIFF_AI, plaintiffSpeech, 1);
        session.setStatus(ArbitrationStatus.HEARING);
        sessionRepository.save(session);

        return sessionDetail(session);
    }

    /** 课后仲裁准入：掌握度 ≥ 0.5；若该知识点无练习题则直接放行 */
    private void validatePostEligible(UUID kpId, UUID studentId) {
        long questionCount = questionRepository.countByKnowledgePointId(kpId);
        if (questionCount == 0) {
            return; // 无练习题的知识点直接放行（风险 R3 对策）
        }
        List<AnswerRecord> records = answerRecordRepository
                .findByStudentIdAndKnowledgePointId(studentId, kpId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("请先完成本课学习（掌握度达到 50%）后再进行课后仲裁");
        }
        long correct = records.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
        if ((double) correct / records.size() < MASTERY_THRESHOLD) {
            throw new IllegalArgumentException("请先完成本课学习（当前掌握度未达到 50%）后再进行课后仲裁");
        }
    }

    /** 查询仲裁会话（含案件 + 消息 + 裁决） */
    @Transactional(readOnly = true)
    public Map<String, Object> getSession(UUID kpId, UUID studentId, ArbitrationPhase phase) {
        ArbitrationSession session = requireSession(kpId, studentId, phase);
        return sessionDetail(session);
    }

    /**
     * 加载案件：POST 复用 PRE 案件；PRE 不存在则生成。
     */
    private ArbitrationCase loadOrGenerateCase(UUID kpId, UUID studentId) {
        ArbitrationSession pre = sessionRepository
                .findFirstByKnowledgePointIdAndStudentIdAndPhaseOrderByCreatedAtDesc(
                        kpId, studentId, ArbitrationPhase.PRE)
                .orElse(null);
        if (pre != null && pre.getCaseContent() != null && !pre.getCaseContent().isBlank()) {
            ArbitrationCase c = parseCase(pre.getCaseContent());
            if (c != null) {
                return c;
            }
        }
        return generateCase(kpId);
    }

    /**
     * 按知识点生成案件（LLM 结构化输出，失败降级为通识案件）。
     * 素材：当前知识点内容优先，不足时扩展父级章节 + 同级兄弟知识点。
     */
    private ArbitrationCase generateCase(UUID kpId) {
        String knowledgeContext = buildKnowledgeContext(kpId);
        String courseName = "";
        KnowledgePoint kp = knowledgePointRepository.findById(kpId).orElse(null);
        if (kp != null && kp.getCourseId() != null) {
            courseName = courseRepository.findById(kp.getCourseId())
                    .map(c -> c.getName()).orElse("");
        }

        String systemPrompt = "你是一名法律教育工作者和模拟仲裁案件设计者。请根据提供的知识点内容，"
                + "设计一个贴近生活的模拟仲裁案件。要求："
                + "1. 案情要像真实生活中会发生的事（买卖、租赁、邻里纠纷、校园冲突等），当事人都是普通老百姓；"
                + "2. 争议焦点（2-3 个）必须紧扣知识点；"
                + "3. 原告与被告立场清晰对立，诉求与抗辩都用生活化语言表达；"
                + "4. 案件难度与知识点难度匹配（1-5）；"
                + "5. 使用通俗易懂的中文，适合中小学生模拟仲裁。";

        String userPrompt = "【课程名称】" + courseName + "\n"
                + "【知识点内容】\n" + knowledgeContext;

        try {
            ArbitrationCase caseData = llmService.askStructured(systemPrompt, userPrompt,
                    ArbitrationCase.class, "arbitration-case");
            if (caseData == null || caseData.getCaseTitle() == null || caseData.getCaseTitle().isBlank()) {
                log.warn("LLM 生成仲裁案件为空，使用降级案件");
                return buildFallbackCase(kp);
            }
            log.info("仲裁案件生成成功: {}", caseData.getCaseTitle());
            return caseData;
        } catch (Exception e) {
            log.error("仲裁案件生成失败，使用降级案件: {}", e.getMessage());
            return buildFallbackCase(kp);
        }
    }

    /**
     * 收集知识点内容作为案件素材（当前知识点 + 父级章节 + 同级兄弟节点名称）。
     */
    private String buildKnowledgeContext(UUID kpId) {
        KnowledgePoint kp = knowledgePointRepository.findById(kpId).orElse(null);
        if (kp == null) {
            return "（知识点不存在）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("■ 知识点：").append(kp.getName()).append("\n");
        if (kp.getDescription() != null && !kp.getDescription().isBlank()) {
            sb.append("概述：").append(kp.getDescription()).append("\n");
        }
        if (kp.getContent() != null && !kp.getContent().isBlank()) {
            sb.append("内容：").append(kp.getContent()).append("\n");
        }

        // 内容不足 → 扩展父级章节
        boolean contentRich = kp.getContent() != null && kp.getContent().length() > 60;
        if (!contentRich && kp.getParentKpId() != null) {
            KnowledgePoint parent = knowledgePointRepository.findById(kp.getParentKpId()).orElse(null);
            if (parent != null) {
                sb.append("\n■ 所属章节：").append(parent.getName()).append("\n");
                if (parent.getDescription() != null && !parent.getDescription().isBlank()) {
                    sb.append("章节概述：").append(parent.getDescription()).append("\n");
                }
                if (parent.getContent() != null && !parent.getContent().isBlank()) {
                    sb.append("章节内容：").append(parent.getContent()).append("\n");
                }
                // 同级兄弟知识点名称（扩展素材面）
                List<KnowledgePoint> siblings = knowledgePointRepository
                        .findByParentKpId(parent.getId());
                if (siblings.size() > 1) {
                    sb.append("本章节相关知识点：");
                    for (KnowledgePoint s : siblings) {
                        if (!s.getId().equals(kpId)) {
                            sb.append(s.getName()).append("、");
                        }
                    }
                    sb.setLength(sb.length() - 1);
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    /** 降级案件：基于知识点名称的通用案件。 */
    private ArbitrationCase buildFallbackCase(KnowledgePoint kp) {
        String title = kp != null ? kp.getName() : "本课";
        ArbitrationCase c = new ArbitrationCase();
        c.setCaseTitle("关于「" + title + "」相关问题的模拟仲裁案件");
        c.setFact("本案围绕「" + title + "」所涉及的问题展开。申请人（原告）认为自己吃了亏，"
                + "被申请人（被告）则认为事情没那么严重，双方各说各理，无法协商一致，申请仲裁。");
        c.setDisputes(List.of("申请人主张的事实是否属实", "被申请人是否应当承担责任、如何承担"));
        c.setLegalPoints(List.of("相关法律规定及其适用条件", "双方各自应承担的举证说明义务"));
        c.setPlaintiffName("申请人（原告）");
        c.setPlaintiffClaim("要求对方给个说法、赔偿自己的损失");
        c.setDefendantName("被申请人（被告）");
        c.setDefendantDefense("认为自己没有过错，事情是误会");
        c.setDifficulty(kp != null && kp.getDifficulty() != null ? kp.getDifficulty() : 3);
        return c;
    }

    /** 记录员开庭词 */
    private String buildOpeningSpeech(ArbitrationCase c) {
        return "仲裁庭现在开庭。本案为「" + c.getCaseTitle() + "」。\n"
                + "申请人（原告）主张：" + c.getPlaintiffClaim() + "。\n"
                + "被申请人（被告）意见：" + c.getDefendantDefense() + "。\n"
                + "争议焦点：" + String.join("；", c.getDisputes()) + "。\n"
                + "下面请申请人（原告）陈述。";
    }

    // ═══════════════════════════════════════════════════════════
    //  庭审对话（降智引擎）
    // ═══════════════════════════════════════════════════════════

    /**
     * 仲裁人（学生）发言 → 记录消息 → AI（老百姓原/被告）回应。
     */
    @Transactional
    public Map<String, Object> sendMessage(UUID kpId, UUID studentId,
                                           ArbitrationPhase phase, String content) {
        ArbitrationSession session = requireSession(kpId, studentId, phase);
        if (session.getStatus() != ArbitrationStatus.HEARING
                && session.getStatus() != ArbitrationStatus.OPENING) {
            throw new IllegalArgumentException("当前阶段不可发言（status=" + session.getStatus() + "）");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("发言内容不能为空");
        }

        List<ArbitrationMessage> all = messageRepository
                .findBySessionIdOrderByRoundSeqAscCreatedAtAsc(session.getId());
        int nextRound = all.isEmpty() ? 0 : all.get(all.size() - 1).getRoundSeq() + 1;

        // 1. 记录仲裁人发言
        addMessage(session, ArbitrationRole.ARBITER_STUDENT, content.trim(), nextRound);

        // 2. 判断回应方：含"被告/被申请人"→被告；含"原告/申请人"→原告；否则按上一 AI 角色交替
        ArbitrationRole nextSpeaker = determineRespondent(session, content);
        String reply = speak(session, nextSpeaker, "仲裁员刚才的发言是：「" + content.trim()
                + "」。请作为本角色用老百姓的大白话正面回应（60-180 字）。");
        addMessage(session, nextSpeaker, reply, nextRound + 1);

        return sessionDetail(session);
    }

    /** 判断 AI 回应方。 */
    private ArbitrationRole determineRespondent(ArbitrationSession session, String arbiterContent) {
        boolean mentionDefendant = arbiterContent.contains("被告") || arbiterContent.contains("被申请人");
        boolean mentionPlaintiff = arbiterContent.contains("原告") || arbiterContent.contains("申请人");
        if (mentionDefendant && !mentionPlaintiff) {
            return ArbitrationRole.DEFENDANT_AI;
        }
        if (mentionPlaintiff && !mentionDefendant) {
            return ArbitrationRole.PLAINTIFF_AI;
        }
        List<ArbitrationMessage> all = messageRepository
                .findBySessionIdOrderByRoundSeqAscCreatedAtAsc(session.getId());
        for (int i = all.size() - 1; i >= 0; i--) {
            ArbitrationRole r = all.get(i).getRole();
            if (r == ArbitrationRole.PLAINTIFF_AI) {
                return ArbitrationRole.DEFENDANT_AI;
            }
            if (r == ArbitrationRole.DEFENDANT_AI) {
                return ArbitrationRole.PLAINTIFF_AI;
            }
        }
        return ArbitrationRole.PLAINTIFF_AI;
    }

    /**
     * 进入下一仲裁环节（记录员播报 + 对应角色自动发言）。
     */
    @Transactional
    public Map<String, Object> nextStage(UUID kpId, UUID studentId, ArbitrationPhase phase) {
        ArbitrationSession session = requireSession(kpId, studentId, phase);
        if (session.getStatus() != ArbitrationStatus.HEARING) {
            throw new IllegalArgumentException("当前阶段不可切换环节（status=" + session.getStatus() + "）");
        }
        int stage = Math.min(session.getStageIndex() + 1, 4);
        session.setStageIndex(stage);
        // 进入「裁决」环节 → 状态置为 AWARD_READY
        if (stage == 4) {
            session.setStatus(ArbitrationStatus.AWARD_READY);
        }
        sessionRepository.save(session);

        String announcement = switch (stage) {
            case 1 -> "本庭现在进入「答辩」环节。请被申请人（被告）答辩。";
            case 2 -> "本庭现在进入「举证质证」环节。请双方就证据和事实发表意见。";
            case 3 -> "本庭现在进入「辩论」环节。双方可围绕争议焦点充分表达自己的看法。";
            case 4 -> "辩论结束。请仲裁员（你）作出最终裁决。";
            default -> "";
        };
        addMessage(session, ArbitrationRole.CLERK, announcement, currentMaxRound(session) + 1);

        // 环节 1（答辩）：被告自动答辩；环节 2/3（举证/辩论）：原告先发言
        if (stage == 1) {
            String reply = speak(session, ArbitrationRole.DEFENDANT_AI,
                    "请作为被申请人（被告）进行答辩：讲讲自己的道理，为什么认为自己没错（60-180 字，大白话）。");
            addMessage(session, ArbitrationRole.DEFENDANT_AI, reply, currentMaxRound(session) + 1);
        } else if (stage == 2 || stage == 3) {
            String instruction = stage == 2
                    ? "请作为申请人（原告）说说自己有哪些证据或依据，说明为什么对方该赔（60-180 字，大白话）。"
                    : "请作为申请人（原告）发表最后的辩论意见，把最想说的话说出来（60-180 字，大白话）。";
            String reply = speak(session, ArbitrationRole.PLAINTIFF_AI, instruction);
            addMessage(session, ArbitrationRole.PLAINTIFF_AI, reply, currentMaxRound(session) + 1);
        }

        return sessionDetail(session);
    }

    // ═══════════════════════════════════════════════════════════
    //  裁决与报告
    // ═══════════════════════════════════════════════════════════

    /**
     * 学生（仲裁人）提交裁决书（结构化：result + reason）。
     */
    @Transactional
    public Map<String, Object> submitAward(UUID kpId, UUID studentId, ArbitrationAwardRequest request) {
        if (request == null || request.getResult() == null || request.getResult().isBlank()) {
            throw new IllegalArgumentException("裁决结果不能为空");
        }
        ArbitrationPhase phase = request.getPhase() != null ? request.getPhase() : ArbitrationPhase.POST;
        ArbitrationSession session = requireSession(kpId, studentId, phase);
        if (session.getAward() != null && !session.getAward().isBlank()) {
            throw new IllegalArgumentException("本阶段已提交过裁决书");
        }

        String result = request.getResult().trim().toUpperCase();
        if (!List.of("SUPPORT", "REJECT", "PARTIAL").contains(result)) {
            throw new IllegalArgumentException("裁决结果非法，应为 SUPPORT/REJECT/PARTIAL");
        }

        Map<String, Object> award = new LinkedHashMap<>();
        award.put("result", result);
        award.put("reason", request.getReason() != null ? request.getReason().trim() : "");
        award.put("phase", phase.name());
        session.setAward(toJson(award));
        session.setStatus(ArbitrationStatus.AWARDED);
        sessionRepository.save(session);

        // 裁决完成，记录员记录
        String clerkNote = "本庭已收到仲裁员的裁决书，本场仲裁结束。";
        addMessage(session, ArbitrationRole.CLERK, clerkNote, currentMaxRound(session) + 1);

        return sessionDetail(session);
    }

    /**
     * 生成分析报告（要求 PRE + POST 两份裁决齐全；AI 对比生成）。
     */
    @Transactional
    public Map<String, Object> generateReport(UUID kpId, UUID studentId) {
        ArbitrationSession pre = sessionRepository
                .findByKnowledgePointIdAndStudentIdAndPhase(kpId, studentId, ArbitrationPhase.PRE)
                .orElseThrow(() -> new IllegalArgumentException("尚未参加课前仲裁（PRE）"));
        ArbitrationSession post = sessionRepository
                .findByKnowledgePointIdAndStudentIdAndPhase(kpId, studentId, ArbitrationPhase.POST)
                .orElseThrow(() -> new IllegalArgumentException("尚未参加课后仲裁（POST）"));

        if (pre.getAward() == null || pre.getAward().isBlank()
                || post.getAward() == null || post.getAward().isBlank()) {
            throw new IllegalArgumentException("需课前、课后两次裁决均提交后才能生成分析报告");
        }

        ArbitrationCase caseData = parseCase(pre.getCaseContent() != null
                ? pre.getCaseContent() : post.getCaseContent());
        String report = generateReportText(caseData, pre, post);

        pre.setReport(report);
        pre.setStatus(ArbitrationStatus.REPORTED);
        post.setReport(report);
        post.setStatus(ArbitrationStatus.REPORTED);
        sessionRepository.save(pre);
        sessionRepository.save(post);

        return Map.of("report", report);
    }

    /** 获取分析报告（含两份裁决 + 报告正文）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> getReport(UUID kpId, UUID studentId) {
        ArbitrationSession pre = sessionRepository
                .findByKnowledgePointIdAndStudentIdAndPhase(kpId, studentId, ArbitrationPhase.PRE)
                .orElseThrow(() -> new IllegalArgumentException("尚未参加课前仲裁（PRE）"));
        ArbitrationSession post = sessionRepository
                .findByKnowledgePointIdAndStudentIdAndPhase(kpId, studentId, ArbitrationPhase.POST)
                .orElseThrow(() -> new IllegalArgumentException("尚未参加课后仲裁（POST）"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("case", parseCase(pre.getCaseContent() != null
                ? pre.getCaseContent() : post.getCaseContent()));
        result.put("preAward", parseAward(pre.getAward()));
        result.put("postAward", parseAward(post.getAward()));
        result.put("report", post.getReport());
        return result;
    }

    /**
     * 查询知识点仲裁状态（供学习页入口卡片展示）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatus(UUID kpId, UUID studentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgePointId", kpId);

        ArbitrationSession pre = sessionRepository
                .findByKnowledgePointIdAndStudentIdAndPhase(kpId, studentId, ArbitrationPhase.PRE)
                .orElse(null);
        ArbitrationSession post = sessionRepository
                .findByKnowledgePointIdAndStudentIdAndPhase(kpId, studentId, ArbitrationPhase.POST)
                .orElse(null);

        boolean preAwarded = pre != null && pre.getAward() != null && !pre.getAward().isBlank();
        boolean postAwarded = post != null && post.getAward() != null && !post.getAward().isBlank();

        result.put("pre", preAwarded ? "AWARDED" : (pre != null ? pre.getStatus().name() : "NONE"));
        result.put("post", postAwarded ? "AWARDED" : (post != null ? post.getStatus().name() : "NONE"));
        result.put("preAwarded", preAwarded);
        result.put("postAwarded", postAwarded);
        result.put("reportReady", preAwarded && postAwarded);
        result.put("report", post != null ? post.getReport() : null);
        return result;
    }

    /** 生成报告文本（LLM 对比两份裁决；失败降级为结构化摘要）。 */
    private String generateReportText(ArbitrationCase caseData, ArbitrationSession pre, ArbitrationSession post) {
        String preA = pre.getAward();
        String postA = post.getAward();

        String systemPrompt = "你是一名法学教育分析师。请对比学生在同一模拟仲裁案件上的两次裁决"
                + "（课前裁决 vs 课后裁决），撰写一份学习成长分析报告。"
                + "报告需包含三部分："
                + "1.【认知变化轨迹】分析学生从课前到课后的观点、理由变化（若两份裁决相同，需明确指出「未发生明显认知变化」）；"
                + "2.【知识掌握评估】对照案件涉及的知识要点，评估学生对知识点的掌握情况；"
                + "3.【学习建议】给出 2-3 条具体可行的改进建议。"
                + "使用 Markdown 格式，语气鼓励、客观，总字数 400-700 字。";

        String caseBrief = "";
        if (caseData != null) {
            caseBrief = "案件：" + caseData.getCaseTitle() + "\n"
                    + "事实：" + caseData.getFact() + "\n"
                    + "争议焦点：" + String.join("；", caseData.getDisputes()) + "\n"
                    + "知识要点：" + String.join("；", caseData.getLegalPoints()) + "\n";
        }

        String userPrompt = caseBrief
                + "\n【课前裁决】\n" + preA
                + "\n\n【课后裁决】\n" + postA;

        try {
            LLMResponse resp = llmService.ask(systemPrompt, userPrompt);
            String content = resp.getContent();
            if (content == null || content.isBlank()) {
                return buildFallbackReport(caseData, preA, postA);
            }
            return content.trim();
        } catch (Exception e) {
            log.error("仲裁分析报告生成失败，使用降级报告: {}", e.getMessage());
            return buildFallbackReport(caseData, preA, postA);
        }
    }

    private String buildFallbackReport(ArbitrationCase caseData, String preA, String postA) {
        return "## 案例分析学习报告\n\n"
                + "### 1. 认知变化轨迹\n"
                + "已完成课前与课后两次模拟仲裁裁决，建议对比两次裁决理由，反思自己的判断依据。\n\n"
                + "### 2. 知识掌握评估\n"
                + "两次裁决均已提交，建议结合本课知识点与案件争议焦点复习。\n\n"
                + "### 3. 学习建议\n"
                + "- 对照本课知识点梳理裁决依据\n"
                + "- 尝试站在对方立场再写一份抗辩意见\n"
                + "- 结合生活中的真实纠纷检验自己的判断\n";
    }

    // ═══════════════════════════════════════════════════════════
    //  内部：消息 / 降智提示词 / 校验 / 序列化
    // ═══════════════════════════════════════════════════════════

    private ArbitrationMessage addMessage(ArbitrationSession session, ArbitrationRole role,
                                          String content, int roundSeq) {
        ArbitrationMessage msg = new ArbitrationMessage();
        msg.setSessionId(session.getId());
        msg.setRole(role);
        msg.setContent(content);
        msg.setRoundSeq(roundSeq);
        return messageRepository.save(msg);
    }

    /**
     * 生成某角色的发言（降智老百姓人设 + 完整案件上下文 + 庭审历史）。
     */
    private String speak(ArbitrationSession session, ArbitrationRole role, String extraInstruction) {
        ArbitrationCase caseData = parseCase(session.getCaseContent());
        if (caseData == null) {
            return "（仲裁庭暂时无法获取案件信息，请稍后重试。）";
        }

        String systemPrompt = buildRolePrompt(caseData, role);
        List<ChatMessage> messages = new ArrayList<>();

        // 最近 12 条庭审历史（含仲裁人发言），保证上下文连贯
        List<ArbitrationMessage> recent = messageRepository
                .findTop30BySessionIdOrderByRoundSeqDescCreatedAtDesc(session.getId());
        List<ArbitrationMessage> history = recent.size() > 12 ? recent.subList(0, 12) : recent;
        history.sort((a, b) -> {
            int c = Integer.compare(a.getRoundSeq(), b.getRoundSeq());
            return c != 0 ? c : a.getCreatedAt().compareTo(b.getCreatedAt());
        });
        for (ArbitrationMessage m : history) {
            // OpenAI 兼容 API 仅接受 system/user/assistant 角色，发言方用内容前缀区分
            String speaker = switch (m.getRole()) {
                case PLAINTIFF_AI -> "申请人（原告）";
                case DEFENDANT_AI -> "被申请人（被告）";
                case ARBITER_STUDENT -> "仲裁员";
                case CLERK -> "记录员";
            };
            messages.add(new ChatMessage("user", "【" + speaker + "】" + m.getContent()));
        }
        messages.add(new ChatMessage("user", "【仲裁员】" + extraInstruction));

        try {
            LLMResponse resp = llmService.chat(systemPrompt, messages);
            String content = resp.getContent();
            if (content == null || content.isBlank()) {
                return buildFallbackSpeech(caseData, role);
            }
            return content.trim();
        } catch (Exception e) {
            log.error("AI 发言失败（{}）: {}", role, e.getMessage());
            return buildFallbackSpeech(caseData, role);
        }
    }

    private String buildFallbackSpeech(ArbitrationCase c, ArbitrationRole role) {
        if (role == ArbitrationRole.PLAINTIFF_AI) {
            return "我来说两句。事情就是这么个事情，我实实在在吃了亏，"
                    + c.getPlaintiffClaim()
                    + "。大家给评评理，这钱该不该退？";
        }
        return "我也说两句。事情不是他说的那样，我这边没什么问题，"
                + c.getDefendantDefense()
                + "。大家说说，这能怪我吗？";
    }

    /**
     * 角色卡 system prompt（原告 / 被告）— 核心「降智」设计。
     */
    private String buildRolePrompt(ArbitrationCase c, ArbitrationRole role) {
        boolean plaintiff = role == ArbitrationRole.PLAINTIFF_AI;
        String roleDesc = plaintiff
                ? "你是本案申请人（原告）「" + c.getPlaintiffName() + "」。" + c.getPlaintiffClaim()
                : "你是本案被申请人（被告）「" + c.getDefendantName() + "」。" + c.getDefendantDefense();

        return "你正在参加一场面向中小学生的模拟仲裁活动，扮演" + (plaintiff ? "申请人（原告）" : "被申请人（被告）") + "。\n"
                + "你的人设：一个普通老百姓，没什么法律知识，第一次进仲裁庭，只会用大白话讲自己的理。\n"
                + roleDesc + "。\n\n"
                + "【案件事实】\n" + c.getFact() + "\n\n"
                + "【争议焦点】\n" + String.join("\n", c.getDisputes()) + "\n\n"
                + "说话要求（非常重要）：\n"
                + "1. 用最平常的大白话，就像跟街坊邻居倒苦水一样，可以带情绪、可以反问、可以重复强调；\n"
                + "2. 【禁止】引用法条原文，禁止使用「合同」「欺诈」「举证责任」「赔偿标准」「诉讼时效」等法律术语；\n"
                + "   想表达法律意思时用大白话（如「这是骗人」「得给个说法」「该赔的就得赔」）；\n"
                + "3. 基于眼前事实和生活经验说话，不讲大道理；\n"
                + "4. 每次发言 60-180 字，短句为主；\n"
                + "5. 仲裁员（学生）的提问必须正面回应；不得代替对方发言；不得提及「我是AI/模型」。";
    }

    /** 解析会话中的案件 JSON。 */
    private ArbitrationCase parseCase(String caseContent) {
        if (caseContent == null || caseContent.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(caseContent, ArbitrationCase.class);
        } catch (Exception e) {
            log.error("解析案件内容失败: {}", e.getMessage());
            return null;
        }
    }

    /** 解析裁决书 JSON。 */
    private Map<String, Object> parseAward(String award) {
        if (award == null || award.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(award, Map.class);
        } catch (Exception e) {
            return Map.of("raw", award);
        }
    }

    private Map<String, Object> sessionDetail(ArbitrationSession session) {
        Map<String, Object> dto = session.toDto();
        dto.put("case", parseCase(session.getCaseContent()));
        List<Map<String, Object>> messages = messageRepository
                .findBySessionIdOrderByRoundSeqAscCreatedAtAsc(session.getId())
                .stream().map(ArbitrationMessage::toDto).toList();
        dto.put("messages", messages);
        dto.put("awardData", parseAward(session.getAward()));
        return dto;
    }

    private ArbitrationSession requireSession(UUID kpId, UUID studentId, ArbitrationPhase phase) {
        return sessionRepository
                .findByKnowledgePointIdAndStudentIdAndPhase(kpId, studentId, phase)
                .orElseThrow(() -> new IllegalArgumentException(
                        "仲裁会话不存在，请先启动（knowledgePointId=" + kpId + ", phase=" + phase + "）"));
    }

    private int currentMaxRound(ArbitrationSession session) {
        List<ArbitrationMessage> all = messageRepository
                .findBySessionIdOrderByRoundSeqAscCreatedAtAsc(session.getId());
        return all.isEmpty() ? 0 : all.get(all.size() - 1).getRoundSeq();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
