package com.edumentor.record.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.diagnosis.repository.AnswerRecordRepository;
import com.edumentor.diagnosis.repository.StudySessionRepository;
import com.edumentor.entity.enums.ErrorType;
import com.edumentor.learningpath.entity.LearningPath;
import com.edumentor.learningpath.entity.LearningPathNode;
import com.edumentor.learningpath.entity.PathNodeStatus;
import com.edumentor.learningpath.entity.PathStatus;
import com.edumentor.learningpath.repository.LearningPathNodeRepository;
import com.edumentor.learningpath.repository.LearningPathRepository;
import com.edumentor.record.dto.SubmitAnswerRequest;
import com.edumentor.record.dto.SubmitAnswerResult;
import com.edumentor.record.entity.AnswerRecord;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.edumentor.review.entity.ErrorRecord;
import com.edumentor.review.repository.ErrorRecordRepository;
import com.edumentor.session.entity.StudySession;
import com.edumentor.entity.enums.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 答题提交服务 — 学生作答并即时反馈结果。
 * <p>
 * 级联操作：
 * <ul>
 *   <li>答错 → 创建 error_record（错题复盘）</li>
 *   <li>答对 → 推进学习路径节点（markCompleted）</li>
 *   <li>每次答题 → 更新学习会话记录（study_sessions）</li>
 * </ul>
 * </p>
 */
