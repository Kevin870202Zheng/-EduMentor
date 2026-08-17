package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.ActionDTO;
import com.edumentor.classroom.dto.SceneContent;
import com.edumentor.classroom.entity.enums.ActionType;
import com.edumentor.classroom.entity.enums.SceneType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 场景内容结构校验器 — 在课堂生成落库前校验 LLM 产出的
 * slides / widget / summaryMap 等新结构，保证前端渲染器可正确消费。
 * <p>
 * 纯静态工具、无 Spring 依赖，便于单元测试。校验不合格时调用
 * {@link #sanitize(SceneContent)} 就地降级（移除非法结构、将组件动作
 * 替换为 speech），保证课堂生成不中断（设计文档 §7 降级策略）。
 * </p>
 */
public final class SceneContentValidator {

    private static final int CANVAS_W = 960;
    private static final int CANVAS_H = 540;
    private static final int MARGIN = 50;

    private SceneContentValidator() {
    }

    /**
     * 校验场景内容结构，返回问题列表（空 = 合格）。
     * 问题前缀约定：[slides] / [widget] / [action]，供 sanitize 分类处理。
     */
    public static List<String> validate(SceneContent scene) {
        List<String> problems = new ArrayList<>();
        if (scene == null || scene.getType() == null) return problems;
        Map<String, Object> cm = scene.getContentJson();
        if (cm == null) return problems;
        SceneType type = scene.getType();

        if (type == SceneType.slide || type == SceneType.review) {
            // review 场景若提供了 summaryMap 思维导图，slides 布局为可选
            boolean summaryMapProvided = cm.containsKey("summaryMap");
            if (!(type == SceneType.review && summaryMapProvided) || cm.containsKey("slides")) {
                validateSlides(cm, problems);
            }
        }
        if (type == SceneType.interactive) {
            validateWidget(cm, problems);
        }
        validateActions(scene, cm, problems);
        return problems;
    }

    /** 校验通过则返回 true（无问题）。 */
    public static boolean isValid(SceneContent scene) {
        return validate(scene).isEmpty();
    }

    /**
     * 就地降级修复：移除非法结构、将引用了缺失结构的动作替换为 speech。
     */
    public static void sanitize(SceneContent scene) {
        if (scene == null) return;
        List<String> problems = validate(scene);
        if (problems.isEmpty()) return;
        Map<String, Object> cm = scene.getContentJson();
        if (cm == null) return;
        SceneType type = scene.getType();

        if ((type == SceneType.slide || type == SceneType.review)
                && problems.stream().anyMatch(p -> p.startsWith("[slides]"))) {
            cm.remove("slides");
        }
        if (type == SceneType.interactive) {
            boolean widgetBroken = problems.stream().anyMatch(p -> p.startsWith("[widget]"));
            boolean driverMissing = problems.stream().anyMatch(p -> p.startsWith("[widget-driver]"));
            if (widgetBroken) {
                // 组件完全非法：移除组件并降级全部组件动作
                cm.remove("widget");
                if (scene.getActions() != null) {
                    for (ActionDTO a : scene.getActions()) {
                        if (isWidgetAction(a.getType())) downgradeToSpeech(a);
                    }
                }
            } else if (driverMissing) {
                // 组件可用但缺少 postMessage 驱动：保留组件与 launch_widget，仅降级 widget_* 驱动动作
                if (scene.getActions() != null) {
                    for (ActionDTO a : scene.getActions()) {
                        if (isWidgetAction(a.getType()) && a.getType() != ActionType.launch_widget) {
                            downgradeToSpeech(a);
                        }
                    }
                }
            }
        }
        if (scene.getActions() != null) {
            for (ActionDTO a : scene.getActions()) {
                if (a.getType() == ActionType.show_slide) {
                    boolean summaryOk = "summary".equals(a.getLayoutId()) && type == SceneType.review;
                    if (!summaryOk && !hasLayout(cm, a.getLayoutId())) downgradeToSpeech(a);
                }
            }
        }
    }

    // ───────────────────────── 内部校验 ─────────────────────────

    private static void validateSlides(Map<String, Object> cm, List<String> problems) {
        List<JsonNode> slides = parseSlides(cm.get("slides"));
        if (slides.isEmpty()) {
            problems.add("[slides] 场景缺少 slides 布局数组");
            return;
        }
        for (JsonNode slide : slides) {
            if (!slide.has("layoutId") || slide.get("layoutId").asText().isEmpty()) {
                problems.add("[slides] 页面缺少 layoutId");
            }
            JsonNode elements = slide.get("elements");
            if (elements == null || !elements.isArray() || elements.size() < 3 || elements.size() > 8) {
                problems.add("[slides] 页面 " + slide.path("layoutId").asText("?")
                        + " 元素数量必须为 3~8 个");
                continue;
            }
            for (JsonNode el : elements) {
                if (!el.has("kind") || !el.has("x") || !el.has("y") || !el.has("w") || !el.has("h")) {
                    problems.add("[slides] 元素缺少 kind/x/y/w/h 字段");
                    continue;
                }
                int x = el.get("x").asInt(), y = el.get("y").asInt();
                int w = el.get("w").asInt(), h = el.get("h").asInt();
                if (x < 0 || y < 0 || x + w > CANVAS_W || y + h > CANVAS_H) {
                    problems.add("[slides] 元素 " + el.path("id").asText("?")
                            + " 超出画布边界 (960x540)");
                }
            }
        }
    }

    private static void validateWidget(Map<String, Object> cm, List<String> problems) {
        JsonNode widget = parseWidget(cm.get("widget"));
        if (widget == null) {
            problems.add("[widget] 缺少 widget 组件");
            return;
        }
        String subtype = widget.path("subtype").asText("");
        if (!List.of("simulation", "game", "explore").contains(subtype)) {
            problems.add("[widget] subtype 必须是 simulation/game/explore 之一");
        }
        String html = widget.path("html").asText("");
        if (html.isEmpty()) {
            problems.add("[widget] 缺少 html 字段");
            return;
        }
        if (!html.contains("</html>") || !html.contains("<!DOCTYPE")) {
            problems.add("[widget] html 不是完整 HTML 文档（缺少 DOCTYPE 或 </html>）");
        }
        if (html.contains("src=\"http") || html.contains("src='http")) {
            problems.add("[widget] html 禁止外链 CDN/外部资源");
        }
        if (!html.contains("addEventListener('message'")
                && !html.contains("addEventListener(\"message\"")
                && !html.toLowerCase().contains("onmessage")) {
            problems.add("[widget-driver] html 缺少 postMessage 监听（AI 教师驱动不可用，组件保留）");
        }
    }

    private static void validateActions(SceneContent scene, Map<String, Object> cm, List<String> problems) {
        if (scene.getActions() == null) return;
        List<JsonNode> slides = parseSlides(cm.get("slides"));
        boolean hasWidget = parseWidget(cm.get("widget")) != null;
        for (ActionDTO a : scene.getActions()) {
            if (a.getType() == ActionType.show_slide) {
                String layoutId = a.getLayoutId();
                boolean summaryOk = "summary".equals(layoutId) && scene.getType() == SceneType.review;
                boolean found = summaryOk || slides.stream()
                        .anyMatch(s -> layoutId != null && layoutId.equals(s.path("layoutId").asText()));
                if (!found) {
                    problems.add("[action] show_slide 引用了不存在的 layoutId=" + layoutId);
                }
            }
            if (a.getType() == ActionType.launch_widget && !hasWidget) {
                problems.add("[action] launch_widget 但缺少 widget 组件");
            }
            if (isWidgetAction(a.getType()) && (a.getTarget() == null || a.getTarget().isEmpty())
                    && a.getType() != ActionType.launch_widget && a.getType() != ActionType.widget_set_state) {
                problems.add("[action] " + a.getType() + " 缺少 target");
            }
        }
    }

    // ───────────────────────── 工具方法 ─────────────────────────

    private static boolean hasLayout(Map<String, Object> cm, String layoutId) {
        if (layoutId == null) return false;
        return parseSlides(cm.get("slides")).stream()
                .anyMatch(s -> layoutId.equals(s.path("layoutId").asText()));
    }

    private static boolean isWidgetAction(ActionType t) {
        return t == ActionType.launch_widget || t == ActionType.widget_highlight
                || t == ActionType.widget_set_state || t == ActionType.widget_annotate
                || t == ActionType.widget_reveal;
    }

    private static void downgradeToSpeech(ActionDTO a) {
        String text = a.getContent() != null ? a.getContent()
                : (a.getSpeech() != null ? a.getSpeech() : "让我们继续学习这个知识点。");
        a.setType(ActionType.speech);
        a.setText(text);
        a.setContent(null);
        a.setSpeech(null);
        a.setLayoutId(null);
        a.setWidgetKey(null);
        a.setIntro(null);
        a.setTarget(null);
        a.setState(null);
    }

    private static List<JsonNode> parseSlides(Object raw) {
        List<JsonNode> list = new ArrayList<>();
        if (raw == null) return list;
        try {
            JsonNode arr = new ObjectMapper().readTree(raw.toString());
            if (arr != null && arr.isArray()) arr.forEach(list::add);
        } catch (Exception ignored) {
        }
        return list;
    }

    private static JsonNode parseWidget(Object raw) {
        if (raw == null) return null;
        try {
            JsonNode node = new ObjectMapper().readTree(raw.toString());
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }
}
