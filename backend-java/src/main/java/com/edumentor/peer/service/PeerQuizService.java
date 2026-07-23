package com.edumentor.peer.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.diagnosis.repository.AnswerRecordRepository;
import com.edumentor.entity.enums.QuestionType;
import com.edumentor.peer.dto.*;
import com.edumentor.peer.entity.PeerQuiz;
import com.edumentor.peer.entity.PeerQuizParticipant;
import com.edumentor.peer.entity.PeerQuizQuestion;
import com.edumentor.peer.repository.PeerQuizParticipantRepository;
import com.edumentor.peer.repository.PeerQuizQuestionRepository;
import com.edumentor.peer.repository.PeerQuizRepository;
import com.edumentor.question.dto.QuestionDto;
import com.edumentor.question.dto.QuestionCreateRequest;
import com.edumentor.question.service.QuestionService;
import com.edumentor.record.entity.AnswerRecord;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生互出题考核服务。
 */
@Service
public class PeerQuizService {

    private static final Logger log = LoggerFactory.getLogger(PeerQuizService.class);

    private final PeerQuizRepository peerQuizRepository;
    private final PeerQuizParticipantRepository participantRepository;
    private final PeerQuizQuestionRepository quizQuestionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionService questionService;
    private final UserRepository userRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final ObjectMapper objectMapper;

    public PeerQuizService(PeerQuizRepository peerQuizRepository,
                           PeerQuizParticipantRepository participantRepository,
                           PeerQuizQuestionRepository quizQuestionRepository,
                           QuestionRepository questionRepository,
                           QuestionService questionService,
                           UserRepository userRepository,
                           KnowledgePointRepository knowledgePointRepository,
                           AnswerRecordRepository answerRecordRepository,
                           ObjectMapper objectMapper) {
        this.peerQuizRepository = peerQuizRepository;
        this.participantRepository = participantRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.questionRepository = questionRepository;
        this.questionService = questionService;
        this.userRepository = userRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建考核任务（含题目和参与者）。
     */
    @Transactional
    public PeerQuizDto createQuiz(UUID creatorId, PeerQuizCreateRequest request,
                                   List<PeerQuizQuestionCreateRequest> questionRequests) {
        // 1. 验证出题学生
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("用户", creatorId));

        // 2. 创建考核
        PeerQuiz quiz = new PeerQuiz();
        quiz.setCreatorId(creatorId);
        quiz.setCourseId(request.courseId());
        quiz.setKnowledgePointId(request.knowledgePointId());
        quiz.setTitle(request.title());
        quiz.setDeadline(request.deadline());
        quiz.setStatus("OPEN");
        quiz = peerQuizRepository.save(quiz);
        final UUID quizId = quiz.getId();

        // 3. 创建题目并关联
        List<Question> savedQuestions = new ArrayList<>();
        if (questionRequests != null) {
            for (int i = 0; i < questionRequests.size(); i++) {
                PeerQuizQuestionCreateRequest qReq = questionRequests.get(i);
                Question question = new Question();
                question.setKnowledgePointId(request.knowledgePointId());
                question.setCourseId(request.courseId());
                question.setContent(qReq.content());
                question.setCorrectAnswer(qReq.correctAnswer());
                question.setExplanation(qReq.explanation());
                question.setIsPublished(true);
                question.setCreatedBy(creatorId);

                // 类型映射
                try {
                    question.setQuestionType(QuestionType.valueOf(qReq.questionType()));
                } catch (Exception e) {
                    question.setQuestionType(QuestionType.SHORT_ANSWER);
                }

                // 选项
                if (qReq.options() != null && !qReq.options().isEmpty()) {
                    question.setOptions(objectMapper.valueToTree(qReq.options()));
                }

                Question saved = questionRepository.save(question);
                savedQuestions.add(saved);

                // 建立关联
                PeerQuizQuestion pqq = new PeerQuizQuestion();
                pqq.setQuizId(quizId);
                pqq.setQuestionId(saved.getId());
                pqq.setOrderIndex(i);
                quizQuestionRepository.save(pqq);
            }
        }

        // 4. 创建参与者记录
        int participantCount = 0;
        if (request.participantIds() != null) {
            for (UUID studentId : request.participantIds()) {
                if (studentId.equals(creatorId)) continue; // 不能考核自己
                PeerQuizParticipant participant = new PeerQuizParticipant();
                participant.setQuizId(quizId);
                participant.setStudentId(studentId);
                participant.setTotalQuestions(savedQuestions.size());
                participantRepository.save(participant);
                participantCount++;
            }
        }

        log.info("考核创建成功: quizId={}, creatorId={}, 题目={}题, 参与者={}人",
                quizId, creatorId, savedQuestions.size(), participantCount);

        return toDto(quiz, participantCount, 0, savedQuestions.size());
    }

