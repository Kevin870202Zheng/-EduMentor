package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * AI 规划多轮对话请求 DTO。
 * <p>
 * generatePath=true 时，LLM 输出结构化路径 JSON 并落库为 DRAFT 路径。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class AiPlanChatRequest {

    @NotNull(message = "学生 ID 不能为空")
    private UUID studentId;

    @NotBlank(message = "会话 ID 不能为空")
    private String sessionId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** true=让 LLM 生成结构化路径并落库 */
    private Boolean generatePath = false;

    /** 可选：候选池课程过滤 */
    private UUID courseId;
}
