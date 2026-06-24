package com.edumentor.review.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 复习排程请求 DTO — 基于艾宾浩斯遗忘曲线生成复习计划。
 * <p>
 * 系统会根据学生的错题记录和学习历史，自动生成最优的复习排程。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class ReviewPlanRequest {

    @NotNull(message = "学生 ID 不能为空")
    private UUID studentId;

    /** 可选：指定课程的 ID（不指定则为全部错题生成计划） */
    private UUID courseId;

    /** 复习计划名称 */
    @NotBlank(message = "计划名称不能为空")
    private String planName;

    /** 每天最大复习知识点数 */
    @Min(value = 1, message = "每天至少复习 1 个知识点")
    private int maxDailyReviews = 5;

    /** 是否包含已复习过的错题（用于再次巩固） */
    private boolean includeReviewed = false;

    /** 是否优先排程高频错题 */
    private boolean prioritizeHighFrequency = true;
}
