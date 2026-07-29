package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 课后练习生成请求 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeGenerateRequest {
    private String classroomId;
    private Integer questionCount;
    private List<String> focusKnowledgePointIds;
    private Boolean includeReviewLinks;
}
