package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.MootCourtCase;
import com.edumentor.classroom.dto.MootCourtJudgmentRequest;
import com.edumentor.classroom.entity.Classroom;
import com.edumentor.classroom.entity.ClassroomProgress;
import com.edumentor.classroom.entity.MootCourtMessage;
import com.edumentor.classroom.entity.MootCourtSession;
import com.edumentor.classroom.entity.enums.ClassroomStatus;
import com.edumentor.classroom.entity.enums.MootCourtPhase;
import com.edumentor.classroom.entity.enums.MootCourtRole;
import com.edumentor.classroom.entity.enums.MootCourtStatus;
import com.edumentor.classroom.entity.enums.ProgressStatus;
import com.edumentor.classroom.repository.ClassroomProgressRepository;
import com.edumentor.classroom.repository.ClassroomRepository;
import com.edumentor.classroom.repository.MootCourtMessageRepository;
import com.edumentor.classroom.repository.MootCourtSessionRepository;
import com.edumentor.course.entity.Course;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.engine.llm.ChatMessage;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.engine.llm.LLMResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟法庭服务。
 * <p>
 * 核心模型：一个案件，两场庭审（PRE 课前 / POST 课后），一份报告。
 * <ul>
 *   <li>案例生成：以课堂关联知识点为素材，LLM 结构化生成案件（MootCourtCase）</li>
 *   <li>庭审对话：学生扮演法官，AI 扮演原被告，多轮对抗 + 环节编排</li>
 *   <li>判决与报告：两份判决齐全后手动生成对比报告</li>
 * </ul>
 * </p>
 */
@Service
public class MootCourtService {

    private static final Logger log = LoggerFactory.getLogger(MootCourtService.class);

    /** 庭审环节名称 */
    public static final String[] STAGE_NAMES = {"陈述", "答辩", "举证质证", "法庭辩论", "判决"};

