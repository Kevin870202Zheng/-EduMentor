package com.edumentor.course.dto;

import lombok.Data;

import java.util.UUID;

/**
 * 更新知识点请求 DTO。
 * <p>
 * 所有字段均为可选，只更新不为 null 的字段。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Data
public class KnowledgePointUpdateRequest {

    private String name;
    private String description;
    private String content;
    private Integer difficulty;
    private Integer importance;
    private String subject;
    private String tags;
    private Integer orderIndex;
    private UUID parentKpId;
    private String type;
}
