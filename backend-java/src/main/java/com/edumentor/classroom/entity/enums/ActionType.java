package com.edumentor.classroom.entity.enums;

/**
 * 教学动作类型枚举。
 * 对应前端渲染器支持的所有动作。
 */
public enum ActionType {
    speech,
    speech_with_highlight,
    wb_draw_text,
    wb_draw_diagram,
    quiz,
    discussion,
    scene_transition,
    pause_for_thought,
    code_demo
}