    private final MootCourtSessionRepository sessionRepository;
    private final MootCourtMessageRepository messageRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomProgressRepository progressRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CourseRepository courseRepository;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public MootCourtService(MootCourtSessionRepository sessionRepository,
                            MootCourtMessageRepository messageRepository,
                            ClassroomRepository classroomRepository,
                            ClassroomProgressRepository progressRepository,
                            KnowledgePointRepository knowledgePointRepository,
                            CourseRepository courseRepository,
                            LLMService llmService,
                            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.classroomRepository = classroomRepository;
        this.progressRepository = progressRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.courseRepository = courseRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════
    //  M2：会话与案例
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取或创建法庭会话。
     * <p>首次进入（PRE 或 POST）时：生成案件（若 PRE 已生成则 POST 复用同一案件）→
     * 书记员宣布开庭 → 原告首次陈述 → 进入 HEARING。</p>
     *
     * @param classroomId 课堂 ID
     * @param studentId   学生 ID
     * @param phase       阶段（PRE/POST）
     * @return 会话详情（含 caseContent + 庭审消息列表）
     */
    @Transactional
    public Map<String, Object> start(UUID classroomId, UUID studentId, MootCourtPhase phase) {
        Classroom classroom = requirePublishedClassroom(classroomId);
        validatePhase(classroomId, studentId, phase);

        MootCourtSession session = sessionRepository
                .findByClassroomIdAndStudentIdAndPhase(classroomId, studentId, phase)
                .orElse(null);

        if (session == null) {
            session = new MootCourtSession();
            session.setClassroomId(classroomId);
            session.setStudentId(studentId);
            session.setPhase(phase);
            session.setStatus(MootCourtStatus.CASE_GENERATING);
            session.setStageIndex(0);
            session = sessionRepository.save(session);

            // 案例生成：PRE 先生成；POST 复用 PRE 的案件
            MootCourtCase caseData = loadOrGenerateCase(classroomId);
            session.setCaseContent(toJson(caseData));
            session.setStatus(MootCourtStatus.OPENING);
            sessionRepository.save(session);

            // 书记员宣布开庭（模板，不调 LLM）
            addMessage(session, MootCourtRole.CLERK, buildOpeningSpeech(caseData), 0);
            // 原告首次陈述（LLM）
            String plaintiffSpeech = speak(session, MootCourtRole.PLAINTIFF_AI,
                    "请作为本角色进行首次陈述，清晰说明你的立场、主张与依据（150-250 字）。");
            addMessage(session, MootCourtRole.PLAINTIFF_AI, plaintiffSpeech, 1);

            session.setStatus(MootCourtStatus.HEARING);
            sessionRepository.save(session);
        }

        return sessionDetail(session);
    }

    /**
     * 查询会话详情（含案件 + 消息列表）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSession(UUID classroomId, UUID studentId, MootCourtPhase phase) {
        MootCourtSession session = requireSession(classroomId, studentId, phase);
        return sessionDetail(session);
    }

    // ═══════════════════════════════════════════════════════════
    //  内部：案例生成
    // ═══════════════════════════════════════════════════════════

    /**
     * 加载已有案件（POST 复用 PRE 的案件）；都没有则按课堂知识点生成。
     */
    private MootCourtCase loadOrGenerateCase(UUID classroomId) {
        List<MootCourtSession> existing = sessionRepository
                .findByClassroomIdAndPhaseOrderByCreatedAtDesc(classroomId, MootCourtPhase.PRE);
        for (MootCourtSession s : existing) {
            if (s.getCaseContent() != null && !s.getCaseContent().isBlank()) {
                try {
                    return objectMapper.readValue(s.getCaseContent(), MootCourtCase.class);
                } catch (Exception e) {
                    log.warn("解析 PRE 案件失败，重新生成: {}", e.getMessage());
                }
            }
        }
        return generateCase(classroomId);
    }

    /**
     * 按课堂知识点生成案件（LLM 结构化输出，失败降级为通识性案件）。
     */
    private MootCourtCase generateCase(UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId).orElse(null);
        if (classroom == null) {
            throw new IllegalArgumentException("课堂不存在: " + classroomId);
        }

        String knowledgeContext = buildKnowledgeContext(classroom);
        String courseName = "";
        if (classroom.getCourseId() != null) {
            courseName = courseRepository.findById(classroom.getCourseId())
                    .map(Course::getName).orElse("");
        }

        String systemPrompt = "你是一名资深法学教育专家和模拟法庭案件设计者。请根据提供的课堂知识点内容，"
                + "设计一个贴近该知识点的模拟法庭案件。要求："
                + "1. 案件背景应自然融入知识点的核心概念，案情完整、可信；"
                + "2. 争议焦点（2-3 个）必须紧扣知识点；"
                + "3. 原告与被告立场清晰对立，诉求与抗辩具体明确；"
                + "4. 案件难度与知识点难度匹配（1-5）；"
                + "5. 使用通俗易懂的中文，适合中小学模拟法庭。";

        String userPrompt = "【课程名称】" + courseName + "\n"
                + "【课堂标题】" + (classroom.getTitle() != null ? classroom.getTitle() : "")
                + "\n【课堂描述】" + (classroom.getDescription() != null ? classroom.getDescription() : "")
                + "\n【知识点内容】\n" + knowledgeContext;

        try {
            MootCourtCase caseData = llmService.askStructured(systemPrompt, userPrompt,
                    MootCourtCase.class, "moot-court-case");
            if (caseData == null || caseData.getCaseTitle() == null || caseData.getCaseTitle().isBlank()) {
                log.warn("LLM 生成案件为空，使用降级案件");
                return buildFallbackCase(classroom);
            }
            log.info("模拟法庭案件生成成功: {}", caseData.getCaseTitle());
            return caseData;
        } catch (Exception e) {
            log.error("模拟法庭案件生成失败，使用降级案件: {}", e.getMessage());
            return buildFallbackCase(classroom);
        }
    }

