package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.ActionDTO;
import com.edumentor.classroom.dto.SceneContent;
import com.edumentor.classroom.dto.SceneOutline;
import com.edumentor.classroom.entity.enums.ActionType;
import com.edumentor.classroom.entity.enums.SceneType;
import com.edumentor.engine.llm.LLMService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 场景内容生成器 — 课堂生成管线第二阶段。
 * <p>
 * 使用高质量的 prompt 模板，指导 LLM 生成口语化的教师讲解词、
 * 白板视觉辅助内容、引导性问题和高质量的选择题。
 * </p>
 */
@Component
public class SceneContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(SceneContentGenerator.class);
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptLoader;
    private static final int MAX_RETRIES = 2;

    public SceneContentGenerator(LLMService llmService, ObjectMapper objectMapper,
                                 PromptTemplateLoader promptLoader) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
    }

    public SceneContent generate(SceneOutline outline, int difficulty, String context) {
        return generate(outline, difficulty, context, null);
    }

    public SceneContent generate(SceneOutline outline, int difficulty, String context, Map<String, String> extraContext) {
        // slide/review 场景：讲稿优先两阶段生成（视觉反向对齐讲稿；失败自动降级单轮）
        if (outline.getType() == SceneType.slide || outline.getType() == SceneType.review) {
            SceneContent twoPhase = generateScriptFirst(outline, difficulty, context, extraContext);
            if (twoPhase != null) {
                log.info("Two-phase generation OK for '{}' (type={})", outline.getTitle(), outline.getType());
                return twoPhase;
            }
            log.info("Two-phase generation failed for '{}', fallback to single-pass", outline.getTitle());
        }

        String systemPrompt = promptLoader.loadRaw("content-system.md");
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("sceneTitle", outline.getTitle() != null ? outline.getTitle() : "");
        vars.put("sceneType", outline.getType() != null ? outline.getType().name() : "slide");
        vars.put("sceneDescription", outline.getDescription() != null ? outline.getDescription() : "");
        vars.put("keyPoints", outline.getKeyPoints() != null ? String.join("、", outline.getKeyPoints()) : "");
        vars.put("teachingObjective", outline.getTeachingObjective() != null ? outline.getTeachingObjective() : "");
        vars.put("knowledgePointName", context != null ? context : "");
        vars.put("difficulty", String.valueOf(difficulty));

        // 增强上下文：传入知识点详细内容（当前场景对应的知识点内容）
        vars.put("knowledgePointContent", extraContext != null && extraContext.containsKey("knowledgePointContent")
                ? extraContext.get("knowledgePointContent") : "");
        vars.put("courseName", extraContext != null && extraContext.containsKey("courseName")
                ? extraContext.get("courseName") : "");
        vars.put("aggregatedContent", extraContext != null && extraContext.containsKey("aggregatedContent")
                ? extraContext.get("aggregatedContent") : "");

        String userPrompt = promptLoader.load("content-user.md", vars);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String raw = llmService.askStructured(systemPrompt, userPrompt, String.class, "scene-content");
                SceneContent content = parseSceneContent(raw, outline);
                if (content != null && content.getActions() != null && !content.getActions().isEmpty()) {
                    return content;
                }
                log.warn("Attempt {}/{}: parsed content has no actions", attempt + 1, MAX_RETRIES + 1);
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed for '{}': {}", attempt + 1, MAX_RETRIES + 1, outline.getTitle(), e.getMessage());
                if (attempt == MAX_RETRIES) return createDefaultContent(outline, difficulty, context);
            }
        }
        return createDefaultContent(outline, difficulty, context);
    }

    /**
     * 讲稿优先两阶段生成（阶段A 讲解稿 → 阶段B 视觉对齐设计）。
     *
     * @return 生成成功的 SceneContent；任何异常/解析失败返回 null（调用方降级单轮）
     */
    private SceneContent generateScriptFirst(SceneOutline outline, int difficulty, String context,
                                             Map<String, String> extraContext) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("sceneTitle", outline.getTitle() != null ? outline.getTitle() : "");
        vars.put("sceneDescription", outline.getDescription() != null ? outline.getDescription() : "");
        vars.put("keyPoints", outline.getKeyPoints() != null ? String.join("、", outline.getKeyPoints()) : "");
        vars.put("teachingObjective", outline.getTeachingObjective() != null ? outline.getTeachingObjective() : "");
        vars.put("knowledgePointName", context != null ? context : "");
        vars.put("difficulty", String.valueOf(difficulty));
        vars.put("courseName", extraContext != null && extraContext.containsKey("courseName")
                ? extraContext.get("courseName") : "");
        vars.put("knowledgePointContent", extraContext != null && extraContext.containsKey("knowledgePointContent")
                ? extraContext.get("knowledgePointContent") : "");
        vars.put("aggregatedContent", extraContext != null && extraContext.containsKey("aggregatedContent")
                ? extraContext.get("aggregatedContent") : "");

        try {
            // ── 阶段 A：生成讲解稿（逐句 + 语义标签 + 关键词） ──
            String scriptSystem = promptLoader.loadRaw("script-first-system.md");
            String scriptUser = promptLoader.load("script-first-user.md", vars);
            String scriptRaw = llmService.askStructured(scriptSystem, scriptUser, String.class, "scene-script");
            JsonNode scriptNode = objectMapper.readTree(scriptRaw);
            JsonNode sentences = scriptNode.path("script").path("sentences");
            if (!sentences.isArray() || sentences.isEmpty()) {
                log.warn("Two-phase: script-first returned no sentences");
                return null;
            }

            // ── 阶段 B：基于讲稿设计 slides + actions（视觉对齐） ──
            String visualSystem = promptLoader.loadRaw("visual-align-system.md");
            Map<String, String> varsB = new LinkedHashMap<>(vars);
            varsB.put("scriptSentences", sentences.toString());
            String visualUser = promptLoader.load("visual-align-user.md", varsB);
            String raw = llmService.askStructured(visualSystem, visualUser, String.class, "scene-content");
            SceneContent content = parseSceneContent(raw, outline);
            if (content != null && content.getActions() != null && !content.getActions().isEmpty()) {
                return content;
            }
            log.warn("Two-phase: visual-align returned no actions");
        } catch (Exception e) {
            log.warn("Two-phase generation error for '{}': {}", outline.getTitle(), e.getMessage());
        }
        return null;
    }

    private SceneContent parseSceneContent(String raw, SceneOutline outline) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        SceneContent.SceneContentBuilder builder = SceneContent.builder();
        builder.type(parseSceneType(root.has("type") ? root.get("type").asText() : outline.getType().name()));
        builder.title(root.has("title") ? root.get("title").asText() : outline.getTitle());
        builder.description(root.has("description") ? root.get("description").asText() : outline.getDescription());

        if (root.has("keyPoints") && root.get("keyPoints").isArray()) {
            List<String> kps = new ArrayList<>();
            for (JsonNode kp : root.get("keyPoints")) kps.add(kp.asText());
            builder.keyPoints(kps);
        } else builder.keyPoints(outline.getKeyPoints());

        if (root.has("estimatedDurationSeconds")) builder.estimatedDurationSeconds(root.get("estimatedDurationSeconds").asInt());
        else if (outline.getEstimatedDurationSeconds() != null) builder.estimatedDurationSeconds(outline.getEstimatedDurationSeconds());

        Map<String, Object> contentMap = new HashMap<>();
        if (root.has("content") && root.get("content").isObject()) {
            JsonNode cn = root.get("content");
            if (cn.has("teacherScript")) contentMap.put("teacherScript", cn.get("teacherScript").asText());
            if (cn.has("introScript")) contentMap.put("introScript", cn.get("introScript").asText());
            if (cn.has("whiteboardItems")) contentMap.put("whiteboardItems", cn.get("whiteboardItems").toString());
            if (cn.has("guidingQuestions")) {
                List<String> qs = new ArrayList<>();
                for (JsonNode q : cn.get("guidingQuestions")) qs.add(q.asText());
                contentMap.put("guidingQuestions", qs);
            }
            if (cn.has("questions")) contentMap.put("questions", cn.get("questions").toString());
            if (cn.has("discussionTopic")) contentMap.put("discussionTopic", cn.get("discussionTopic").asText());
            if (cn.has("knowledgeMap")) {
                List<String> km = new ArrayList<>();
                for (JsonNode n : cn.get("knowledgeMap")) km.add(n.asText());
                contentMap.put("knowledgeMap", km);
            }
            if (cn.has("takeawayMessage")) contentMap.put("takeawayMessage", cn.get("takeawayMessage").asText());
            // 幻灯片布局（slide/review 场景）
            if (cn.has("slides")) contentMap.put("slides", cn.get("slides").toString());
            // 交互组件（interactive 场景）
            if (cn.has("widget")) contentMap.put("widget", cn.get("widget").toString());
            // 总结思维导图（review 场景）
            if (cn.has("summaryMap")) contentMap.put("summaryMap", cn.get("summaryMap").toString());
        }

        List<ActionDTO> actions = new ArrayList<>();
        if (root.has("actions") && root.get("actions").isArray()) {
            for (JsonNode an : root.get("actions")) {
                ActionDTO a = parseAction(an);
                if (a != null) actions.add(a);
            }
        }

        if (actions.isEmpty()) actions = buildDefaultActions(outline, contentMap);
        if (outline.getType() == SceneType.quiz && actions.stream().noneMatch(a -> a.getType() == ActionType.quiz)) {
            List<ActionDTO> qa = buildQuizActions(contentMap, outline);
            if (!qa.isEmpty()) actions = qa;
        }

        builder.actions(actions);
        builder.contentJson(contentMap);
        return builder.build();
    }

    private ActionDTO parseAction(JsonNode node) {
        if (!node.has("type")) return null;
        String type = node.get("type").asText();
        ActionType at;
        try { at = ActionType.valueOf(type); }
        catch (IllegalArgumentException e) { at = "text".equals(type) ? ActionType.speech : ActionType.speech; }

        var b = ActionDTO.builder().type(at).duration(node.has("duration") ? node.get("duration").asInt() : 3000);
        if (node.has("text")) b.text(node.get("text").asText());
        if (node.has("content")) b.content(node.get("content").asText());
        if (node.has("position")) b.position(node.get("position").asText());
        if (node.has("style")) b.params(Map.of("style", node.get("style").asText()));
        if (node.has("question")) b.question(node.get("question").asText());
        if (node.has("options") && node.get("options").isArray()) {
            List<String> opts = new ArrayList<>();
            for (JsonNode o : node.get("options")) opts.add(o.asText().replaceFirst("^[A-Z]\\.\\s*", ""));
            b.options(opts.toArray(new String[0]));
        }
        if (node.has("correctIndex")) b.correctIndex(node.get("correctIndex").asInt());
        if (node.has("explanation")) b.explanation(node.get("explanation").asText());
        if (node.has("topic")) b.topic(node.get("topic").asText());
        if (node.has("prompt")) b.prompt(node.get("prompt").asText());
        // 幻灯片/交互组件字段
        if (node.has("layoutId")) b.layoutId(node.get("layoutId").asText());
        if (node.has("speech")) b.speech(node.get("speech").asText());
        if (node.has("widgetKey")) b.widgetKey(node.get("widgetKey").asText());
        if (node.has("intro")) b.intro(node.get("intro").asText());
        if (node.has("target")) b.target(node.get("target").asText());
        if (node.has("state") && node.get("state").isObject()) {
            b.state(objectMapper.convertValue(node.get("state"), Map.class));
        }
        // 句-页联动：当前讲解句对应的高亮元素（M4）
        if (node.has("highlightElementIds") && node.get("highlightElementIds").isArray()) {
            List<String> hl = new ArrayList<>();
            for (JsonNode h : node.get("highlightElementIds")) hl.add(h.asText());
            b.highlightElementIds(hl);
        }
        return b.build();
    }

    private List<ActionDTO> buildDefaultActions(SceneOutline outline, Map<String, Object> cm) {
        List<ActionDTO> actions = new ArrayList<>();
        String script = (String) cm.get("teacherScript");
        String intro = (String) cm.get("introScript");
        String opening = intro != null ? intro : (script != null ? script : "同学们好，我们来学习" + outline.getTitle());
        actions.add(ActionDTO.builder().type(ActionType.speech).text(opening).duration(Math.min(opening.length() * 50, 8000)).build());
        if (outline.getKeyPoints() != null && !outline.getKeyPoints().isEmpty()) {
            actions.add(ActionDTO.builder().type(ActionType.wb_draw_text)
                    .content("核心要点：\n" + String.join("\n", outline.getKeyPoints())).position("center").build());
        }
        if (intro != null && script != null) {
            actions.add(ActionDTO.builder().type(ActionType.speech).text(script).duration(Math.min(script.length() * 50, 15000)).build());
        }
        if (cm.containsKey("guidingQuestions")) {
            @SuppressWarnings("unchecked") List<String> qs = (List<String>) cm.get("guidingQuestions");
            if (!qs.isEmpty()) { actions.add(ActionDTO.builder().type(ActionType.speech).text(qs.get(0)).duration(5000).build());
                actions.add(ActionDTO.builder().type(ActionType.pause_for_thought).duration(3000).build()); }
        } else { actions.add(ActionDTO.builder().type(ActionType.pause_for_thought).duration(2000).build()); }
        actions.add(ActionDTO.builder().type(ActionType.speech).text("以上就是" + outline.getTitle() + "的内容。").duration(3000).build());
        return actions;
    }

    @SuppressWarnings("unchecked")
    private List<ActionDTO> buildQuizActions(Map<String, Object> cm, SceneOutline outline) {
        List<ActionDTO> actions = new ArrayList<>();
        String intro = cm.containsKey("introScript") ? (String) cm.get("introScript") : "来做几道练习题检验一下吧！";
        actions.add(ActionDTO.builder().type(ActionType.speech).text(intro).duration(4000).build());
        String qJson = cm.containsKey("questions") ? (String) cm.get("questions") : null;
        if (qJson != null) {
            try {
                for (JsonNode qn : objectMapper.readTree(qJson)) {
                    String q = qn.has("question") ? qn.get("question").asText() : "";
                    List<String> opts = new ArrayList<>();
                    if (qn.has("options") && qn.get("options").isArray()) {
                        for (JsonNode o : qn.get("options")) {
                            String t = o.has("label") ? o.get("label").asText() : o.asText();
                            opts.add(t.replaceFirst("^[A-Z]\\.\\s*", ""));
                        }
                    }
                    int ci = qn.has("correctIndex") ? qn.get("correctIndex").asInt() : 0;
                    String exp = qn.has("explanation") ? qn.get("explanation").asText() : "";
                    String ana = qn.has("analysis") ? qn.get("analysis").asText() : "";
                    actions.add(ActionDTO.builder().type(ActionType.quiz).question(q)
                            .options(opts.toArray(new String[0])).correctIndex(ci)
                            .explanation(!exp.isEmpty() ? exp : ana).build());
                }
            } catch (Exception e) { log.warn("Failed to parse questions: {}", e.getMessage()); }
        }
        if (actions.size() <= 1) {
            // 基于大纲信息生成有意义的默认题目
            String defaultQ = "关于「" + outline.getTitle() + "」，以下理解正确的是？";
            String kp1 = outline.getKeyPoints() != null && outline.getKeyPoints().size() > 0
                    ? outline.getKeyPoints().get(0) : "核心概念";
            String kp2 = outline.getKeyPoints() != null && outline.getKeyPoints().size() > 1
                    ? outline.getKeyPoints().get(1) : "基本原理";
            actions.add(ActionDTO.builder().type(ActionType.quiz)
                    .question(defaultQ)
                    .options(new String[]{
                            kp1 + "是这一节最核心的概念",
                            kp2 + "与本节课内容无关",
                            "以上说法都不正确",
                            kp1 + "和" + kp2 + "是同一个概念"
                    }).correctIndex(0)
                    .explanation(kp1 + "是本节课的核心内容，需要重点掌握。" +
                            (outline.getDescription() != null ? " " + outline.getDescription() : ""))
                    .build());
        }
        actions.add(ActionDTO.builder().type(ActionType.speech).text("题目都做完了，我们来看看解析。").duration(3000).build());
        return actions;
    }

    private SceneType parseSceneType(String str) {
        try { return SceneType.valueOf(str); } catch (IllegalArgumentException e) { return SceneType.slide; }
    }

    private SceneContent createDefaultContent(SceneOutline outline, int difficulty, String context) {
        List<ActionDTO> actions = new ArrayList<>();
        actions.add(ActionDTO.builder().type(ActionType.speech)
                .text("同学们好，今天我们来学习" + (context != null ? context : "") + "中的" + outline.getTitle() + "。")
                .duration(3000).build());
        if (outline.getKeyPoints() != null && !outline.getKeyPoints().isEmpty()) {
            actions.add(ActionDTO.builder().type(ActionType.wb_draw_text)
                    .content("核心要点：\n" + String.join("\n", outline.getKeyPoints())).position("center").build());
        }
        actions.add(ActionDTO.builder().type(ActionType.speech)
                .text(outline.getDescription() != null ? outline.getDescription() : "让我们深入学习这个知识点。")
                .duration(5000).build());
        actions.add(ActionDTO.builder().type(ActionType.pause_for_thought).duration(2000).build());
        if (outline.getType() == SceneType.quiz) {
            actions.add(ActionDTO.builder().type(ActionType.quiz)
                    .question("关于" + (context != null ? context : "") + "，下列说法正确的是？")
                    .options(new String[]{"完全理解", "基本理解", "部分理解", "需要重新学习"}).correctIndex(0)
                    .explanation("请根据你的实际情况选择。").build());
        }
        actions.add(ActionDTO.builder().type(ActionType.speech)
                .text(outline.getTitle() + "的内容就讲到这里，接下来我们进入下一部分。").duration(3000).build());
        Map<String, Object> cm = new HashMap<>();
        cm.put("teacherScript", outline.getDescription());
        return SceneContent.builder().type(outline.getType()).title(outline.getTitle())
                .description(outline.getDescription()).actions(actions).contentJson(cm)
                .estimatedDurationSeconds(outline.getEstimatedDurationSeconds() != null ? outline.getEstimatedDurationSeconds() : actions.size() * 5)
                .keyPoints(outline.getKeyPoints()).build();
    }
}
