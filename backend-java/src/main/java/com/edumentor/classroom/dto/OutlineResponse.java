package com.edumentor.classroom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * LLM 大纲响应的顶层解析结构。
 * 注意：ignoreUnknown = true 允许 LLM 返回额外的字段而不导致解析失败。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OutlineResponse {
    private List<SceneOutline> scenes;
    private String classroomTitle;
    private String classroomDescription;

    public OutlineResponse() {}

    public List<SceneOutline> getScenes() { return scenes; }
    public void setScenes(List<SceneOutline> scenes) { this.scenes = scenes; }

    public String getClassroomTitle() { return classroomTitle; }
    public void setClassroomTitle(String classroomTitle) { this.classroomTitle = classroomTitle; }

    public String getClassroomDescription() { return classroomDescription; }
    public void setClassroomDescription(String classroomDescription) { this.classroomDescription = classroomDescription; }
}
