package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 课堂完整详情 DTO — 包含场景列表和教学动作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomDetailDto {
    private String id;
    private String courseId;
    private String knowledgePointId;
    private String title;
    private String description;
    private Integer difficulty;
    private Integer totalDurationSeconds;
    private String status;
    private Integer sceneCount;
    private Integer version;
    private List<SceneDetailDto> scenes;
    private Map<String, Object> metadata;
    private String createdAt;
    private String updatedAt;
}