    /**
     * 收集课堂关联知识点的名称+内容（单知识点 / 多知识点聚合 metadata.knowledgePointIds）。
     */
    private String buildKnowledgeContext(Classroom classroom) {
        List<UUID> kpIds = new ArrayList<>();
        if (classroom.getKnowledgePointId() != null) {
            kpIds.add(classroom.getKnowledgePointId());
        }
        // 多知识点聚合课堂：metadata.knowledgePointIds
        if (classroom.getMetadata() != null && !classroom.getMetadata().isBlank()) {
            try {
                Map<String, Object> meta = objectMapper.readValue(classroom.getMetadata(), Map.class);
                Object ids = meta.get("knowledgePointIds");
                if (ids instanceof List<?> list) {
                    for (Object o : list) {
                        try {
                            UUID id = UUID.fromString(o.toString());
                            if (!kpIds.contains(id)) {
                                kpIds.add(id);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析课堂 metadata 失败: {}", e.getMessage());
            }
        }

        if (kpIds.isEmpty()) {
            return "（课堂未关联具体知识点，请基于课堂标题与描述设计通识性法律案件）";
        }

        StringBuilder sb = new StringBuilder();
        for (KnowledgePoint kp : knowledgePointRepository.findAllById(kpIds)) {
            sb.append("■ ").append(kp.getName()).append("\n");
            if (kp.getDescription() != null && !kp.getDescription().isBlank()) {
                sb.append("概述：").append(kp.getDescription()).append("\n");
            }
            if (kp.getContent() != null && !kp.getContent().isBlank()) {
                sb.append("内容：").append(kp.getContent()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 降级案件：基于课堂标题/描述的通用案件。
     */
    private MootCourtCase buildFallbackCase(Classroom classroom) {
        String title = classroom.getTitle() != null ? classroom.getTitle() : "本课";
        MootCourtCase c = new MootCourtCase();
        c.setCaseTitle("关于「" + title + "」相关法律问题的模拟案件");
        c.setFact("本案围绕课堂主题「" + title + "」所涉及的法律问题展开。"
                + "原告主张自身合法权益受到侵害，被告则认为其行为于法有据。双方就核心法律争议各执一词，诉至法院。");
        c.setDisputes(List.of("本案双方的核心行为是否违反了相关法律规定", "原告的诉讼请求是否有事实与法律依据"));
        c.setLegalPoints(List.of("相关法律规定及其适用条件", "举证责任分配规则"));
        c.setPlaintiffName("原告");
        c.setPlaintiffClaim("请求法院依法判令被告停止侵害、赔偿损失");
        c.setDefendantName("被告");
        c.setDefendantDefense("主张自身行为合法合规，不存在侵害事实");
        c.setDifficulty(classroom.getDifficulty() != null ? classroom.getDifficulty() : 3);
        return c;
    }

    /**
     * 书记员开庭词（模板）。
     */
    private String buildOpeningSpeech(MootCourtCase c) {
        return "现在开庭。本庭依法公开审理「" + c.getCaseTitle() + "」。"
                + "原告诉讼请求：" + c.getPlaintiffClaim() + "。"
                + "被告抗辩意见：" + c.getDefendantDefense() + "。"
                + "本案争议焦点：" + String.join("；", c.getDisputes()) + "。"
                + "下面由原告进行陈述。";
    }

    // ═══════════════════════════════════════════════════════════
    //  M3：庭审对话
    // ═══════════════════════════════════════════════════════════

    /**
     * 法官（学生）发言 → 记录消息 → AI（原/被告）回应。
     *
     * @return 会话详情（含新增消息）
     */
    @Transactional
    public Map<String, Object> sendMessage(UUID classroomId, UUID studentId,
                                           MootCourtPhase phase, String content) {
        MootCourtSession session = requireSession(classroomId, studentId, phase);
        if (session.getStatus() != MootCourtStatus.HEARING
                && session.getStatus() != MootCourtStatus.OPENING) {
            throw new IllegalArgumentException("当前阶段不可发言（status=" + session.getStatus() + "）");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("发言内容不能为空");
        }

        List<MootCourtMessage> all = messageRepository
                .findBySessionIdOrderByRoundSeqAscCreatedAtAsc(session.getId());
        int nextRound = all.isEmpty() ? 0 : all.get(all.size() - 1).getRoundSeq() + 1;

        // 1. 记录法官发言
        addMessage(session, MootCourtRole.JUDGE_STUDENT, content.trim(), nextRound);

        // 2. 判断回应方：发言中含"被告"→被告回应；含"原告"→原告回应；否则按上一 AI 角色交替
        MootCourtRole nextSpeaker = determineRespondent(session, content);
        String reply = speak(session, nextSpeaker, "法官刚才的发言是：「" + content.trim()
                + "」。请作为本角色正面回应法官的发言或提问（100-250 字）。");
        addMessage(session, nextSpeaker, reply, nextRound + 1);

        return sessionDetail(session);
    }

    /**
     * 判断 AI 回应方。
     */
    private MootCourtRole determineRespondent(MootCourtSession session, String judgeContent) {
        if (judgeContent.contains("被告") && !judgeContent.contains("原告")) {
            return MootCourtRole.DEFENDANT_AI;
        }
        if (judgeContent.contains("原告") && !judgeContent.contains("被告")) {
            return MootCourtRole.PLAINTIFF_AI;
        }
        List<MootCourtMessage> all = messageRepository
                .findBySessionIdOrderByRoundSeqAscCreatedAtAsc(session.getId());
        for (int i = all.size() - 1; i >= 0; i--) {
            MootCourtRole r = all.get(i).getRole();
            if (r == MootCourtRole.PLAINTIFF_AI) {
                return MootCourtRole.DEFENDANT_AI;
            }
            if (r == MootCourtRole.DEFENDANT_AI) {
                return MootCourtRole.PLAINTIFF_AI;
            }
        }
        return MootCourtRole.PLAINTIFF_AI;
    }

    /**
     * 进入下一庭审环节（书记员播报 + 对应角色自动发言）。
     */
    @Transactional
    public Map<String, Object> nextStage(UUID classroomId, UUID studentId, MootCourtPhase phase) {
        MootCourtSession session = requireSession(classroomId, studentId, phase);
        if (session.getStatus() != MootCourtStatus.HEARING) {
            throw new IllegalArgumentException("当前阶段不可切换环节（status=" + session.getStatus() + "）");
        }
        int stage = Math.min(session.getStageIndex() + 1, 4);
        session.setStageIndex(stage);
        // 进入「判决」环节 → 状态置为 JUDGMENT_READY（等待法官提交判决）
        if (stage == 4) {
            session.setStatus(MootCourtStatus.JUDGMENT_READY);
        }
        sessionRepository.save(session);

        String announcement = switch (stage) {
            case 1 -> "本庭现在进入「答辩」环节。请被告进行答辩。";
            case 2 -> "本庭现在进入「举证质证」环节。请双方就证据进行举证与质证。";
            case 3 -> "本庭现在进入「法庭辩论」环节。双方可就争议焦点充分辩论。";
            case 4 -> "庭审辩论终结。请法官（学生）进行最终判决。";
            default -> "";
        };
        addMessage(session, MootCourtRole.CLERK, announcement, currentMaxRound(session) + 1);

        // 环节 1（答辩）：被告自动答辩；环节 2/3（举证/辩论）：原告先发言
        if (stage == 1) {
            String reply = speak(session, MootCourtRole.DEFENDANT_AI, "请作为被告进行答辩陈述（150-250 字）。");
            addMessage(session, MootCourtRole.DEFENDANT_AI, reply, currentMaxRound(session) + 1);
        } else if (stage == 2 || stage == 3) {
            String instruction = stage == 2
                    ? "请作为原告进行举证：出示证据并说明其证明目的（100-200 字）。"
                    : "请作为原告发表本轮法庭辩论意见（100-200 字）。";
            String reply = speak(session, MootCourtRole.PLAINTIFF_AI, instruction);
            addMessage(session, MootCourtRole.PLAINTIFF_AI, reply, currentMaxRound(session) + 1);
        }

        return sessionDetail(session);
    }

    private int currentMaxRound(MootCourtSession session) {
        List<MootCourtMessage> all = messageRepository
                .findBySessionIdOrderByRoundSeqAscCreatedAtAsc(session.getId());
        return all.isEmpty() ? 0 : all.get(all.size() - 1).getRoundSeq();
    }

    // ═══════════════════════════════════════════════════════════
    //  M4：判决与报告
    // ═══════════════════════════════════════════════════════════

    /**
     * 学生（法官）提交判决（结构化：result + reason）。
     */
    @Transactional
    public Map<String, Object> submitJudgment(UUID classroomId, UUID studentId,
                                              MootCourtJudgmentRequest request) {
        if (request == null || request.getResult() == null || request.getResult().isBlank()) {
            throw new IllegalArgumentException("判决结果不能为空");
        }
        MootCourtPhase phase = request.getPhase() != null ? request.getPhase() : MootCourtPhase.POST;
        MootCourtSession session = requireSession(classroomId, studentId, phase);
        if (session.getJudgment() != null && !session.getJudgment().isBlank()) {
            throw new IllegalArgumentException("本阶段已提交过判决");
        }

        String result = request.getResult().trim().toUpperCase();
        if (!List.of("SUPPORT", "REJECT", "PARTIAL").contains(result)) {
            throw new IllegalArgumentException("判决结果非法，应为 SUPPORT/REJECT/PARTIAL");
        }

        Map<String, Object> judgment = new java.util.LinkedHashMap<>();
        judgment.put("result", result);
        judgment.put("reason", request.getReason() != null ? request.getReason().trim() : "");
        judgment.put("phase", phase.name());
        session.setJudgment(toJson(judgment));
        session.setStatus(MootCourtStatus.JUDGED);
        sessionRepository.save(session);

        // 判决完成，书记员记录
        String clerkNote = "本庭已收到法官的判决书，庭审结束。";
        addMessage(session, MootCourtRole.CLERK, clerkNote, currentMaxRound(session) + 1);

        return sessionDetail(session);
    }

    /**
     * 生成分析报告（要求 PRE + POST 两份判决齐全；AI 对比生成）。
     */
    @Transactional
    public Map<String, Object> generateReport(UUID classroomId, UUID studentId) {
        MootCourtSession pre = sessionRepository
                .findByClassroomIdAndStudentIdAndPhase(classroomId, studentId, MootCourtPhase.PRE)
                .orElseThrow(() -> new IllegalArgumentException("尚未参加课前法庭（PRE）"));
        MootCourtSession post = sessionRepository
                .findByClassroomIdAndStudentIdAndPhase(classroomId, studentId, MootCourtPhase.POST)
                .orElseThrow(() -> new IllegalArgumentException("尚未参加课后法庭（POST）"));

        if (pre.getJudgment() == null || pre.getJudgment().isBlank()
                || post.getJudgment() == null || post.getJudgment().isBlank()) {
            throw new IllegalArgumentException("需课前、课后两次判决均提交后才能生成报告");
        }

        MootCourtCase caseData = parseCase(pre.getCaseContent() != null ? pre.getCaseContent() : post.getCaseContent());
        String report = generateReportText(caseData, pre, post);

        // 报告写回两个会话，标记 REPORTED
        pre.setReport(report);
        pre.setStatus(MootCourtStatus.REPORTED);
        post.setReport(report);
        post.setStatus(MootCourtStatus.REPORTED);
        sessionRepository.save(pre);
        sessionRepository.save(post);

        return Map.of("report", report);
    }

    /**
     * 获取分析报告（含两份判决 + 报告正文）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReport(UUID classroomId, UUID studentId) {
        MootCourtSession pre = sessionRepository
                .findByClassroomIdAndStudentIdAndPhase(classroomId, studentId, MootCourtPhase.PRE)
                .orElseThrow(() -> new IllegalArgumentException("尚未参加课前法庭（PRE）"));
        MootCourtSession post = sessionRepository
                .findByClassroomIdAndStudentIdAndPhase(classroomId, studentId, MootCourtPhase.POST)
                .orElseThrow(() -> new IllegalArgumentException("尚未参加课后法庭（POST）"));

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("case", parseCase(pre.getCaseContent() != null ? pre.getCaseContent() : post.getCaseContent()));
        result.put("preJudgment", parseJudgment(pre.getJudgment()));
        result.put("postJudgment", parseJudgment(post.getJudgment()));
        result.put("report", post.getReport());
        return result;
    }

    /**
     * 生成报告文本（LLM 对比两份判决；失败降级为结构化摘要）。
     */
    private String generateReportText(MootCourtCase caseData, MootCourtSession pre, MootCourtSession post) {
        String preJ = pre.getJudgment();
        String postJ = post.getJudgment();

        String systemPrompt = "你是一名法学教育分析师。请对比学生在同一模拟法庭案件上的两次判决"
                + "（课前判决 vs 课后判决），撰写一份学习成长分析报告。"
                + "报告需包含三部分："
                + "1.【认知变化轨迹】分析学生从课前到课后的观点、理由变化（若两份判决相同，需明确指出「未发生明显认知变化」）；"
                + "2.【知识掌握评估】对照案件涉及的法律知识要点，评估学生对知识点的掌握情况；"
                + "3.【学习建议】给出 2-3 条具体可行的改进建议。"
                + "使用 Markdown 格式，语气鼓励、客观，总字数 400-700 字。";

        String caseBrief = "";
        if (caseData != null) {
            caseBrief = "案件：" + caseData.getCaseTitle() + "\n"
                    + "事实：" + caseData.getFact() + "\n"
                    + "争议焦点：" + String.join("；", caseData.getDisputes()) + "\n"
                    + "法律要点：" + String.join("；", caseData.getLegalPoints()) + "\n";
        }

        String userPrompt = caseBrief
                + "\n【课前判决】\n" + preJ
                + "\n\n【课后判决】\n" + postJ;

        try {
            LLMResponse resp = llmService.ask(systemPrompt, userPrompt);
            String content = resp.getContent();
            if (content == null || content.isBlank()) {
                return buildFallbackReport(caseData, preJ, postJ);
            }
            return content.trim();
        } catch (Exception e) {
            log.error("分析报告生成失败，使用降级报告: {}", e.getMessage());
            return buildFallbackReport(caseData, preJ, postJ);
        }
    }

    private String buildFallbackReport(MootCourtCase caseData, String preJ, String postJ) {
        return "## 模拟法庭学习报告\n\n"
                + "### 1. 认知变化轨迹\n"
                + "已完成课前与课后两次庭审判决，请在学习更多知识点后再次对比两次判决理由。\n\n"
                + "### 2. 知识掌握评估\n"
                + "两次判决均已提交，建议结合案件争议焦点复习相关知识点。\n\n"
                + "### 3. 学习建议\n"
                + "- 对照本课知识点梳理判决依据\n"
                + "- 尝试为相反立场撰写抗辩意见\n"
                + "- 结合真实案例检验自己的判断\n";
    }

    // ═══════════════════════════════════════════════════════════
    //  内部：消息 / 校验 / 序列化
    // ═══════════════════════════════════════════════════════════

    private MootCourtMessage addMessage(MootCourtSession session, MootCourtRole role,
                                        String content, int roundSeq) {
        MootCourtMessage msg = new MootCourtMessage();
        msg.setSessionId(session.getId());
        msg.setRole(role);
        msg.setContent(content);
        msg.setRoundSeq(roundSeq);
        return messageRepository.save(msg);
    }

    /**
     * 生成某角色的发言（带完整案件上下文 + 庭审历史）。
     */
    private String speak(MootCourtSession session, MootCourtRole role, String extraInstruction) {
        MootCourtCase caseData = parseCase(session.getCaseContent());
        if (caseData == null) {
            return "（AI 暂时无法获取案件信息，请稍后重试。）";
        }

        String systemPrompt = buildRolePrompt(caseData, role);
        List<ChatMessage> messages = new ArrayList<>();

        // 最近 12 条庭审历史（含法官发言），保证上下文连贯
        List<MootCourtMessage> recent = messageRepository
                .findTop30BySessionIdOrderByRoundSeqDescCreatedAtDesc(session.getId());
        List<MootCourtMessage> history = recent.size() > 12 ? recent.subList(0, 12) : recent;
        history.sort((a, b) -> {
            int c = Integer.compare(a.getRoundSeq(), b.getRoundSeq());
            return c != 0 ? c : a.getCreatedAt().compareTo(b.getCreatedAt());
        });
        for (MootCourtMessage m : history) {
            // OpenAI 兼容 API 仅接受 system/user/assistant 角色，发言方用内容前缀区分
            String speaker = switch (m.getRole()) {
                case PLAINTIFF_AI -> "原告";
                case DEFENDANT_AI -> "被告";
                case JUDGE_STUDENT -> "法官";
                case CLERK -> "书记员";
            };
            messages.add(new ChatMessage("user", "【" + speaker + "】" + m.getContent()));
        }
        messages.add(new ChatMessage("user", "【法官】" + extraInstruction));

        try {
            LLMResponse resp = llmService.chat(systemPrompt, messages);
            String content = resp.getContent();
            if (content == null || content.isBlank()) {
                return buildFallbackSpeech(caseData, role);
            }
            return content.trim();
        } catch (Exception e) {
            log.error("LLM 发言失败（{}）: {}", role, e.getMessage());
            return buildFallbackSpeech(caseData, role);
        }
    }

    private String buildFallbackSpeech(MootCourtCase c, MootCourtRole role) {
        if (role == MootCourtRole.PLAINTIFF_AI) {
            return "法官大人，原告认为被告的行为侵害了原告的合法权益。具体而言，" + c.getPlaintiffClaim()
                    + "。恳请法庭查明事实，依法支持原告的诉讼请求。";
        }
        return "法官大人，被告认为自身行为符合法律规定，不存在原告所述侵害事实。"
                + c.getDefendantDefense() + "。恳请法庭驳回原告的诉讼请求。";
    }

    /**
     * 角色卡 system prompt（原告 / 被告）。
     */
    private String buildRolePrompt(MootCourtCase c, MootCourtRole role) {
        boolean plaintiff = role == MootCourtRole.PLAINTIFF_AI;
        String roleDesc = plaintiff
                ? "你是本案原告「" + c.getPlaintiffName() + "」。你的诉讼请求：" + c.getPlaintiffClaim()
                : "你是本案被告「" + c.getDefendantName() + "」。你的抗辩理由：" + c.getDefendantDefense();

        return "你是模拟法庭庭审中的" + (plaintiff ? "原告" : "被告") + "。\n"
                + roleDesc + "。\n\n"
                + "【案件事实】\n" + c.getFact() + "\n\n"
                + "【争议焦点】\n" + String.join("\n", c.getDisputes()) + "\n\n"
                + "【相关法律知识】\n" + String.join("\n", c.getLegalPoints()) + "\n\n"
                + "庭审规则：\n"
                + "1. 以第一人称发言，语气符合角色身份，立场坚定但有理有据；\n"
                + "2. 每次发言 100-250 字，围绕争议焦点，可引用法律知识支撑观点；\n"
                + "3. 法官（学生）的指令优先于流程，必须正面回应法官的提问；\n"
                + "4. 不得代替法官或其他当事人发言，不得提及\"我是AI/模型\"；\n"
                + "5. 发言末尾不要添加\"请法官判决\"之外的寒暄客套。";
    }

    /**
     * 解析会话中的案件 JSON。
     */
    private MootCourtCase parseCase(String caseContent) {
        if (caseContent == null || caseContent.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(caseContent, MootCourtCase.class);
        } catch (Exception e) {
            log.error("解析案件内容失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> sessionDetail(MootCourtSession session) {
        Map<String, Object> dto = session.toDto();
        dto.put("case", parseCase(session.getCaseContent()));
        List<Map<String, Object>> messages = messageRepository
                .findBySessionIdOrderByRoundSeqAscCreatedAtAsc(session.getId())
                .stream().map(MootCourtMessage::toDto).toList();
        dto.put("messages", messages);
        dto.put("judgmentData", parseJudgment(session.getJudgment()));
        return dto;
    }

    private Map<String, Object> parseJudgment(String judgment) {
        if (judgment == null || judgment.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(judgment, Map.class);
        } catch (Exception e) {
            return Map.of("raw", judgment);
        }
    }

    private MootCourtSession requireSession(UUID classroomId, UUID studentId, MootCourtPhase phase) {
        return sessionRepository.findByClassroomIdAndStudentIdAndPhase(classroomId, studentId, phase)
                .orElseThrow(() -> new IllegalArgumentException(
                        "模拟法庭会话不存在，请先进入法庭（phase=" + phase + ")"));
    }

    private Classroom requirePublishedClassroom(UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("课堂不存在: " + classroomId));
        if (classroom.getStatus() != ClassroomStatus.published) {
            throw new IllegalArgumentException("课堂未发布，无法进入模拟法庭");
        }
        return classroom;
    }

    /**
     * 阶段校验：POST（课后法庭）要求课堂已学完；PRE 无限制。
     */
    private void validatePhase(UUID classroomId, UUID studentId, MootCourtPhase phase) {
        if (phase == MootCourtPhase.POST) {
            ClassroomProgress progress = progressRepository
                    .findByStudentIdAndClassroomId(studentId, classroomId).orElse(null);
            if (progress == null || progress.getStatus() != ProgressStatus.completed) {
                throw new IllegalArgumentException("课后法庭需先完成本课堂学习");
            }
        }
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
