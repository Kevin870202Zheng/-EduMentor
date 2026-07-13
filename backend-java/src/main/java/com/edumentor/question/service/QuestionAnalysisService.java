package com.edumentor.question.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.question.dto.QuestionAnalysisRequest;
import com.edumentor.question.dto.QuestionAnalysisResult;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 题目分析服务 — 利用大模型对题目进行深度分析。
 * 无副作用，不写数据库。
 */
@Service
public class QuestionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(QuestionAnalysisService.class);

    private static final String SYSTEM_PROMPT = """
            你是一位经验丰富的学科辅导老师，擅长帮助学生理解题目。
            
            请分析以下题目，按 JSON 格式输出分析结果，包含以下字段：
            {
              "knowledge_point": "考察的知识点名称（一句话概括）",
              "option_analysis": [
                {
                  "label": "选项标签（如 A、B、C、D）",
                  "text": "选项内容",
                  "is_correct": true或false,
                  "reason": "该选项正确/错误的原因"
                }
              ],
              "solution_steps": ["步骤1", "步骤2", ...],
              "common_mistakes": ["常见错误1", "常见错误2", ...],
              "related_knowledge": ["相关知识1", "相关知识2", ...]
            }
            
            注意：
            - 选项分析中，对于正确选项请给出其正确的原因
            - 对于错误选项请指出错在哪里、为什么错
            - 如果题目包含学生答案，请在常见错误中针对性地分析学生的选择
            - 解题思路要分步骤、清晰明了
            - 使用中文输出
            """;

    private final QuestionRepository questionRepository;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public QuestionAnalysisService(QuestionRepository questionRepository,
                                   LLMService llmService,
                                   ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    /**
     * 分析题目。
     *
     * @param request 分析请求（含题目ID、可选的学生答案和用途）
     * @return 结构化分析结果
     */
    public QuestionAnalysisResult analyze(QuestionAnalysisRequest request) {
        UUID questionId = request.questionId();
        log.info("分析题目: id={}, usage={}", questionId, request.getEffectiveUsage());

        // 加载题目
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("题目", questionId));

        // 构建查询消息
        String userMessage = buildUserMessage(question, request);

        // 调用 LLM 获取结构化分析
        QuestionAnalysisResult result = llmService.askStructured(
                SYSTEM_PROMPT, userMessage,
                QuestionAnalysisResult.class, "question_analysis");

        log.info("题目分析完成: id={}, knowledgePoint={}",
                questionId, result != null ? result.getKnowledgePoint() : "null");

        return result;
    }

    /**
     * 构建发送给 LLM 的用户消息，包含完整的题目信息。
     */
    private String buildUserMessage(Question question, QuestionAnalysisRequest request) {
        StringBuilder sb = new StringBuilder();

        // 题目类型
        sb.append("【题目类型】").append(getTypeLabel(question.getQuestionType())).append("\n");

        // 题目内容
        sb.append("【题目内容】").append(question.getContent()).append("\n");

        // 选项
        String optionsStr = formatOptions(question.getOptions());
        if (optionsStr != null) {
            sb.append("【选项】\n").append(optionsStr);
        }

        // 正确答案
        sb.append("【正确答案】").append(question.getCorrectAnswer()).append("\n");

        // 难度
        if (question.getDifficulty() != null) {
            sb.append("【难度】").append(question.getDifficulty()).append("/5\n");
        }

        // 学生答案（答题后分析）
        String studentAnswer = request.studentAnswer();
        if (studentAnswer != null && !studentAnswer.isBlank()) {
            sb.append("【学生答案】").append(studentAnswer).append("\n");
            sb.append("【分析说明】学生已作答，请结合学生答案分析，指出学生的选择是否正确，\n");
            sb.append("如果不正确请分析错误原因，并在常见错误中突出显示该错误。\n");
        } else {
            sb.append("【分析说明】学生尚未作答，请仅基于题目本身进行分析，\n");
            sb.append("帮助学生理解题目和选项后再作答。\n");
        }

        return sb.toString();
    }

    /**
     * 将 JSON 格式的选项格式化为文本。
     */
    private String formatOptions(JsonNode options) {
        if (options == null) return null;

        StringBuilder sb = new StringBuilder();
        try {
            if (options.isObject()) {
                // { "A": "内容", "B": "内容" }
                options.fieldNames().forEachRemaining(key -> {
                    sb.append(key).append(". ").append(options.get(key).asText()).append("\n");
                });
            } else if (options.isArray()) {
                // [{"label":"A","text":"内容"}, ...] 或 ["A) 内容", ...]
                for (JsonNode item : options) {
                    if (item.has("label") && item.has("text")) {
                        sb.append(item.get("label").asText()).append(". ")
                                .append(item.get("text").asText()).append("\n");
                    } else {
                        sb.append(item.asText()).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("格式化选项失败: {}", e.getMessage());
            sb.append(options.toString());
        }
        return sb.toString();
    }

    private String getTypeLabel(Object type) {
        if (type == null) return "未知";
        String name = type.toString();
        return switch (name) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTIPLE_CHOICE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "简答题";
            case "ESSAY" -> "论述题";
            default -> name;
        };
    }
}
