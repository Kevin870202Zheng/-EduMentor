package com.edumentor.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 学习热力图 DTO — 展示学生在时间轴上的学习强度分布。
 *
 * <p>按天统计学习活动，包含答题量、正确率、学习时长和专注度等维度。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "学习热力图数据")
public class HeatMapData {

    @Schema(description = "学生 ID")
    private UUID studentId;

    @Schema(description = "统计起始日期")
    private LocalDate startDate;

    @Schema(description = "统计结束日期")
    private LocalDate endDate;

    @Schema(description = "统计总天数")
    private int totalDays;

    @Schema(description = "活跃天数（有学习活动的天数）")
    private int activeDays;

    @Schema(description = "热力图数据点列表")
    private List<HeatMapDay> heatData;

    /**
     * 单日热力图数据点。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "单日热力图数据")
    public static class HeatMapDay {

        @Schema(description = "日期", example = "2026-06-15")
        private LocalDate date;

        @Schema(description = "学习强度（答题数）", example = "12")
        private int questionCount;

        @Schema(description = "正确数", example = "8")
        private int correctCount;

        @Schema(description = "正确率 (0.00 ~ 1.00)", example = "0.67")
        private BigDecimal accuracyRate;

        @Schema(description = "学习时长（分钟）", example = "45")
        private int durationMinutes;

        @Schema(description = "专注度评分 (0-100)", example = "72.5")
        private BigDecimal focusScore;

        @Schema(description = "覆盖知识点数", example = "3")
        private int kpCovered;
    }
}