@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;
    private final ErrorRecordRepository errorRecordRepository;
    private final LearningPathRepository learningPathRepository;
    private final LearningPathNodeRepository learningPathNodeRepository;
    private final StudySessionRepository studySessionRepository;
    private final KnowledgePointRepository knowledgePointRepository;

    public AnswerService(AnswerRecordRepository answerRecordRepository,
                         QuestionRepository questionRepository,
                         ErrorRecordRepository errorRecordRepository,
                         LearningPathRepository learningPathRepository,
                         LearningPathNodeRepository learningPathNodeRepository,
                         StudySessionRepository studySessionRepository,
                         KnowledgePointRepository knowledgePointRepository) {
        this.answerRecordRepository = answerRecordRepository;
        this.questionRepository = questionRepository;
        this.errorRecordRepository = errorRecordRepository;
        this.learningPathRepository = learningPathRepository;
        this.learningPathNodeRepository = learningPathNodeRepository;
        this.studySessionRepository = studySessionRepository;
        this.knowledgePointRepository = knowledgePointRepository;
    }

    /**
     * 提交答题：比对答案、保存记录、级联更新。
     */
    @Transactional
    public SubmitAnswerResult submitAnswer(UUID studentId, SubmitAnswerRequest request) {
        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("题目", request.getQuestionId()));

        boolean isCorrect = checkAnswer(question, request.getStudentAnswer());

        // ── 1. 保存答题记录 ──
        AnswerRecord record = new AnswerRecord();
        record.setStudentId(studentId);
        record.setQuestionId(question.getId());
        record.setKnowledgePointId(question.getKnowledgePointId());
        record.setCourseId(question.getCourseId());
        record.setIsCorrect(isCorrect);
        record.setStudentAnswer(request.getStudentAnswer());
        record.setTimeSpentSeconds(request.getTimeSpentSeconds() != null ? request.getTimeSpentSeconds() : 0);
        record.setAttemptedAt(LocalDateTime.now());

        AnswerRecord saved = answerRecordRepository.save(record);
        log.info("学生 {} 答题: questionId={}, isCorrect={}", studentId, question.getId(), isCorrect);

        // ── 2. 答错 → 创建错题记录 ──
        if (!isCorrect) {
            createErrorRecord(studentId, question, request.getStudentAnswer());
        }

        // ── 3. 答对 → 推进学习路径节点 ──
        if (isCorrect && question.getCourseId() != null) {
            advanceLearningPathNode(studentId, question.getCourseId(), question.getKnowledgePointId());
        }

        // ── 4. 更新学习会话 ──
        updateStudySession(studentId, question.getKnowledgePointId(), isCorrect);

        return new SubmitAnswerResult(
                saved.getId(),
                question.getId(),
                question.getKnowledgePointId(),
                question.getCourseId(),
                isCorrect,
                question.getCorrectAnswer(),
                question.getExplanation(),
                request.getStudentAnswer()
        );
    }

    /**
     * 创建错题记录。
     */
    private void createErrorRecord(UUID studentId, Question question, String studentAnswer) {
        try {
            // 查询知识点名称
            String kpName = null;
            if (question.getKnowledgePointId() != null) {
                Optional<KnowledgePoint> kp = knowledgePointRepository.findById(question.getKnowledgePointId());
                if (kp.isPresent()) {
                    kpName = kp.get().getName();
                }
            }

            ErrorRecord er = new ErrorRecord();
            er.setStudentId(studentId);
            er.setCourseId(question.getCourseId());
            er.setQuestionId(question.getId());
            er.setKnowledgePointId(question.getKnowledgePointId());
            er.setKnowledgePointName(kpName);
            er.setQuestionContent(question.getContent());
            er.setStudentAnswer(studentAnswer);
            er.setCorrectAnswer(question.getCorrectAnswer());
            er.setErrorType(classifyErrorType(question, studentAnswer));
            er.setDifficulty(question.getDifficulty());
            er.setErrorCount(1);
            er.setIsReviewed(false);
            errorRecordRepository.save(er);
            log.info("已创建错题记录: studentId={}, questionId={}, kp={}, type={}",
                    studentId, question.getId(), kpName, er.getErrorType());
        } catch (Exception e) {
            log.warn("创建错题记录失败: {}", e.getMessage());
        }
    }

    /**
     * 智能判题：根据题型使用不同的比对策略。
     */
    private boolean checkAnswer(Question question, String studentAnswer) {
        if (studentAnswer == null) return false;
        String correct = question.getCorrectAnswer();
        if (correct == null) return false;

        String student = studentAnswer.trim();
        String expected = correct.trim();

        if (question.getQuestionType() == null) {
            return expected.equalsIgnoreCase(student);
        }

        return switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE -> {
                // 多选题："A,B,C" 不区分顺序
                String[] studentParts = student.split("[,\\s]+");
                String[] expectedParts = expected.split("[,\\s]+");
                if (studentParts.length != expectedParts.length) yield false;
                Arrays.sort(studentParts);
                Arrays.sort(expectedParts);
                yield Arrays.equals(studentParts, expectedParts);
            }
            case FILL_BLANK -> {
                // 填空题：忽略大小写和首尾空格
                yield expected.equalsIgnoreCase(student);
            }
            case SHORT_ANSWER, ESSAY -> {
                // 简答题/论述题：包含关键词判断
                String studentLower = student.toLowerCase();
                String[] keywords = expected.split("[，。、；：\\s]+");
                long matchCount = Arrays.stream(keywords)
                        .filter(kw -> kw.length() > 2)
                        .filter(kw -> studentLower.contains(kw.toLowerCase()))
                        .count();
                long totalKeywords = Arrays.stream(keywords)
                        .filter(kw -> kw.length() > 2)
                        .count();
                yield totalKeywords > 0 && matchCount >= Math.max(1, totalKeywords / 2);
            }
            default -> expected.equalsIgnoreCase(student);
        };
    }

    /** 基于题目类型和学生答案进行简单的错误类型分类。 */
    private ErrorType classifyErrorType(Question question, String studentAnswer) {
        // 空答案 → 粗心
        if (studentAnswer == null || studentAnswer.trim().isEmpty()) {
            return ErrorType.CARELESS;
        }
        // 选择题场景
        if (question.getQuestionType() != null
                && question.getQuestionType().name().contains("CHOICE")) {
            String correct = question.getCorrectAnswer() != null ? question.getCorrectAnswer().trim() : "";
            String answer = studentAnswer.trim();
            // 学生答案与正确答案接近（如只差一个字母）
            if (correct.length() > 0 && answer.length() > 0) {
                int dist = levenshteinDistance(correct.toUpperCase(), answer.toUpperCase());
                if (dist == 1) {
                    return ErrorType.CARELESS; // 差一个字母 → 粗心
                }
            }
        }
        // 默认归为知识盲区
        return ErrorType.KNOWLEDGE_GAP;
    }

    /** Levenshtein 编辑距离（用于答案相似度判断） */
    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * 答对时推进学习路径节点。
     * 找到学生对该课程的活跃学习路径中，匹配该知识点的 PENDING 节点，标记为 COMPLETED。
     */
    private void advanceLearningPathNode(UUID studentId, UUID courseId, UUID knowledgePointId) {
        try {
            List<LearningPath> paths = learningPathRepository.findByStudentIdAndCourseId(studentId, courseId);
            for (LearningPath path : paths) {
                if (path.getStatus() == PathStatus.ACTIVE || path.getStatus() == PathStatus.DRAFT) {
                    List<LearningPathNode> nodes = learningPathNodeRepository
                            .findByLearningPathIdOrderByOrderIndexAsc(path.getId());

                    for (LearningPathNode node : nodes) {
                        if (node.getKnowledgePointId().equals(knowledgePointId)
                                && node.getStatus() == PathNodeStatus.PENDING) {
                            node.markCompleted();
                            learningPathNodeRepository.save(node);

                            // 更新路径进度
                            path.setCompletedNodes(
                                    (int) learningPathNodeRepository
                                            .countByLearningPathIdAndStatus(path.getId(), PathNodeStatus.COMPLETED));
                            path.setProgress(
                                    path.getTotalNodes() > 0
                                            ? path.getCompletedNodes() * 100 / path.getTotalNodes()
                                            : 0);

                            // 如果全部完成，标记路径为 COMPLETED
                            if (path.getCompletedNodes() >= path.getTotalNodes()) {
                                path.setStatus(PathStatus.COMPLETED);
                            }
                            learningPathRepository.save(path);

                            log.info("学习路径节点已推进: pathId={}, kpId={}, progress={}/{}",
                                    path.getId(), knowledgePointId, path.getCompletedNodes(), path.getTotalNodes());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("推进学习路径节点失败: {}", e.getMessage());
        }
    }

    /**
     * 更新学习会话记录。
     * 查找当前学生的活跃会话，如果没有则创建新会话。
     */
    private void updateStudySession(UUID studentId, UUID knowledgePointId, boolean isCorrect) {
        try {
            LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
            List<StudySession> todaySessions = studySessionRepository
                    .findByStudentIdAndStartTimeBetween(studentId, todayStart, LocalDateTime.now());

            StudySession session;
            if (!todaySessions.isEmpty()) {
                // 使用今天的最后一个活跃会话
                session = todaySessions.get(todaySessions.size() - 1);
                if (session.getStatus() == SessionStatus.ACTIVE) {
                    session.setEndTime(LocalDateTime.now());
                    session.setDurationSeconds(
                            (int) java.time.Duration.between(session.getStartTime(), LocalDateTime.now()).getSeconds());
                    session.setQuestionsAnswered(
                            (session.getQuestionsAnswered() != null ? session.getQuestionsAnswered() : 0) + 1);
                    session.setCorrectCount(
                            (session.getCorrectCount() != null ? session.getCorrectCount() : 0) + (isCorrect ? 1 : 0));
                    studySessionRepository.save(session);
                    return;
                }
            }

            // 没有活跃会话 → 创建新会话
            session = new StudySession();
            session.setStudentId(studentId);
            session.setKnowledgePointId(knowledgePointId);
            session.setStartTime(LocalDateTime.now());
            session.setStatus(SessionStatus.ACTIVE);
            session.setQuestionsAnswered(1);
            session.setCorrectCount(isCorrect ? 1 : 0);
            session.setDurationSeconds(0);
            studySessionRepository.save(session);
            log.debug("创建新学习会话: studentId={}", studentId);

        } catch (Exception e) {
            log.warn("更新学习会话失败: {}", e.getMessage());
        }
    }
}
