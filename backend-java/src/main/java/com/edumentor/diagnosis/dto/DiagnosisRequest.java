package com.edumentor.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.util.UUID;

/**
 * 诊断分析请求 DTO。
 *
 * @param courseId  课程 ID（可选，指定则只分析该课程范围内的知识点）
 * @param daysBack  回溯天数（默认 30 天，最大 365 天）
 */
@Schema(description = "诊断分析请求")
public record DiagnosisRequest(

        @Schema(description = "课程 ID（可选，限定分析范围）", example = "e5f6g7h8-...")
        UUID courseId,

        @Min(value = 1, message = "回溯天数至少为 1")
        @Schema(description = "回溯天数（默认 30，最大 365）", example = "30")
        int daysBack
) {

    /** 默认回溯天数 */
    public static final int DEFAULT_DAYS_BACK = 30;

    /** 最大回溯天数 */
    public static final int MAX_DAYS_BACK = 365;

    public DiagnosisRequest {
        if (daysBack <= 0) {
            daysBack = DEFAULT_DAYS_BACK;
        }
        if (daysBack > MAX_DAYS_BACK) {
            daysBack = MAX_DAYS_BACK;
        }
    }
}
