package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Quiz 提交请求 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitRequest {
    private String sceneId;
    private Map<String, Object> studentAnswer;
    private Integer selectedIndex;
    private Integer timeSpentSeconds;
}
