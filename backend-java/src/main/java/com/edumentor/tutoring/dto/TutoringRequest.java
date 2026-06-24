package com.edumentor.tutoring.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 智能答疑请求 DTO。
 *
 * @author EduMentor Team
 */
@Data
public class TutoringRequest {

    /** 用户问题 */
    @NotBlank(message = "问题内容不能为空")
    private String question;

    /** 会话 ID（为空时创建新会话） */
    private String sessionId;

    /** 相关知识点 ID */
    private String knowledgePointId;

    /** 额外上下文 */
    private Map<String, Object> context;

    /** 是否启用 RAG 增强（默认 true） */
    private Boolean useRag = true;
}
