package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 场景详情 DTO — 包含该场景的所有教学动作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneDetailDto {
    private String id;
    private String classroomId;
    private String title;
    private String description;
    private String sceneType;
    private Integer orderIndex;
    private Integer estimatedDurationSeconds;
    private List<ActionDTO> actions;
    private Map<String, Object> content;
    private String createdAt;
}