    /**
     * 获取学生待考核列表。
     */
    public List<PeerQuizDto> getPendingQuizzes(UUID studentId) {
        List<PeerQuizParticipant> participants = participantRepository
                .findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "PENDING");

        return participants.stream()
                .map(p -> {
                    PeerQuiz quiz = peerQuizRepository.findById(p.getQuizId()).orElse(null);
                    if (quiz == null || !"OPEN".equals(quiz.getStatus())) return null;
                    int qCount = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quiz.getId()).size();
                    int totalParticipants = (int) participantRepository.countByQuizIdAndStatus(quiz.getId(), "PENDING")
                            + (int) participantRepository.countByQuizIdAndStatus(quiz.getId(), "COMPLETED");
                    return toDto(quiz, totalParticipants, 0, qCount);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取已完成考核列表。
     */
    public List<PeerQuizDto> getCompletedQuizzes(UUID studentId) {
        List<PeerQuizParticipant> participants = participantRepository
                .findByStudentIdOrderByCreatedAtDesc(studentId);

        return participants.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .map(p -> {
                    PeerQuiz quiz = peerQuizRepository.findById(p.getQuizId()).orElse(null);
                    if (quiz == null) return null;
                    int qCount = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quiz.getId()).size();
                    int totalParticipants = (int) participantRepository.countByQuizIdAndStatus(quiz.getId(), "COMPLETED")
                            + (int) participantRepository.countByQuizIdAndStatus(quiz.getId(), "COMPLETED");
                    return toDto(quiz, totalParticipants, 0, qCount);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取我创建的考核列表。
     */
    public List<PeerQuizDto> getMyCreatedQuizzes(UUID creatorId) {
        List<PeerQuiz> quizzes = peerQuizRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId);
        return quizzes.stream().map(q -> {
            int participantCount = (int) participantRepository.findByQuizId(q.getId()).size();
            int completedCount = (int) participantRepository.countByQuizIdAndStatus(q.getId(), "COMPLETED");
            int qCount = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(q.getId()).size();
            return toDto(q, participantCount, completedCount, qCount);
        }).collect(Collectors.toList());
    }

    /**
     * 获取考核详情。
     */
    public PeerQuizDetailDto getQuizDetail(UUID quizId) {
        PeerQuiz quiz = peerQuizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("考核", quizId));

        List<PeerQuizQuestion> pqqs = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quizId);
        List<QuestionDto> questions = pqqs.stream()
                .map(pqq -> {
                    Question q = questionRepository.findById(pqq.getQuestionId()).orElse(null);
                    return q != null ? QuestionDto.fromEntity(q) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int participantCount = (int) participantRepository.findByQuizId(quizId).size();
        int completedCount = (int) participantRepository.countByQuizIdAndStatus(quizId, "COMPLETED");

        String creatorName = userRepository.findById(quiz.getCreatorId())
                .map(User::getDisplayName).orElse("未知");

        return new PeerQuizDetailDto(
                quiz.getId(), quiz.getCreatorId(), creatorName,
                quiz.getCourseId(), quiz.getKnowledgePointId(),
                quiz.getTitle(), quiz.getDeadline(), quiz.getStatus(),
                participantCount, completedCount, questions.size(),
                quiz.getCreatedAt(), questions
        );
    }

    /**
     * 提交考核结果（参与者完成全部题目后调用）。
     */
    @Transactional
    public PeerQuizParticipant submitQuiz(UUID quizId, UUID studentId) {
        PeerQuiz quiz = peerQuizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("考核", quizId));

        PeerQuizParticipant participant = participantRepository
                .findByQuizIdAndStudentId(quizId, studentId)
                .orElseThrow(() -> new ValidationException("你未被邀请参加此考核"));

        if ("COMPLETED".equals(participant.getStatus())) {
            throw new ValidationException("你已经完成过此考核");
        }

        // 获取考核中的所有题目
        List<PeerQuizQuestion> pqqs = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quizId);

        // 统计答对的题目数
        int correctCount = 0;
        for (PeerQuizQuestion pqq : pqqs) {
            List<AnswerRecord> records = answerRecordRepository
                    .findByStudentIdAndQuestionIdOrderByAttemptedAtDesc(studentId, pqq.getQuestionId());
            if (!records.isEmpty() && Boolean.TRUE.equals(records.get(0).getIsCorrect())) {
                correctCount++;
            }
        }

        participant.setScore(correctCount);
        participant.setTotalQuestions(pqqs.size());
        participant.setStatus("COMPLETED");
        participant.setCompletedAt(LocalDateTime.now());

        PeerQuizParticipant saved = participantRepository.save(participant);

        log.info("考核提交完成: quizId={}, studentId={}, 得分={}/{}",
                quizId, studentId, correctCount, pqqs.size());

        return saved;
    }

