package com.edumentor.review.dto;

import com.edumentor.entity.enums.ErrorType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 错题分析 DTO — 返回学生错题分析的详细结果。
 * <p>
 * 包含薄弱知识点分析、正确率趋势、错因类型分布等维度。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class ErrorAnalysisDto {

    /** 学生 ID */
    private UUID studentId;

    /** 错题总数 */
    private long totalErrors;

    /** 未复习错题数 */
    private long unreviewedCount;

    /** 薄弱知识点列表（按错题频次降序） */
    private List<WeakKnowledgePoint> weakKnowledgePoints;

    /** 按错因类型的分布 */
    private List<ErrorTypeDistribution> errorTypeDistribution;

    /** 正确率趋势（按日期） */
    private List<AccuracyTrend> accuracyTrend;

    /** 分析生成时间戳 */
    private LocalDateTime analysisTimestamp;

    /**
     * 薄弱知识点概要。
     */
    @Data
    public static class WeakKnowledgePoint {

        private UUID knowledgePointId;
        private String knowledgePointName;
        private long errorCount;
        private long unreviewedCount;
        private double errorRate;
    }

    /**
     * 正确率趋势数据点。
     */
    @Data
    public static class AccuracyTrend {

        private LocalDate date;
        private double accuracy;
        private long totalCount;
        private long correctCount;
    }

    /**
     * 错因类型分布。
     */
    @Data
    public static class ErrorTypeDistribution {

        private ErrorType errorType;
        private long count;
        private double percentage;
    }
}
