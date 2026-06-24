package com.edumentor.review.dto;

import com.edumentor.entity.enums.ReviewType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 复习计划 DTO — 返回学生基于艾宾浩斯遗忘曲线生成的复习计划。
 * <p>
 * 包含今日待复习任务、未来排程概览、已完成复习统计等信息。
 * </p>
 *
 * @param studentId         学生 ID
 * @param todayPendingCount 今日待复习数量
 * @param todayTasks        今日待复习任务列表
 * @param upcomingSchedule  未来排程概览
 * @param completionStats   完成情况统计
 * @param planGeneratedDate 计划生成日期
 *
 * @author EduMentor Team
 */
@Schema(description = "复习计划")
public record ReviewPlanDto(

        @Schema(description = "学生 ID", example = "a1b2c3d4-...")
        UUID studentId,

        @Schema(description = "今日待复习数量", example = "5")
        long todayPendingCount,

        @Schema(description = "今日待复习任务列表")
        List<ReviewTask> todayTasks,

        @Schema(description = "未来 7 天排程概览")
        List<DailySchedule> upcomingSchedule,

        @Schema(description = "完成情况统计")
        CompletionStats completionStats,

        @Schema(description = "计划生成日期")
        LocalDate planGeneratedDate
) {

    /**
     * 单条复习任务。
     *
     * @param id               复习记录 ID
     * @param knowledgePointId 知识点 ID
     * @param knowledgePointName 知识点名称
     * @param reviewType       复习类型
     * @param reviewCycle      当前复习周期
     * @param scheduledDate    计划日期
     * @param isCompleted      是否已完成
     * @param masteryLevel     掌握度
     */
    @Schema(description = "复习任务")
    public record ReviewTask(
            @Schema(description = "复习记录 ID") UUID id,
            @Schema(description = "知识点 ID") UUID knowledgePointId,
            @Schema(description = "知识点名称") String knowledgePointName,
            @Schema(description = "复习类型") ReviewType reviewType,
            @Schema(description = "复习周期") int reviewCycle,
            @Schema(description = "计划日期") LocalDate scheduledDate,
            @Schema(description = "是否已完成") boolean isCompleted,
            @Schema(description = "掌握度（0-1）") BigDecimal masteryLevel
    ) {}

    /**
     * 每日排程概览。
     *
     * @param date       日期
     * @param taskCount  任务数
     * @param completedCount 已完成数
     */
    @Schema(description = "每日排程概览")
    public record DailySchedule(
            @Schema(description = "日期") LocalDate date,
            @Schema(description = "任务数") long taskCount,
            @Schema(description = "已完成数") long completedCount
    ) {}

    /**
     * 完成情况统计。
     *
     * @param totalScheduled  总排程数
     * @param totalCompleted  总完成数
     * @param completionRate  完成率（百分比）
     * @param avgMasteryLevel 平均掌握度
     */
    @Schema(description = "完成情况统计")
    public record CompletionStats(
            @Schema(description = "总排程数") long totalScheduled,
            @Schema(description = "总完成数") long totalCompleted,
            @Schema(description = "完成率（百分比）") double completionRate,
            @Schema(description = "平均掌握度") BigDecimal avgMasteryLevel
    ) {}
}
