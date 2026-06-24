package com.edumentor.dashboard.dto;

import java.util.List;

/**
 * 班级学情总览 DTO — 教师驾驶舱核心聚合数据。
 * <p>
 * 包含整个班级的宏观统计指标，如总人数、平均正确率、今日活跃度、待处理预警等。
 * </p>
 *
 * @author EduMentor Team
 */
public class ClassOverviewDto {

    /** 班级 / 课程名称 */
    private String className;

    /** 关联课程 ID */
    private String courseId;

    /** 学生总数 */
    private int totalStudents;

    /** 今日活跃学生数（有过学习会话或作答记录） */
    private int activeStudentsToday;

    /** 活跃率百分比 */
    private double activeRate;

    /** 全班平均正确率（百分比 0-100） */
    private double averageCorrectRate;

    /** 平均正确率较昨日变化（百分点） */
    private double correctRateChange;

    /** 本周累计答题总数 */
    private long totalAnswersThisWeek;

    /** 今日新增答题数 */
    private long answersToday;

    /** 累计作答正确数 */
    private long totalCorrectAnswers;

    /** 待处理预警数量 */
    private int pendingAlertCount;

    /** 高危预警（HIGH + CRITICAL）数量 */
    private int criticalAlertCount;

    /** 薄弱知识点数量（正确率 &lt; 60% 的知识点） */
    private int weakKnowledgeCount;

    /** 各知识点掌握度概况 */
    private List<KnowledgeMasterySummary> knowledgeMastery;

    public ClassOverviewDto() {
    }

    // ──── Builder-style setters ────

    public ClassOverviewDto className(String className) {
        this.className = className;
        return this;
    }

    public ClassOverviewDto courseId(String courseId) {
        this.courseId = courseId;
        return this;
    }

