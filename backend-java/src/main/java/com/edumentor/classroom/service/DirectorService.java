package com.edumentor.classroom.service;

import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.engine.llm.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 课堂导演服务 — 状态机实现多Agent编排。
 * <p>
 * 职责：
 * 1. 决定当前课堂应该由哪个Agent发言
 * 2. 决定何时触发AI同学提问
 * 3. 处理学生打断/提问
 * 4. 管理课堂节奏（Quiz后答错的 Tutor 模式切换）
 * </p>
 *
 * 状态流转:
 * LECTURE ←→ QUIZ ←→ DISCUSSION ←→ TUTOR
 *    ↑                                    ↑
 *    └──────── INTERRUPTED ────────────────┘
 */
@Service
public class DirectorService {

    private static final Logger log = LoggerFactory.getLogger(DirectorService.class);

    private final LLMService llmService;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ObjectMapper objectMapper;

    public DirectorService(LLMService llmService,
                           KnowledgePointRepository knowledgePointRepository,
                           ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.knowledgePointRepository = knowledgePointRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据当前状态和课堂上下文，决定下一个要执行的Agent角色和动作。
     *
     * @param currentState   当前导演状态
     * @param context        课堂上下文（知识点、当前场景、上次Quiz结果等）
     * @return 决策结果（下一个Agent角色 + 动作）
     */
    public DirectorDecision decideNext(DirectorState currentState, ClassroomContext context) {
        return switch (currentState) {
            case LECTURE -> decideLecture(context);
            case QUIZ -> decideQuiz(context);
            case DISCUSSION -> decideDiscussion(context);
            case TUTOR -> decideTutor(context);
            case INTERRUPTED -> decideInterrupted(context);
        };
    }

    /**
     * 处理学生打断/提问。
     *
     * @param studentQuestion 学生的问题
     * @param context         课堂上下文
     * @return DirectorDecision — 通常由AI教师回答
     */
    public DirectorDecision handleInterruption(String studentQuestion, ClassroomContext context) {
        log.info("Student interruption: {}", studentQuestion);

        // 由AI教师来回答学生的问题
        return DirectorDecision.builder()
                .nextState(DirectorState.INTERRUPTED)
                .agentRole("TEACHER")
                .actionType("answer_question")
                .content(studentQuestion)
                .context(context.getCurrentTopic())
                .build();
    }

    /**
     * 判断是否应该触发AI同学提问。
     * 在讲解复杂概念后，有一定的概率触发AI同学提问。
     */
    public boolean shouldTriggerClassmate(DirectorState currentState, ClassroomContext context) {
        if (currentState != DirectorState.LECTURE) return false;

        // 只在讲解复杂知识点时有一定概率触发
        int difficulty = context.getDifficulty();
        int lectureCount = context.getTeacherSpeechCount();

        // 每3次讲解后，有概率触发AI同学提问
        return lectureCount > 0 && lectureCount % 3 == 0 && difficulty >= 3;
    }

    /**
     * 生成AI同学的提问内容。
     */
    public String generateClassmateQuestion(String topic, int difficulty) {
        String prompt = String.format("""
                你是一位正在听课的"AI同学"，名叫"小E"。老师刚刚讲解了关于"%s"的知识点（难度%d）。
                请以学生的口吻，提出一个有代表性的问题或疑惑。
                问题要真实反映学生学习时可能遇到的困惑，不要太简单。
                
                请直接返回问题内容，不要包含角色前缀。
                """, topic, difficulty);

        try {
            return llmService.ask("你是一位勤学好问的学生，请提出一个学习上的问题。", prompt).getContent();
        } catch (Exception e) {
            return "老师，这里我有点不太明白，能再详细解释一下吗？";
        }
    }

    /**
     * 生成AI教师的回答。
     */
    public String generateTeacherResponse(String question, String topic) {
        String prompt = String.format("""
                你是一位专业的AI教师，正在讲授以"%s"为核心的课程。
                学生提问：%s
                
                请用清晰、耐心的方式回答学生的问题。回答要：
                1. 先肯定学生的提问
                2. 用通俗易懂的语言解释
                3. 必要时给出示例
                4. 最后确认学生是否理解
                """, topic, question);

        try {
            return llmService.ask(
                    "你是一位耐心、专业的AI教师，善于用通俗易懂的方式解答学生问题。", prompt).getContent();
        } catch (Exception e) {
            return "这是个很好的问题！让我来为你详细解释一下。";
        }
    }

    /**
     * 生成苏格拉底式引导（Tutor模式）。
     */
    public String generateSocraticGuidance(String question, String studentAnswer, String correctAnswer) {
        String prompt = String.format("""
                你是一位苏格拉底导师，正在通过追问引导学生自己发现正确答案。
                
                题目：%s
                学生的回答：%s
                正确答案：%s
                
                请用苏格拉底式提问法，通过2-3个启发式问题引导学生自己发现错误并得出正确答案。
                不要直接告诉学生正确答案，而是通过追问让他们自己思考。
                """, question, studentAnswer, correctAnswer);

        try {
            return llmService.ask(
                    "你是一位苏格拉底导师，善于通过追问引导学生思考。请不要直接给出答案。", prompt).getContent();
        } catch (Exception e) {
            return "让我们重新思考一下这个问题的前提条件。";
        }
    }

    // ── 私有决策方法 ──

    private DirectorDecision decideLecture(ClassroomContext context) {
        return DirectorDecision.builder()
                .nextState(DirectorState.LECTURE)
                .agentRole("TEACHER")
                .actionType("continue_lecture")
                .content(context.getCurrentTopic())
                .build();
    }

    private DirectorDecision decideQuiz(ClassroomContext context) {
        return DirectorDecision.builder()
                .nextState(DirectorState.QUIZ)
                .agentRole("TEACHER")
                .actionType("present_quiz")
                .build();
    }

    private DirectorDecision decideDiscussion(ClassroomContext context) {
        return DirectorDecision.builder()
                .nextState(DirectorState.LECTURE) // 讨论结束后回到讲授
                .agentRole("CLASSMATE")
                .actionType("ask_question")
                .content(context.getLastDiscussedTopic())
                .build();
    }

    private DirectorDecision decideTutor(ClassroomContext context) {
        return DirectorDecision.builder()
                .nextState(DirectorState.LECTURE) // 引导结束后回到讲授
                .agentRole("TUTOR")
                .actionType("socratic_guidance")
                .content(context.getLastQuizQuestion())
                .build();
    }

    private DirectorDecision decideInterrupted(ClassroomContext context) {
        return DirectorDecision.builder()
                .nextState(DirectorState.LECTURE) // 回答完问题后恢复讲授
                .agentRole("TEACHER")
                .actionType("answer_question")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  内部类型
    // ═══════════════════════════════════════════════════════════════

    /**
     * 导演决策结果。
     */
    public static class DirectorDecision {
        private DirectorState nextState;
        private String agentRole;
        private String actionType;
        private String content;
        private String context;

        public DirectorDecision() {}

        public static DirectorDecisionBuilder builder() {
            return new DirectorDecisionBuilder();
        }

        // Getters
        public DirectorState getNextState() { return nextState; }
        public String getAgentRole() { return agentRole; }
        public String getActionType() { return actionType; }
        public String getContent() { return content; }
        public String getContext() { return context; }

        public static class DirectorDecisionBuilder {
            private DirectorState nextState;
            private String agentRole;
            private String actionType;
            private String content;
            private String context;

            public DirectorDecisionBuilder nextState(DirectorState nextState) { this.nextState = nextState; return this; }
            public DirectorDecisionBuilder agentRole(String agentRole) { this.agentRole = agentRole; return this; }
            public DirectorDecisionBuilder actionType(String actionType) { this.actionType = actionType; return this; }
            public DirectorDecisionBuilder content(String content) { this.content = content; return this; }
            public DirectorDecisionBuilder context(String context) { this.context = context; return this; }
            public DirectorDecision build() {
                DirectorDecision d = new DirectorDecision();
                d.nextState = this.nextState;
                d.agentRole = this.agentRole;
                d.actionType = this.actionType;
                d.content = this.content;
                d.context = this.context;
                return d;
            }
        }
    }

    /**
     * 课堂上下文。
     */
    public static class ClassroomContext {
        private String currentTopic;
        private String lastQuizQuestion;
        private String lastDiscussedTopic;
        private int difficulty;
        private int teacherSpeechCount;
        private UUID knowledgePointId;
        private UUID classroomId;
        private UUID studentId;

        // Getters & Setters
        public String getCurrentTopic() { return currentTopic; }
        public void setCurrentTopic(String currentTopic) { this.currentTopic = currentTopic; }
        public String getLastQuizQuestion() { return lastQuizQuestion; }
        public void setLastQuizQuestion(String lastQuizQuestion) { this.lastQuizQuestion = lastQuizQuestion; }
        public String getLastDiscussedTopic() { return lastDiscussedTopic; }
        public void setLastDiscussedTopic(String lastDiscussedTopic) { this.lastDiscussedTopic = lastDiscussedTopic; }
        public int getDifficulty() { return difficulty; }
        public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
        public int getTeacherSpeechCount() { return teacherSpeechCount; }
        public void setTeacherSpeechCount(int teacherSpeechCount) { this.teacherSpeechCount = teacherSpeechCount; }
        public UUID getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(UUID knowledgePointId) { this.knowledgePointId = knowledgePointId; }
        public UUID getClassroomId() { return classroomId; }
        public void setClassroomId(UUID classroomId) { this.classroomId = classroomId; }
        public UUID getStudentId() { return studentId; }
        public void setStudentId(UUID studentId) { this.studentId = studentId; }
    }
}
