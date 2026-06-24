package com.edumentor.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 雷达图数据 DTO — 用于前端展示学生多维能力分布。
 *
 * <p>包含学生基本信息、各维度评分以及综合评分。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "雷达图数据")
public class RadarChartData {

    @Schema(description = "学生 ID")
    private UUID studentId;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "各维度数据")
    private List<Dimension> dimensions;

    @Schema(description = "综合评分 (0 ~ 100)", example = "68.5")
    private BigDecimal overallScore;

    /**
     * 雷达图单一维度。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "雷达图维度")
    public static class Dimension {

        @Schema(description = "维度名称", example = "基础知识")
        private String name;

        @Schema(description = "维度值 (0 ~ 100)", example = "75.0")
        private BigDecimal value;

        @Schema(description = "满分值", example = "100")
        private BigDecimal maxValue;

        @Schema(description = "维度颜色（十六进制）", example = "#1890FF")
        private String color;
    }
}
