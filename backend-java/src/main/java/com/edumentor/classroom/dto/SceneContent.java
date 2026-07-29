package com.edumentor.classroom.dto;

import com.edumentor.classroom.entity.enums.SceneType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 场景内容 — LLM 结构化输出的第二段产物。
 * 包含场景的完整教学内容和教学动作列表。
 * <p>
 * contentJson 存储 LLM 生成的丰富教学内容（如 teacherScript、whiteboardItems、questions 等），
 * 供前端渲染时使用。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneContent {
    private SceneType type;
    private String title;
    private String description;
    private List<ActionDTO> actions;
    private List<String> keyPoints;
    private Integer estimatedDurationSeconds;

    /** 丰富的教学内容（JSON化存储，前端按需渲染） */
    private Map<String, Object> contentJson;

    // === 向后兼容字段（旧版 quiz 数据） ===
    private String question;
    private String[] options;
    private Integer correctIndex;
    private String explanation;
}
