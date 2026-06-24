package com.edumentor.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 文档 DTO。
 *
 * @author EduMentor Team
 */
@Data
public class RAGDocumentDto {

    private String id;
    private String title;
    private String content;
    private Integer contentLength;
    private String source;
    private String courseId;
    private LocalDateTime createdAt;

    public void setContent(String content) {
        this.content = content;
        this.contentLength = content != null ? content.length() : 0;
    }
}
