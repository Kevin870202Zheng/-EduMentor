package com.edumentor.classroom.dto;

import com.edumentor.classroom.entity.enums.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 教学动作 DTO — 用于 API 传输和 LLM 结构化产出。
 * 对应前端播放器可渲染的一个教学动作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionDTO {
    private ActionType type;
    private String text;
    private String content;
    private String position;
    private Integer duration;
    private Map<String, Object> params;

    // Quiz 专用字段
    private String question;
    private String[] options;
    private Integer correctIndex;
    private String explanation;

    // Discussion 专用字段
    private String topic;
    private String prompt;

    // 白板专用
    private String wbContent;
    private String wbStyle;

    // 幻灯片专用（show_slide）
    private String layoutId;
    private String speech;

    // 交互组件专用（launch_widget / widget_*）
    private String widgetKey;
    private String intro;
    private String target;
    private Map<String, Object> state;
}
