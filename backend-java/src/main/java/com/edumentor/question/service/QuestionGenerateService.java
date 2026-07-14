package com.edumentor.question.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.engine.rag.RAGEngine;
import com.edumentor.entity.enums.QuestionType;
import com.edumentor.question.dto.QuestionDto;
import com.edumentor.question.dto.QuestionGenerateRequest;
import com.edumentor.question.dto.QuestionGenerateResult;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 出题服务 — 基于 RAG 检索增强 + LLM 生成习题。
 */
@Service
public class QuestionGenerateService {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerateService.class);

    private static final String SYSTEM_PROMPT = """
            你是一位经验丰富的学科教师，擅长命制高质量的练习题。
            
            请根据提供的「知识点信息」和「参考上下文」，生成指定数量的题目。
            题型包括：SINGLE_CHOICE（单选题）、MULTIPLE_CHOICE（多选题）、TRUE_FALSE（判断题）、
            FILL_BLANK（填空题）、SHORT_ANSWER（简答题）。
            
            输出 JSON 格式（不要任何额外文字）：
            {
              "questions": [
                {
                  "kpName": "知识点名称",
                  "content": "题目内容",
                  "type": "SINGLE_CHOICE",
                  "options": ["选项A内容", "选项B内容", "选项C内容", "选项D内容"],
                  "correctAnswer": "A",
                  "explanation": "解析"
                }
              ]
            }
            
            要求：
            - 单选题必须提供 4 个选项，正确答案为 A/B/C/D
            - 多选题必须提供 4 个选项，正确答案为字母组合如 "A,B,C"
            - 判断题答案用 A（正确）或 B（错误）
            - 填空题的 correctAnswer 为填入内容
            - 简答题的 correctAnswer 为参考答案要点
            - **严格按指定的题型和数量生成，不要多出也不要缺少**
            - 题目内容要结合参考上下文，符合该知识点的学习目标
            - 输出纯 JSON，不要任何 markdown 代码块标记
            """;

    private static final String OUTPUT_SCHEMA_NAME = "fill_questions";

    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionRepository questionRepository;
    private final LLMService llmService;
    private final Optional<RAGEngine> ragEngine;
    private final ObjectMapper objectMapper;

    public QuestionGenerateService(KnowledgePointRepository knowledgePointRepository,
                                   QuestionRepository questionRepository,
                                   LLMService llmService,
                                   Optional<RAGEngine> ragEngine,
                                   ObjectMapper objectMapper) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.questionRepository = questionRepository;
        this.llmService = llmService;
        this.ragEngine = ragEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * AI 生成习题。
     *
     * @param request 出题请求（含课程、知识点、各题型数量）
     * @return 生成结果
     */
    @Transactional
    public QuestionGenerateResult generate(QuestionGenerateRequest request) {
        UUID kpId = request.knowledgePointId();
        UUID courseId = request.courseId();

        // 1. 加载知识点
        KnowledgePoint kp = knowledgePointRepository.findById(kpId)
                .orElseThrow(() -> new ResourceNotFoundException("知识点", kpId));

        // 2. 构建 Prompt
        String userMessage = buildUserMessage(kp, request);

        // 3. 调用 LLM 生成
        GenerateResult llmResult;
        try {
            llmResult = llmService.askStructured(
                    SYSTEM_PROMPT, userMessage,
                    GenerateResult.class, OUTPUT_SCHEMA_NAME);
        } catch (Exception e) {
            log.error("LLM 出题失败: {}", e.getMessage());
            throw new RuntimeException("AI 出题调用失败: " + e.getMessage());
        }

        if (llmResult == null || llmResult.questions == null || llmResult.questions.isEmpty()) {
            log.warn("LLM 返回空结果");
            return new QuestionGenerateResult(0, List.of());
        }

        // 4. 批量保存
        List<QuestionDto> savedDtos = new ArrayList<>();
        for (GeneratedQuestion gq : llmResult.questions) {
            if (gq.content == null || gq.content.isBlank()) continue;

            // 去重
            if (questionRepository.existsByContentAndKnowledgePointId(gq.content.trim(), kpId)) {
                continue;
            }

            Question q = new Question();
            q.setKnowledgePointId(kpId);
            q.setCourseId(courseId);
            q.setContent(gq.content);
            q.setCorrectAnswer(gq.correctAnswer != null ? gq.correctAnswer : "");

            // 类型映射
            QuestionType qType = QuestionType.SINGLE_CHOICE;
            if (gq.type != null) {
                try { qType = QuestionType.valueOf(gq.type.toUpperCase()); }
                catch (Exception ignored) {}
            }
            q.setQuestionType(qType);

            // 选项
            if (gq.options != null && !gq.options.isEmpty()
                    && qType != QuestionType.ESSAY && qType != QuestionType.SHORT_ANSWER) {
                Map<String, String> optMap = new LinkedHashMap<>();
                for (int i = 0; i < gq.options.size(); i++) {
                    String label = String.valueOf((char) ('A' + i));
                    String text = gq.options.get(i);
                    if (text != null && text.contains(". ")) {
                        text = text.substring(text.indexOf(". ") + 2);
                    }
                    if (text != null) optMap.put(label, text.trim());
                }
                if (!optMap.isEmpty()) {
                    q.setOptions(objectMapper.valueToTree(optMap));
                }
            }

            q.setExplanation(gq.explanation != null ? gq.explanation : "");
            q.setDifficulty(kp.getDifficulty() != null ? kp.getDifficulty() : 3);
            q.setIsPublished(true);

            Question saved = questionRepository.save(q);
            savedDtos.add(QuestionDto.fromEntity(saved));
        }

        log.info("AI 出题完成: kpId={}, 生成 {} 题, 去重跳过 {} 题",
                kpId, savedDtos.size(), llmResult.questions.size() - savedDtos.size());

        return new QuestionGenerateResult(savedDtos.size(), savedDtos);
    }

    /**
     * 构建发送给 LLM 的用户消息。
     */
    private String buildUserMessage(KnowledgePoint kp, QuestionGenerateRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("【知识点名称】").append(kp.getName()).append("\n");
        if (kp.getDescription() != null && !kp.getDescription().isBlank()) {
            sb.append("【知识点描述】").append(kp.getDescription()).append("\n");
        }
        if (kp.getContent() != null && !kp.getContent().isBlank()) {
            sb.append("【知识点内容】").append(kp.getContent()).append("\n");
        }

        // 从 RAG 检索增强上下文
        Map<String, Integer> counts = request.counts();
        int totalNeeded = counts != null ? counts.values().stream().mapToInt(Integer::intValue).sum() : 5;
        if (ragEngine.isPresent() && totalNeeded > 0) {
            try {
                List<RAGEngine.DocumentChunk> chunks = ragEngine.get()
                        .retrieveByKnowledgePoint(kp.getName(), kp.getId().toString(), 5);
                if (chunks != null && !chunks.isEmpty()) {
                    sb.append("\n【参考上下文】\n");
                    for (int i = 0; i < chunks.size(); i++) {
                        sb.append("--- 参考 ").append(i + 1).append(" ---\n");
                        sb.append(chunks.get(i).getContent()).append("\n");
                    }
                }
            } catch (Exception e) {
                log.warn("RAG 检索失败（不影响出题）: {}", e.getMessage());
            }
        }

        // 指定各题型数量
        sb.append("\n【出题要求】\n");
        int totalRequested = 0;
        if (counts != null) {
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    String typeLabel = getTypeChineseName(entry.getKey());
                    sb.append("- ").append(typeLabel).append("：").append(entry.getValue()).append(" 道\n");
                    totalRequested += entry.getValue();
                }
            }
        }
        if (totalRequested == 0) {
            sb.append("- 单选题：3 道\n- 填空题：1 道\n");
            totalRequested = 4;
        }
        sb.append("\n请严格按以上数量出题，共 ").append(totalRequested).append(" 道。");

        return sb.toString();
    }

    private String getTypeChineseName(String type) {
        return switch (type) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTIPLE_CHOICE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "简答题";
            default -> type;
        };
    }

    /**
     * LLM 返回的生成结果（结构化输出）。
     */
    public static class GenerateResult {
        public List<GeneratedQuestion> questions;
    }

    public static class GeneratedQuestion {
        public String kpName;
        public String content;
        public String type;
        public List<String> options;
        public String correctAnswer;
        public String explanation;
    }
}
