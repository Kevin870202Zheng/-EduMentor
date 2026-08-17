package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.ActionDTO;
import com.edumentor.classroom.dto.SceneContent;
import com.edumentor.classroom.entity.enums.ActionType;
import com.edumentor.classroom.entity.enums.SceneType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SceneContentValidator} 单元测试 — 校验智慧课堂内容升级后
 * slides / widget / summaryMap 等新结构的完整性与降级路径。
 */
@DisplayName("SceneContentValidator — 场景内容结构校验")
class SceneContentValidatorTest {

    // ───────────────────────── 合法结构样例 ─────────────────────────

    private static String validSlidesJson() {
        return """
                [{"layoutId":"s1","title":"生活中的概率","elements":[
                  {"id":"t1","kind":"text","x":60,"y":80,"w":840,"h":76,"content":"抛硬币100次","fontSize":28},
                  {"id":"c1","kind":"chart","x":140,"y":200,"w":480,"h":280,"chartType":"bar","data":{"labels":["正","反"],"series":[[51,49]]}},
                  {"id":"b1","kind":"shape","x":680,"y":230,"w":220,"h":120,"shape":"round","fill":"#f0f5ff","label":"P=1/2"},
                  {"id":"l1","kind":"line","x":640,"y":290,"w":40,"h":1,"from":[0,0],"to":[40,0],"arrow":true}
                ]}]""";
    }

    private static String validWidgetJson() {
        return """
                {"subtype":"simulation","title":"刹车距离模拟器","config":{"variables":[{"name":"speed","label":"车速","min":20,"max":120,"default":60}]},
                 "html":"<!DOCTYPE html><html><head><style></style></head><body><input id=\\"speed-slider\\" type=\\"range\\"><div id=\\"distance-display\\"></div><script>window.addEventListener('message', function(e){var d=e.data||{};if(d.type==='SET_WIDGET_STATE'){}});</script></body></html>"}""";
    }

    private static SceneContent slideScene(String slidesJson) {
        Map<String, Object> cm = new HashMap<>();
        cm.put("teacherScript", "讲解词");
        cm.put("slides", slidesJson);
        return SceneContent.builder().type(SceneType.slide).title("slide")
                .contentJson(cm)
                .actions(List.of(ActionDTO.builder().type(ActionType.show_slide).layoutId("s1").build()))
                .build();
    }

    private static SceneContent interactiveScene(String widgetJson) {
        Map<String, Object> cm = new HashMap<>();
        cm.put("widget", widgetJson);
        return SceneContent.builder().type(SceneType.interactive).title("interactive")
                .contentJson(cm)
                .actions(List.of(
                        ActionDTO.builder().type(ActionType.launch_widget).widgetKey("widget").build(),
                        ActionDTO.builder().type(ActionType.widget_set_state)
                                .state(Map.of("speed", 80)).build(),
                        ActionDTO.builder().type(ActionType.widget_highlight).target("#distance-display").build()))
                .build();
    }

    // ───────────────────────── 测试用例 ─────────────────────────

    @Nested
    @DisplayName("slide 场景 slides 布局校验")
    class SlideValidationTests {

        @Test
        @DisplayName("合法 slides — 校验通过")
        void validSlides() {
            assertThat(SceneContentValidator.validate(slideScene(validSlidesJson()))).isEmpty();
            assertThat(SceneContentValidator.isValid(slideScene(validSlidesJson()))).isTrue();
        }

        @Test
        @DisplayName("缺少 slides — 校验失败")
        void missingSlides() {
            Map<String, Object> cm = new HashMap<>();
            cm.put("teacherScript", "讲解词");
            SceneContent scene = SceneContent.builder().type(SceneType.slide)
                    .contentJson(cm).actions(List.of()).build();
            assertThat(SceneContentValidator.validate(scene))
                    .anyMatch(p -> p.startsWith("[slides]"));
        }

        @Test
        @DisplayName("元素数量不足 3 个 — 校验失败")
        void tooFewElements() {
            String bad = """
                    [{"layoutId":"s1","elements":[
                      {"id":"t1","kind":"text","x":60,"y":80,"w":100,"h":40,"content":"a"},
                      {"id":"t2","kind":"text","x":60,"y":140,"w":100,"h":40,"content":"b"}
                    ]}]""";
            assertThat(SceneContentValidator.validate(slideScene(bad)))
                    .anyMatch(p -> p.contains("元素数量必须为 3~8"));
        }

