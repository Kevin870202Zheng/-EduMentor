package com.edumentor.review.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 复习重新排程请求 DTO — 修改复习的计划日期。
 *
 * @author EduMentor Team
 */
@Data
public class ReviewRescheduleRequest {

    @NotNull(message = "复习记录 ID 不能为空")
    private UUID reviewId;

    /** 延迟天数（正数推迟，负数提前） */
    @NotNull(message = "延迟天数不能为空")
    private Integer daysOffset;

    /** 调整原因 */
    private String reason;
}
