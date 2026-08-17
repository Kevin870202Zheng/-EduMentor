-- ============================================================================
-- V26: 扩展 scene_actions.action_type CHECK 约束
-- 支持智慧课堂内容升级（PPT 幻灯片 + HTML 交互组件）新增的 6 个动作类型：
--   show_slide / launch_widget / widget_highlight / widget_set_state /
--   widget_annotate / widget_reveal
-- ============================================================================

ALTER TABLE scene_actions DROP CONSTRAINT scene_actions_action_type_check;

ALTER TABLE scene_actions ADD CONSTRAINT scene_actions_action_type_check
    CHECK (action_type IN (
        'speech', 'speech_with_highlight',
        'wb_draw_text', 'wb_draw_diagram',
        'show_slide', 'launch_widget',
        'widget_highlight', 'widget_set_state',
        'widget_annotate', 'widget_reveal',
        'quiz', 'discussion',
        'scene_transition', 'pause_for_thought',
        'code_demo'
    ));