        @Test
        @DisplayName("元素越界（超出画布）— 校验失败")
        void outOfBounds() {
            String bad = """
                    [{"layoutId":"s1","elements":[
                      {"id":"t1","kind":"text","x":900,"y":100,"w":300,"h":50,"content":"a"},
                      {"id":"t2","kind":"text","x":60,"y":100,"w":100,"h":40,"content":"b"},
                      {"id":"t3","kind":"text","x":60,"y":160,"w":100,"h":40,"content":"c"}
                    ]}]""";
            List<String> problems = SceneContentValidator.validate(slideScene(bad));
            assertThat(problems).anyMatch(p -> p.contains("边界"));
        }
    }

    @Nested
    @DisplayName("interactive 场景 widget 校验")
    class WidgetValidationTests {

        @Test
        @DisplayName("合法 widget — 校验通过")
        void validWidget() {
            assertThat(SceneContentValidator.validate(interactiveScene(validWidgetJson()))).isEmpty();
        }

        @Test
        @DisplayName("html 缺少 </html> — 校验失败")
        void incompleteHtml() {
            String bad = validWidgetJson().replace("</html>", "");
            assertThat(SceneContentValidator.validate(interactiveScene(bad)))
                    .anyMatch(p -> p.startsWith("[widget]"));
        }

        @Test
        @DisplayName("html 外链 CDN — 校验失败")
        void externalCdn() {
            String bad = validWidgetJson().replace("<style>",
                    "<script src=\\\"https://cdn.example.com/lib.js\\\"></script><style>");
            assertThat(SceneContentValidator.validate(interactiveScene(bad)))
                    .anyMatch(p -> p.contains("禁止外链"));
        }

        @Test
        @DisplayName("html 缺少 postMessage 监听 — 校验失败")
        void noPostMessage() {
            String bad = validWidgetJson().replace("window.addEventListener('message', function(e){var d=e.data||{};if(d.type==='SET_WIDGET_STATE'){}});",
                    "console.log('no listener');");
            assertThat(SceneContentValidator.validate(interactiveScene(bad)))
                    .anyMatch(p -> p.contains("postMessage"));
        }
    }

    @Nested
    @DisplayName("降级路径 sanitize")
    class SanitizeTests {

        @Test
        @DisplayName("widget 非法 — 移除 widget 并将组件动作降级为 speech")
        void sanitizeInvalidWidget() {
            String badWidget = """
                    {"subtype":"simulation","html":"<div>broken"}""";
            SceneContent scene = interactiveScene(badWidget);
            SceneContentValidator.sanitize(scene);

            assertThat(scene.getContentJson()).doesNotContainKey("widget");
            assertThat(scene.getActions()).allMatch(a -> a.getType() != ActionType.launch_widget
                    && a.getType() != ActionType.widget_highlight
                    && a.getType() != ActionType.widget_set_state);
            // 降级后的 speech 动作保留讲解文本
            assertThat(scene.getActions().stream().filter(a -> a.getType() == ActionType.speech)).isNotEmpty();
        }

        @Test
        @DisplayName("show_slide 引用不存在的 layoutId — 降级为 speech")
        void sanitizeMissingLayout() {
            SceneContent scene = slideScene(validSlidesJson());
            scene.getActions().get(0).setLayoutId("not-exist");
            SceneContentValidator.sanitize(scene);

            assertThat(scene.getActions()).allMatch(a -> a.getType() != ActionType.show_slide);
        }

        @Test
        @DisplayName("review 场景 show_slide layoutId=summary — 视为合法")
        void reviewSummaryMapOk() {
            Map<String, Object> cm = new HashMap<>();
            cm.put("teacherScript", "回顾");
            cm.put("summaryMap", """
                    {"root":"法律责任","branches":[{"label":"民事责任","children":["违约"]}]}""");
            SceneContent scene = SceneContent.builder().type(SceneType.review).title("review")
                    .contentJson(cm)
                    .actions(List.of(ActionDTO.builder().type(ActionType.show_slide).layoutId("summary").build()))
                    .build();
            assertThat(SceneContentValidator.validate(scene)).isEmpty();
        }

        @Test
        @DisplayName("合法的 slide 场景 sanitize 后保持不变")
        void sanitizeNoopForValid() {
            SceneContent scene = slideScene(validSlidesJson());
            List<ActionDTO> before = List.copyOf(scene.getActions());
            SceneContentValidator.sanitize(scene);
            assertThat(scene.getActions()).hasSize(before.size());
            assertThat(scene.getActions().get(0).getType()).isEqualTo(ActionType.show_slide);
            assertThat(scene.getContentJson()).containsKey("slides");
        }
    }
}
