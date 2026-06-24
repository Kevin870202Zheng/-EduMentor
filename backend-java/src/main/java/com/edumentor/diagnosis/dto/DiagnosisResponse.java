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
 * 诊断分析结果 DTO。
 *
 * <p>包含学生整体学情统计、薄弱/优势知识点识别、近期趋势以及诊断建议。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "诊断分析结果")
public class DiagnosisResponse {

    @Schema(description = "学生 ID")
    private UUID studentId;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "总答题数", example = "156")
    private long totalQuestions;

    @Schema(description = "正确数", example = "98")
    private long correctCount;

    @Schema(description = "正确率 (0.00 ~ 1.00)", example = "0.63")
    private BigDecimal accuracyRate;

    @Schema(description = "总用时（秒）", example = "12580")
    private long totalTimeSpentSec;

    @Schema(description = "平均每题用时（秒）", example = "80.6")
    private BigDecimal avgTimePerQuestion;

    @Schema(description = "知识点覆盖率 (0.00 ~ 1.00)", example = "0.45")
    private BigDecimal knowledgeCoverage;

    @Schema(description = "薄弱知识点数量", example = "5")
    private int weakKpCount;

    @Schema(description = "优势知识点数量", example = "3")
    private int strongKpCount;

    @Schema(description = "最薄弱 TOP N 知识点")
    private List<KnowledgeMasteryDTO> topWeakKps;

    @Schema(description = "最优势 TOP N 知识点")
    private List<KnowledgeMasteryDTO> topStrongKps;

    @Schema(description = "近期正确率趋势（近7天每天的正确率）")
    private List<DailyAccuracy> recentTrend;

    @Schema(description = "诊断总结文本")
    private String diagnosisSummary;

    @Schema(description = "学习建议列表")
    private List<String> recommendations;

    @Schema(description = "分析日期")
    private LocalDate analysisDate;

    /**
     * 每日正确率趋势数据点。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "每日正确率")
    public static class DailyAccuracy {

        @Schema(description = "日期")
        private LocalDate date;

        @Schema(description = "正确率 (0.00 ~ 1.00)")
        private BigDecimal accuracy;

        @Schema(description = "答题数")
        private int count;
    }
}