    /**
     * 关闭考核（出题者调用）。
     */
    @Transactional
    public void closeQuiz(UUID quizId, UUID userId) {
        PeerQuiz quiz = peerQuizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("考核", quizId));

        if (!quiz.getCreatorId().equals(userId)) {
            throw new ValidationException("只有出题者才能关闭考核");
        }

        quiz.setStatus("CLOSED");
        peerQuizRepository.save(quiz);
        log.info("考核已关闭: quizId={}", quizId);
    }

    /**
     * 获取考核结果（出题者视角）。
     */
    public PeerQuizResultDto getQuizResults(UUID quizId, UUID userId) {
        PeerQuiz quiz = peerQuizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("考核", quizId));

        if (!quiz.getCreatorId().equals(userId)) {
            throw new ValidationException("只有出题者才能查看考核结果");
        }

        List<PeerQuizParticipant> participants = participantRepository.findByQuizId(quizId);

        List<PeerQuizResultDto.ParticipantResult> results = participants.stream()
                .map(p -> {
                    String studentName = userRepository.findById(p.getStudentId())
                            .map(User::getDisplayName).orElse("未知");
                    return new PeerQuizResultDto.ParticipantResult(
                            p.getId(), p.getStudentId(), studentName,
                            p.getStatus(), p.getScore(), p.getTotalQuestions(), p.getCompletedAt()
                    );
                })
                .collect(Collectors.toList());

        return new PeerQuizResultDto(quizId, quiz.getTitle(), results);
    }

    /**
     * 获取单题答题统计（出题者视角）。
     */
    public PeerQuizQuestionResultDto getQuestionResults(UUID quizId, UUID questionId, UUID userId) {
        PeerQuiz quiz = peerQuizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("考核", quizId));

        if (!quiz.getCreatorId().equals(userId)) {
            throw new ValidationException("只有出题者才能查看");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("题目", questionId));

        List<PeerQuizParticipant> participants = participantRepository.findByQuizId(quizId);

        List<PeerQuizQuestionResultDto.StudentAnswerDetail> details = new ArrayList<>();
        int totalAnswers = 0;
        int correctCount = 0;

        for (PeerQuizParticipant p : participants) {
            if (!"COMPLETED".equals(p.getStatus())) continue;
            List<AnswerRecord> records = answerRecordRepository
                    .findByStudentIdAndQuestionIdOrderByAttemptedAtDesc(p.getStudentId(), questionId);
            if (records.isEmpty()) continue;

            totalAnswers++;
            AnswerRecord record = records.get(0);
            if (Boolean.TRUE.equals(record.getIsCorrect())) correctCount++;

            String studentName = userRepository.findById(p.getStudentId())
                    .map(User::getDisplayName).orElse("未知");
            details.add(new PeerQuizQuestionResultDto.StudentAnswerDetail(
                    p.getStudentId(), studentName,
                    record.getStudentAnswer(), Boolean.TRUE.equals(record.getIsCorrect())
            ));
        }

        double correctRate = totalAnswers > 0 ? (double) correctCount / totalAnswers : 0.0;

        return new PeerQuizQuestionResultDto(
                QuestionDto.fromEntity(question),
                totalAnswers, correctCount, correctRate, details
        );
    }

    // ─── 内部辅助方法 ───

    private PeerQuizDto toDto(PeerQuiz quiz, int participantCount, int completedCount, int questionCount) {
        String creatorName = userRepository.findById(quiz.getCreatorId())
                .map(User::getDisplayName).orElse("未知");
        return new PeerQuizDto(
                quiz.getId(), quiz.getCreatorId(), creatorName,
                quiz.getCourseId(), quiz.getKnowledgePointId(),
                quiz.getTitle(), quiz.getDeadline(), quiz.getStatus(),
                participantCount, completedCount, questionCount,
                quiz.getCreatedAt()
        );
    }
}
