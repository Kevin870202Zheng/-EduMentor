package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 课堂进度 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomProgressDto {
    private String id;
    private String studentId;
    private String classroomId;
    private String status;
    private String currentSceneId;
    private Integer currentActionOrder;
    private Integer scenesCompleted;
    private Integer totalScenes;
    private Integer quizCorrectCount;
    private Integer quizTotalCount;
    private Integer totalWatchSeconds;
    private String startedAt;
    private String completedAt;
    private String lastAccessedAt;
}
