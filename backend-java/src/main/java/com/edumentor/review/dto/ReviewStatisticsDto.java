package com.edumentor.review.dto;

import com.edumentor.entity.enums.ErrorType;
import com.edumentor.review.entity.enums.ReviewStatus;
import com.edumentor.entity.enums.ReviewType;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题复盘统计 DTO — 用于 Dashboard 展示学生的错题与复习聚合数据。
 * <p>
 * 包含错题分布、错因分析、复习进度、艾宾浩斯排程状态等维度。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class ReviewStatisticsDto {

    // ══════════════════════════════════════════════════════════════
    //  错题统计
    // ══════════════════════════════════════════════════════════════

    /** 错题总数 */
    private long totalErrors;

    /** 未复习错题数 */
    private long unreviewedErrors;

    /** 已复习错题数 */
    private long reviewedErrors;

    /** 复习率（已复习 / 总数 * 100） */
    private double reviewRate;

    /** 按知识点分布的错题数 */
    private List<KpErrorCount> errorByKnowledgePoint = new ArrayList<>();

    /** 按错因分类的分布 */
    private Map<ErrorType, Long> errorByType = new HashMap<>();

    /** 高频错题知识点 TOP 5 */
    private List<KpErrorCount> topErrorKnowledgePoints = new ArrayList<>();

    // ══════════════════════════════════════════════════════════════
    //  复习统计
    // ══════════════════════════════════════════════════════════════

    /** 复习记录总数 */
    private long totalReviews;

    /** 已完成复习数 */
    private long completedReviews;

    /** 待复习数 */
    private long pendingReviews;

    /** 逾期复习数 */
    private long overdueReviews;

    /** 完成率（已完成 / 总数 * 100） */
    private double completionRate;

    /** 按复习类型统计 */
    private Map<ReviewType, Long> reviewByType = new HashMap<>();

    /** 按状态统计 */
    private Map<ReviewStatus, Long> reviewByStatus = new HashMap<>();

    /** 平均复习成效评分 */
    private double averageEffectivenessScore;

    // ══════════════════════════════════════════════════════════════
    //  艾宾浩斯排程
    // ══════════════════════════════════════════════════════════════

    /** 已完成的最大复习周期 */
    private Integer maxCompletedCycle;

    /** 各周期的完成统计 */
    private Map<Integer, Long> reviewByCycle = new HashMap<>();

    /** 今日待复习数 */
    private long todayReviews;

    /** 本周待复习数 */
    private long weekReviews;

    /** 最近 7 天每日完成数 */
    private List<DailyReviewCount> dailyTrend = new ArrayList<>();

    // ══════════════════════════════════════════════════════════════
    //  内部嵌套类
    // ══════════════════════════════════════════════════════════════

    /**
     * 知识点错题统计。
     */
    @Data
    public static class KpErrorCount {

        private String knowledgePointId;
        private String knowledgePointName;
        private long count;
    }

    /**
     * 每日复习完成统计。
     */
    @Data
    public static class DailyReviewCount {

        private String date;
        private long completedCount;
        private long totalCount;
    }
}
