package com.edumentor.dashboard.dto;

import java.time.LocalDate;

/**
 * 每日简报 DTO — 教师驾驶舱中的今日教学/学习摘要。
 * <p>
 * 每天自动生成，包含今日关键指标变化、突出问题和亮点。
 * </p>
 *
 * @author EduMentor Team
 */
public class DailyBriefDto {

    /** 简报日期 */
    private LocalDate date;

    /** 简报标题（如 "6月23日 教学简报"） */
    private String title;

    /** 今日活跃学生数 */
    private int activeStudents;

    /** 全校/班总学生数 */
    private int totalStudents;

    /** 今日新增答题数 */
    private long newAnswers;

    /** 今日平均正确率（百分比 0-100） */
    private double todayCorrectRate;

    /** 较昨日正确率变化（百分点） */
    private double correctRateChange;

    /** 今日新增学习会话数 */
    private int newSessions;

    /** 今日学习总时长（分钟） */
    private int totalStudyMinutes;

    /** 人均学习时长（分钟） */
    private double averageStudyMinutes;

    /** 今日新增预警数 */
    private int newAlerts;

    /** 今日已处理预警数 */
    private int resolvedAlerts;

    /** 今日新增错题数 */
    private int newErrors;

    /** 今日已复习题数 */
    private int reviewedCount;

    /** 进步最快学生姓名 */
    private String mostImprovedStudent;

    /** 进步最快学生进步率（正确率提升百分点） */
    private double mostImprovedRate;

    /** 需要关注学生数（预警 >= 2 或有未处理高危预警） */
    private int studentsNeedingAttention;

    /** 待办事项摘要文本 */
    private String summary;

    public DailyBriefDto() {
    }

    // ──── Builder-style setters ────

    public DailyBriefDto date(LocalDate date) {
        this.date = date;
        return this;
    }

    public DailyBriefDto title(String title) {
        this.title = title;
        return this;
    }

    public DailyBriefDto activeStudents(int activeStudents) {
        this.activeStudents = activeStudents;
        return this;
    }

    public DailyBriefDto totalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
        return this;
    }

    public DailyBriefDto newAnswers(long newAnswers) {
        this.newAnswers = newAnswers;
        return this;
    }

    public DailyBriefDto todayCorrectRate(double todayCorrectRate) {
        this.todayCorrectRate = todayCorrectRate;
        return this;
    }

    public DailyBriefDto correctRateChange(double correctRateChange) {
        this.correctRateChange = correctRateChange;
        return this;
    }

    public DailyBriefDto newSessions(int newSessions) {
        this.newSessions = newSessions;
        return this;
    }

    public DailyBriefDto totalStudyMinutes(int totalStudyMinutes) {
        this.totalStudyMinutes = totalStudyMinutes;
        return this;
    }

    public DailyBriefDto averageStudyMinutes(double averageStudyMinutes) {
        this.averageStudyMinutes = averageStudyMinutes;
        return this;
    }

    public DailyBriefDto newAlerts(int newAlerts) {
        this.newAlerts = newAlerts;
        return this;
    }

    public DailyBriefDto resolvedAlerts(int resolvedAlerts) {
        this.resolvedAlerts = resolvedAlerts;
        return this;
    }

    public DailyBriefDto newErrors(int newErrors) {
        this.newErrors = newErrors;
        return this;
    }

    public DailyBriefDto reviewedCount(int reviewedCount) {
        this.reviewedCount = reviewedCount;
        return this;
    }

    public DailyBriefDto mostImprovedStudent(String mostImprovedStudent) {
        this.mostImprovedStudent = mostImprovedStudent;
        return this;
    }

    public DailyBriefDto mostImprovedRate(double mostImprovedRate) {
        this.mostImprovedRate = mostImprovedRate;
        return this;
    }

    public DailyBriefDto studentsNeedingAttention(int studentsNeedingAttention) {
        this.studentsNeedingAttention = studentsNeedingAttention;
        return this;
    }

    public DailyBriefDto summary(String summary) {
        this.summary = summary;
        return this;
    }

    // ──── Getters / Setters ────

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getActiveStudents() {
        return activeStudents;
    }

    public void setActiveStudents(int activeStudents) {
        this.activeStudents = activeStudents;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
    }

    public long getNewAnswers() {
        return newAnswers;
    }

    public void setNewAnswers(long newAnswers) {
        this.newAnswers = newAnswers;
    }

    public double getTodayCorrectRate() {
        return todayCorrectRate;
    }

    public void setTodayCorrectRate(double todayCorrectRate) {
        this.todayCorrectRate = todayCorrectRate;
    }

    public double getCorrectRateChange() {
        return correctRateChange;
    }

    public void setCorrectRateChange(double correctRateChange) {
        this.correctRateChange = correctRateChange;
    }

    public int getNewSessions() {
        return newSessions;
    }

    public void setNewSessions(int newSessions) {
        this.newSessions = newSessions;
    }

    public int getTotalStudyMinutes() {
        return totalStudyMinutes;
    }

    public void setTotalStudyMinutes(int totalStudyMinutes) {
        this.totalStudyMinutes = totalStudyMinutes;
    }

    public double getAverageStudyMinutes() {
        return averageStudyMinutes;
    }

    public void setAverageStudyMinutes(double averageStudyMinutes) {
        this.averageStudyMinutes = averageStudyMinutes;
    }

    public int getNewAlerts() {
        return newAlerts;
    }

    public void setNewAlerts(int newAlerts) {
        this.newAlerts = newAlerts;
    }

    public int getResolvedAlerts() {
        return resolvedAlerts;
    }

    public void setResolvedAlerts(int resolvedAlerts) {
        this.resolvedAlerts = resolvedAlerts;
    }

    public int getNewErrors() {
        return newErrors;
    }

    public void setNewErrors(int newErrors) {
        this.newErrors = newErrors;
    }

    public int getReviewedCount() {
        return reviewedCount;
    }

    public void setReviewedCount(int reviewedCount) {
        this.reviewedCount = reviewedCount;
    }

    public String getMostImprovedStudent() {
        return mostImprovedStudent;
    }

    public void setMostImprovedStudent(String mostImprovedStudent) {
        this.mostImprovedStudent = mostImprovedStudent;
    }

    public double getMostImprovedRate() {
        return mostImprovedRate;
    }

    public void setMostImprovedRate(double mostImprovedRate) {
        this.mostImprovedRate = mostImprovedRate;
    }

    public int getStudentsNeedingAttention() {
        return studentsNeedingAttention;
    }

    public void setStudentsNeedingAttention(int studentsNeedingAttention) {
        this.studentsNeedingAttention = studentsNeedingAttention;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