    public ClassOverviewDto totalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
        return this;
    }

    public ClassOverviewDto activeStudentsToday(int activeStudentsToday) {
        this.activeStudentsToday = activeStudentsToday;
        return this;
    }

    public ClassOverviewDto activeRate(double activeRate) {
        this.activeRate = activeRate;
        return this;
    }

    public ClassOverviewDto averageCorrectRate(double averageCorrectRate) {
        this.averageCorrectRate = averageCorrectRate;
        return this;
    }

    public ClassOverviewDto correctRateChange(double correctRateChange) {
        this.correctRateChange = correctRateChange;
        return this;
    }

    public ClassOverviewDto totalAnswersThisWeek(long totalAnswersThisWeek) {
        this.totalAnswersThisWeek = totalAnswersThisWeek;
        return this;
    }

    public ClassOverviewDto answersToday(long answersToday) {
        this.answersToday = answersToday;
        return this;
    }

    public ClassOverviewDto totalCorrectAnswers(long totalCorrectAnswers) {
        this.totalCorrectAnswers = totalCorrectAnswers;
        return this;
    }

    public ClassOverviewDto pendingAlertCount(int pendingAlertCount) {
        this.pendingAlertCount = pendingAlertCount;
        return this;
    }

    public ClassOverviewDto criticalAlertCount(int criticalAlertCount) {
        this.criticalAlertCount = criticalAlertCount;
        return this;
    }

    public ClassOverviewDto weakKnowledgeCount(int weakKnowledgeCount) {
        this.weakKnowledgeCount = weakKnowledgeCount;
        return this;
    }

    public ClassOverviewDto knowledgeMastery(List<KnowledgeMasterySummary> knowledgeMastery) {
        this.knowledgeMastery = knowledgeMastery;
        return this;
    }

    // ──── Getters / Setters ────

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
    }

    public int getActiveStudentsToday() {
        return activeStudentsToday;
    }

    public void setActiveStudentsToday(int activeStudentsToday) {
        this.activeStudentsToday = activeStudentsToday;
    }

    public double getActiveRate() {
        return activeRate;
    }

    public void setActiveRate(double activeRate) {
        this.activeRate = activeRate;
    }

    public double getAverageCorrectRate() {
        return averageCorrectRate;
    }

    public void setAverageCorrectRate(double averageCorrectRate) {
        this.averageCorrectRate = averageCorrectRate;
    }

    public double getCorrectRateChange() {
        return correctRateChange;
    }

    public void setCorrectRateChange(double correctRateChange) {
        this.correctRateChange = correctRateChange;
    }

    public long getTotalAnswersThisWeek() {
        return totalAnswersThisWeek;
    }

    public void setTotalAnswersThisWeek(long totalAnswersThisWeek) {
        this.totalAnswersThisWeek = totalAnswersThisWeek;
    }

    public long getAnswersToday() {
        return answersToday;
    }

    public void setAnswersToday(long answersToday) {
        this.answersToday = answersToday;
    }

    public long getTotalCorrectAnswers() {
        return totalCorrectAnswers;
    }

    public void setTotalCorrectAnswers(long totalCorrectAnswers) {
        this.totalCorrectAnswers = totalCorrectAnswers;
    }

    public int getPendingAlertCount() {
        return pendingAlertCount;
    }

    public void setPendingAlertCount(int pendingAlertCount) {
        this.pendingAlertCount = pendingAlertCount;
    }

    public int getCriticalAlertCount() {
        return criticalAlertCount;
    }

    public void setCriticalAlertCount(int criticalAlertCount) {
        this.criticalAlertCount = criticalAlertCount;
    }

    public int getWeakKnowledgeCount() {
        return weakKnowledgeCount;
    }

    public void setWeakKnowledgeCount(int weakKnowledgeCount) {
        this.weakKnowledgeCount = weakKnowledgeCount;
    }

    public List<KnowledgeMasterySummary> getKnowledgeMastery() {
        return knowledgeMastery;
    }

    public void setKnowledgeMastery(List<KnowledgeMasterySummary> knowledgeMastery) {
        this.knowledgeMastery = knowledgeMastery;
    }

    // ──── 内嵌聚合 VO ────

    /**
     * 知识点掌握度概要 — 用于驾驶舱雷达/柱状图展示。
     */
    public static class KnowledgeMasterySummary {

        /** 知识点 ID */
        private String knowledgePointId;

        /** 知识点名称 */
        private String knowledgePointName;

        /** 全班掌握度（平均正确率 0-100） */
        private double masteryRate;

        /** 涉及题数 */
        private long questionCount;

        /** 作答人次 */
        private long answerCount;

        /** 难度等级 (1-5) */
        private int difficulty;

        public KnowledgeMasterySummary() {
        }

        public KnowledgeMasterySummary(String knowledgePointId, String knowledgePointName,
                                       double masteryRate, long questionCount,
                                       long answerCount, int difficulty) {
            this.knowledgePointId = knowledgePointId;
            this.knowledgePointName = knowledgePointName;
            this.masteryRate = masteryRate;
            this.questionCount = questionCount;
            this.answerCount = answerCount;
            this.difficulty = difficulty;
        }

        // ── Getters / Setters ──

        public String getKnowledgePointId() {
            return knowledgePointId;
        }

        public void setKnowledgePointId(String knowledgePointId) {
            this.knowledgePointId = knowledgePointId;
        }

        public String getKnowledgePointName() {
            return knowledgePointName;
        }

        public void setKnowledgePointName(String knowledgePointName) {
            this.knowledgePointName = knowledgePointName;
        }

        public double getMasteryRate() {
            return masteryRate;
        }

        public void setMasteryRate(double masteryRate) {
            this.masteryRate = masteryRate;
        }

        public long getQuestionCount() {
            return questionCount;
        }

        public void setQuestionCount(long questionCount) {
            this.questionCount = questionCount;
        }

        public long getAnswerCount() {
            return answerCount;
        }

        public void setAnswerCount(long answerCount) {
            this.answerCount = answerCount;
        }

        public int getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(int difficulty) {
            this.difficulty = difficulty;
        }
    }
}
