package com.edumentor.classroom.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 勾选生成课堂请求（场景一，设计文档 §4.4）。
 * mode: aggregated（聚合为一个课堂，默认）| batch（每知识点一课，异步）
 */
@Data
public class GenerateFromSelectionRequest {

    private UUID courseId;

    /** 勾选的知识点/章节 ID 列表（可混合，章节自动展开为叶子） */
    private List<UUID> knowledgePointIds;

    private String mode = "aggregated";

    /** 课堂标题（可空，自动生成） */
    private String title;

    private Integer difficulty = 3;

    /** 课程名称（可空，后端自动查询） */
    private String courseName;
}
