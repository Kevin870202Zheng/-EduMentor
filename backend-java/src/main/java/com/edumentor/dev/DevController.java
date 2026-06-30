package com.edumentor.dev;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.entity.enums.QuestionType;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 开发辅助接口 — 运行时工具（仅在 dev profile 生效）
 */
@RestController
@RequestMapping("/api/dev")
public class DevController {

    private static final Logger log = LoggerFactory.getLogger(DevController.class);

    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionRepository questionRepository;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public DevController(KnowledgePointRepository knowledgePointRepository,
                         QuestionRepository questionRepository,
                         LLMService llmService,
                         ObjectMapper objectMapper) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.questionRepository = questionRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    /**
     * 为指定课程中缺少习题的知识点批量补充习题。
     * 每批处理 count 个知识点，每个知识点生成 3 道题。
     */
    @PostMapping("/fill-questions/{courseCode}")
    public ApiResponse<Map<String, Object>> fillQuestions(
            @PathVariable String courseCode,
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "0") int offset) {

        // 查找课程
        UUID courseId = findCourseId(courseCode);
        if (courseId == null) {
            return ApiResponse.error(404, "课程不存在: " + courseCode);
        }

        // 获取缺少习题的知识点
        List<KnowledgePoint> kps = knowledgePointRepository.findByCourseId(courseId);
        List<KnowledgePoint> needFill = new ArrayList<>();
        for (KnowledgePoint kp : kps) {
            if (questionRepository.findByKnowledgePointId(kp.getId()).isEmpty()) {
                needFill.add(kp);
            }
        }

        // 按 offset/count 取一批
        int from = Math.min(offset, needFill.size());
        int to = Math.min(from + count, needFill.size());
        List<KnowledgePoint> batch = needFill.subList(from, to);

        if (batch.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalKps", kps.size());
            result.put("filled", kps.size() - needFill.size());
            result.put("remaining", 0);
            result.put("done", true);
            return ApiResponse.success(result, "全部知识点已有习题");
        }

        // 构建 LLM 提示
        StringBuilder sb = new StringBuilder();
        sb.append("为以下每个知识点生成3道练习题。题型要多样（单选/多选/判断/填空/简答）。\n");
        sb.append("输出JSON格式：{\"questions\":[{\"kpName\":\"知识点名\",\"content\":\"题目\",\"type\":\"SINGLE_CHOICE\",\"options\":[\"A. 选项\",\"B. 选项\",\"C. 选项\",\"D. 选项\"],\"correctAnswer\":\"A\",\"explanation\":\"解析\"}]}\n\n");
        sb.append("知识点列表：\n");
        for (int i = 0; i < batch.size(); i++) {
            sb.append(i + 1).append(". ").append(batch.get(i).getName()).append("\n");
        }

        // 调用 LLM
        FillResult llmResult;
        try {
            llmResult = llmService.askStructured(
                    "你是一个课程习题生成助手，严格输出JSON。",
                    sb.toString(),
                    FillResult.class,
                    "fill_questions"
            );
        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage());
            return ApiResponse.error(500, "LLM 调用失败: " + e.getMessage());
        }

        if (llmResult == null || llmResult.questions == null) {
            return ApiResponse.error(500, "LLM 返回为空");
        }

        // 保存习题
        int saved = 0;
        int skipped = 0;
        for (FilledQuestion fq : llmResult.questions) {
            if (fq.content == null || fq.content.isBlank()) continue;

            // 匹配知识点
            UUID kpId = findKpId(courseId, batch, fq.kpName);
            if (kpId == null) continue;

            // 去重
            if (questionRepository.existsByContentAndKnowledgePointId(fq.content.trim(), kpId)) {
                skipped++;
                continue;
            }

            Question q = new Question();
            q.setKnowledgePointId(kpId);
            q.setCourseId(courseId);
            q.setContent(fq.content);
            q.setCorrectAnswer(fq.correctAnswer != null ? fq.correctAnswer : "");
            q.setExplanation(fq.explanation != null ? fq.explanation : "");

            // 类型映射
            String typeStr = fq.type != null ? fq.type.toUpperCase() : "SINGLE_CHOICE";
            try {
                q.setQuestionType(QuestionType.valueOf(typeStr));
            } catch (Exception e) {
                q.setQuestionType(QuestionType.SINGLE_CHOICE);
            }

            // 选项
            if (fq.options != null && !fq.options.isEmpty()
                    && q.getQuestionType() != QuestionType.ESSAY
                    && q.getQuestionType() != QuestionType.SHORT_ANSWER) {
                Map<String, String> optMap = new LinkedHashMap<>();
                for (int i = 0; i < fq.options.size(); i++) {
                    String label = String.valueOf((char) ('A' + i));
                    String text = fq.options.get(i);
                    if (text != null && text.contains(". ")) text = text.substring(text.indexOf(". ") + 2);
                    if (text != null) optMap.put(label, text.trim());
                }
                if (!optMap.isEmpty()) {
                    q.setOptions(objectMapper.valueToTree(optMap));
                }
            }

            q.setDifficulty(3);
            q.setIsPublished(true);
            questionRepository.save(q);
            saved++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchSize", batch.size());
        result.put("saved", saved);
        result.put("skipped", skipped);
        result.put("totalKps", kps.size());
        result.put("filled", kps.size() - needFill.size() + saved);
        result.put("remaining", Math.max(0, needFill.size() - to));
        result.put("nextOffset", to);
        result.put("done", to >= needFill.size());

        log.info("补习题完成: courseCode={}, batch={}, saved={}, skipped={}, remaining={}",
                courseCode, batch.size(), saved, skipped, result.get("remaining"));

        return ApiResponse.success(result, "本次补充 " + saved + " 道习题");
    }

    private UUID findCourseId(String courseCode) {
        try (var conn = java.sql.DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/edumentor_dev", "roosevelt", "")) {
            var stmt = conn.prepareStatement("SELECT id FROM courses WHERE course_code = ?");
            stmt.setString(1, courseCode);
            var rs = stmt.executeQuery();
            if (rs.next()) return (UUID) rs.getObject(1);
        } catch (Exception e) {
            log.warn("查询课程ID失败: {}", e.getMessage());
        }
        return null;
    }

    private UUID findKpId(UUID courseId, List<KnowledgePoint> batch, String kpName) {
        if (kpName == null || kpName.isBlank()) return batch.isEmpty() ? null : batch.get(0).getId();
        String target = kpName.trim().toLowerCase().replaceAll("\\s+", "");
        for (KnowledgePoint kp : batch) {
            if (kp.getName().toLowerCase().replaceAll("\\s+", "").equals(target)) {
                return kp.getId();
            }
        }
        // 还从整个课程查找
        var opt = knowledgePointRepository.findByNameAndCourseId(kpName.trim(), courseId);
        return opt.map(KnowledgePoint::getId).orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FillResult {
        public List<FilledQuestion> questions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilledQuestion {
        public String kpName;
        public String content;
        public String type;
        public List<String> options;
        public String correctAnswer;
        public String explanation;
    }
}
