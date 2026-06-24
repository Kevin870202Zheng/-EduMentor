package com.edumentor.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 复习进度更新请求 DTO — 标记复习完成或跳过。
 *
 * @author EduMentor Team
 */
@Data
public class ReviewProgressRequest {

    @NotNull(message = "复习记录 ID 不能为空")
    private UUID reviewId;

    /** 新状态：COMPLETED / SKIPPED */
    @NotNull(message = "状态不能为空")
    private String status;

    /** 复习耗时（分钟） */
    @Min(value = 0, message = "耗时不能为负")
    private Integer spentMinutes;

    /** 复习成效自评（1-5 分） */
    @Min(value = 1, message = "评分最低为 1")
    @Max(value = 5, message = "评分最高为 5")
    private Integer effectivenessScore;

    /** 复习笔记 */
    private String notes;

    /** 本次复习正确率（0.0-100.0） */
    @Min(value = 0, message = "正确率不能小于 0")
    @Max(value = 100, message = "正确率不能大于 100")
    private Double accuracy;
}
