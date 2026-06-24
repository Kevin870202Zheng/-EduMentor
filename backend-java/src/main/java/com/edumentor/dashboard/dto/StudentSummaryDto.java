package com.edumentor.dashboard.dto;

import java.util.List;

/**
 * 学生概要 DTO — 教师驾驶舱学生列表中的单条记录。
 * <p>
 * 每个学生一条，包含基本概况、学习表现、薄弱环节等核心指标。
 * </p>
 *
 * @author EduMentor Team
 */
public class StudentSummaryDto {

    /** 学生 ID */
    private String studentId;

    /** 学生姓名 */
    private String displayName;

    /** 用户名 */
    private String username;

    /** 头像 URL */
    private String avatarUrl;

    /** 班级/年级 */
    private String grade;

    /** 总答题数 */
    private long totalAnswers;

    /** 总正确数 */
    private long correctAnswers;

    /** 正确率（百分比 0-100） */
    private double correctRate;

    /** 今日答题数 */
    private long answersToday;

    /** 今日正确数 */
    private long correctToday;

    /** 今日学习时长（分钟） */
    private int studyMinutesToday;

    /** 连续学习天数 */
    private int streakDays;

    /** 薄弱知识点列表（正确率低于 60% 的知识点名称） */
    private List<String> weakAreas;

    /** 待处理预警数量 */
    private int pendingAlertCount;

    /** 学习进度百分比 (0-100) */
    private double learningProgress;

    /** 最近活跃时间（ISO-8601 字符串） */
    private String lastActiveAt;

    /** 学生状态: active / inactive / at-risk */
    private String status;

    public StudentSummaryDto() {
    }

    // ──── Builder-style setters ────

    public StudentSummaryDto studentId(String studentId) {
        this.studentId = studentId;
        return this;
    }

    public StudentSummaryDto displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public StudentSummaryDto username(String username) {
        this.username = username;
        return this;
    }

    public StudentSummaryDto avatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        return this;
    }

    public StudentSummaryDto grade(String grade) {
        this.grade = grade;
        return this;
    }

    public StudentSummaryDto totalAnswers(long totalAnswers) {
        this.totalAnswers = totalAnswers;
        return this;
    }

    public StudentSummaryDto correctAnswers(long correctAnswers) {
        this.correctAnswers = correctAnswers;
        return this;
    }

    public StudentSummaryDto correctRate(double correctRate) {
        this.correctRate = correctRate;
        return this;
    }

    public StudentSummaryDto answersToday(long answersToday) {
        this.answersToday = answersToday;
        return this;
    }

    public StudentSummaryDto correctToday(long correctToday) {
        this.correctToday = correctToday;
        return this;
    }

    public StudentSummaryDto studyMinutesToday(int studyMinutesToday) {
        this.studyMinutesToday = studyMinutesToday;
        return this;
    }

    public StudentSummaryDto streakDays(int streakDays) {
        this.streakDays = streakDays;
        return this;
    }

    public StudentSummaryDto weakAreas(List<String> weakAreas) {
        this.weakAreas = weakAreas;
        return this;
    }

    public StudentSummaryDto pendingAlertCount(int pendingAlertCount) {
        this.pendingAlertCount = pendingAlertCount;
        return this;
    }

    public StudentSummaryDto learningProgress(double learningProgress) {
        this.learningProgress = learningProgress;
        return this;
    }

    public StudentSummaryDto lastActiveAt(String lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
        return this;
    }

    public StudentSummaryDto status(String status) {
        this.status = status;
        return this;
    }

    // ──── Getters / Setters ────

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public long getTotalAnswers() {
        return totalAnswers;
    }

    public void setTotalAnswers(long totalAnswers) {
        this.totalAnswers = totalAnswers;
    }

    public long getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(long correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    public double getCorrectRate() {
        return correctRate;
    }

    public void setCorrectRate(double correctRate) {
        this.correctRate = correctRate;
    }

    public long getAnswersToday() {
        return answersToday;
    }

    public void setAnswersToday(long answersToday) {
        this.answersToday = answersToday;
    }

    public long getCorrectToday() {
        return correctToday;
    }

    public void setCorrectToday(long correctToday) {
        this.correctToday = correctToday;
    }

    public int getStudyMinutesToday() {
        return studyMinutesToday;
    }

    public void setStudyMinutesToday(int studyMinutesToday) {
        this.studyMinutesToday = studyMinutesToday;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = streakDays;
    }

    public List<String> getWeakAreas() {
        return weakAreas;
    }

    public void setWeakAreas(List<String> weakAreas) {
        this.weakAreas = weakAreas;
    }

    public int getPendingAlertCount() {
        return pendingAlertCount;
    }

    public void setPendingAlertCount(int pendingAlertCount) {
        this.pendingAlertCount = pendingAlertCount;
    }

    public double getLearningProgress() {
        return learningProgress;
    }

    public void setLearningProgress(double learningProgress) {
        this.learningProgress = learningProgress;
    }

    public String getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(String lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
