package com.edumentor.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 知识库检索请求 DTO。
 *
 * @author EduMentor Team
 */
@Data
public class SearchRequest {

    @NotBlank(message = "检索关键词不能为空")
    private String query;

    /** 返回结果数量（默认 10） */
    private Integer topK = 10;

    /** 限定课程 ID（可选） */
    private String courseId;

    /** 相似度阈值（0.0~1.0，默认 0.0） */
    private Double scoreThreshold = 0.0;
}
