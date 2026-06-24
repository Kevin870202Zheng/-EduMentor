package com.edumentor.review.dto;

import com.edumentor.entity.enums.ReviewType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 创建复习记录请求 DTO — 手动创建一条复习记录。
 *
 * @author EduMentor Team
 */
@Data
public class ReviewCreateRequest {

    @NotNull(message = "学生 ID 不能为空")
    private UUID studentId;

    @NotNull(message = "知识点 ID 不能为空")
    private UUID knowledgePointId;

    private String knowledgePointName;

    private UUID errorRecordId;

    private ReviewType reviewType = ReviewType.CUSTOM_REVIEW;

    private Integer reviewCycle;

    @Min(value = 1, message = "复习周期至少为 1")
    private Integer daysUntilReview = 1;
}
