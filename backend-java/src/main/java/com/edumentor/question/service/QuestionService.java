package com.edumentor.question.service;

import com.edumentor.common.exception.DuplicateResourceException;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.question.dto.QuestionCreateRequest;
import com.edumentor.question.dto.QuestionDto;
import com.edumentor.question.dto.QuestionUpdateRequest;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 题目管理服务 — 提供习题的增删改查功能。
 */
@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ObjectMapper objectMapper;

    public QuestionService(QuestionRepository questionRepository,
                           KnowledgePointRepository knowledgePointRepository,
                           ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuestionDto createQuestion(QuestionCreateRequest request, UUID userId) {
        log.info("创建题目: kpId={}, courseId={}", request.getKnowledgePointId(), request.getCourseId());

        if (questionRepository.existsByContentAndKnowledgePointId(request.getContent(), request.getKnowledgePointId())) {
            throw new DuplicateResourceException("题目", "相同知识点下已存在相同内容的题目");
        }

        Question question = new Question();
        question.setKnowledgePointId(request.getKnowledgePointId());
        question.setCourseId(request.getCourseId());
        question.setQuestionType(request.getQuestionType());
        question.setContent(request.getContent());
        question.setOptions(parseOptions(request.getOptions()));
        question.setCorrectAnswer(request.getCorrectAnswer());
        question.setExplanation(request.getExplanation());
        question.setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : 3);
        question.setCreatedBy(userId);

        Question saved = questionRepository.save(question);
        log.info("题目创建成功: id={}", saved.getId());
        return QuestionDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public QuestionDto getQuestion(UUID id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("题目", id));
        return QuestionDto.fromEntity(question);
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> listQuestionsByCourse(UUID courseId) {
        return questionRepository.findByCourseId(courseId).stream()
                .map(QuestionDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> listQuestionsByKnowledgePoint(UUID kpId) {
        // 目录节点（非 LEAF）只做导航，不承载练习题（设计文档 §3.3）
        // 即使历史数据存在误挂在目录节点上的题目，学生端也不展示
        boolean isLeaf = knowledgePointRepository.findById(kpId)
                .map(kp -> "LEAF".equals(kp.getType()))
                .orElse(false);
        if (!isLeaf) {
            return List.of();
        }
        return questionRepository.findByKnowledgePointId(kpId).stream()
                .map(QuestionDto::fromEntity)
                .toList();
    }

    @Transactional
    public QuestionDto updateQuestion(UUID id, QuestionUpdateRequest request) {
        log.info("更新题目: id={}", id);

        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("题目", id));

        if (request.getKnowledgePointId() != null) {
            question.setKnowledgePointId(request.getKnowledgePointId());
        }
        if (request.getQuestionType() != null) {
            question.setQuestionType(request.getQuestionType());
        }
        if (request.getContent() != null) {
            question.setContent(request.getContent());
        }
        if (request.getOptions() != null) {
            question.setOptions(parseOptions(request.getOptions()));
        }
        if (request.getCorrectAnswer() != null) {
            question.setCorrectAnswer(request.getCorrectAnswer());
        }
        if (request.getExplanation() != null) {
            question.setExplanation(request.getExplanation());
        }
        if (request.getDifficulty() != null) {
            if (request.getDifficulty() < 1 || request.getDifficulty() > 5) {
                throw new ValidationException("难度值必须在 1-5 之间");
            }
            question.setDifficulty(request.getDifficulty());
        }
        if (request.getIsPublished() != null) {
            question.setIsPublished(request.getIsPublished());
        }

        Question saved = questionRepository.save(question);
        return QuestionDto.fromEntity(saved);
    }

    private JsonNode parseOptions(String optionsStr) {
        if (optionsStr == null || optionsStr.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(optionsStr);
        } catch (Exception e) {
            log.warn("解析 options 失败，使用默认空数组: {}", e.getMessage());
            return objectMapper.createArrayNode();
        }
    }

    @Transactional
    public void deleteQuestion(UUID id) {
        if (!questionRepository.existsById(id)) {
            throw new ResourceNotFoundException("题目", id);
        }
        questionRepository.deleteById(id);
        log.info("题目已删除: id={}", id);
    }
}
