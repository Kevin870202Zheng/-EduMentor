package com.edumentor.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 认知画像 DTO — 展示学生在各个知识维度的掌握情况。
 *
 * <p>包含整体掌握度、知识点统计、各知识点掌握详情以及雷达图数据。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "认知画像")
public class CognitiveProfile {

    @Schema(description = "整体掌握度 (0.00 ~ 1.00)", example = "0.62")
    private BigDecimal overallMasteryLevel;

    @Schema(description = "总知识点数", example = "30")
    private int totalKpCount;

    @Schema(description = "已掌握知识点数", example = "12")
    private int masteredKpCount;

    @Schema(description = "学习中知识点数", example = "13")
    private int learningKpCount;

    @Schema(description = "薄弱知识点数", example = "5")
    private int weakKpCount;

    @Schema(description = "各知识点掌握详情")
    private List<KnowledgeMasteryDTO> knowledgeMasteries;

    @Schema(description = "雷达图数据（6大维度）")
    private List<RadarDimension> radarChartData;

    @Schema(description = "画像总结")
    private String summary;

    /**
     * 雷达图维度数据点。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "雷达图维度")
    public static class RadarDimension {

        @Schema(description = "维度名称", example = "基础知识")
        private String dimension;

        @Schema(description = "掌握度 (0 ~ 100)", example = "75.0")
        private BigDecimal value;

        @Schema(description = "满分值", example = "100")
        private BigDecimal maxValue;
    }
}
