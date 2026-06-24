package com.edumentor.alert.dto;

import com.edumentor.entity.enums.AlertSeverity;
import com.edumentor.entity.enums.AlertType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警统计 DTO — 用于 Dashboard 展示预警系统的聚合统计数据。
 * <p>
 * 包含按级别、按类型的分类统计，以及趋势数据和优先级排序。
 * </p>
 *
 * @author EduMentor Team
 */
public class AlertStatisticsDto {

    /** 未处理预警总数 */
    private long totalUnresolved;

    /** 今日新增预警数 */
    private long todayNewCount;

    /** 本周新增预警数 */
    private long weekNewCount;

    /** 按严重级别统计未处理预警 */
    private Map<AlertSeverity, Long> bySeverity = new HashMap<>();

    /** 按预警类型统计未处理预警 */
    private Map<AlertType, Long> byType = new HashMap<>();

    /** 待处理的高级别预警列表（HIGH + CRITICAL） */
    private List<AlertDto> urgentAlerts;

    /** 预警处理率（已处理 / 总数 * 100） */
    private double resolutionRate;

    /** 最近 7 天的预警趋势（每天的数量） */
    private List<DailyAlertCount> dailyTrend;

    // ─── Getter / Setter ───

    public long getTotalUnresolved() {
        return totalUnresolved;
    }

    public void setTotalUnresolved(long totalUnresolved) {
        this.totalUnresolved = totalUnresolved;
    }

    public long getTodayNewCount() {
        return todayNewCount;
    }

    public void setTodayNewCount(long todayNewCount) {
        this.todayNewCount = todayNewCount;
    }

    public long getWeekNewCount() {
        return weekNewCount;
    }

    public void setWeekNewCount(long weekNewCount) {
        this.weekNewCount = weekNewCount;
    }

    public Map<AlertSeverity, Long> getBySeverity() {
        return bySeverity;
    }

    public void setBySeverity(Map<AlertSeverity, Long> bySeverity) {
        this.bySeverity = bySeverity;
    }

    public Map<AlertType, Long> getByType() {
        return byType;
    }

    public void setByType(Map<AlertType, Long> byType) {
        this.byType = byType;
    }

    public List<AlertDto> getUrgentAlerts() {
        return urgentAlerts;
    }

    public void setUrgentAlerts(List<AlertDto> urgentAlerts) {
        this.urgentAlerts = urgentAlerts;
    }

    public double getResolutionRate() {
        return resolutionRate;
    }

    public void setResolutionRate(double resolutionRate) {
        this.resolutionRate = resolutionRate;
    }

    public List<DailyAlertCount> getDailyTrend() {
        return dailyTrend;
    }

    public void setDailyTrend(List<DailyAlertCount> dailyTrend) {
        this.dailyTrend = dailyTrend;
    }

    /**
     * 每日预警数量（用于趋势图）。
     */
    public static class DailyAlertCount {
        private String date;
        private long count;
        private long unresolvedCount;

        public DailyAlertCount(String date, long count, long unresolvedCount) {
            this.date = date;
            this.count = count;
            this.unresolvedCount = unresolvedCount;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public long getUnresolvedCount() {
            return unresolvedCount;
        }

        public void setUnresolvedCount(long unresolvedCount) {
            this.unresolvedCount = unresolvedCount;
        }
    }
}
