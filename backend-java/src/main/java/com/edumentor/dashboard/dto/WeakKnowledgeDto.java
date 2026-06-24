package com.edumentor.dashboard.dto;

/**
 * 薄弱知识点 DTO — 教师驾驶舱中标记为"薄弱"的知识点详情。
 * <p>
 * 薄弱判定标准：全班在该知识点的平均正确率低于 60%。
 * </p>
 *
 * @author EduMentor Team
 */
public class WeakKnowledgeDto {

    /** 知识点 ID */
    private String knowledgePointId;

    /** 知识点名称 */
    private String knowledgePointName;

    /** 所属课程名称 */
    private String courseName;

    /** 难度等级 (1-5) */
    private int difficulty;

    /** 重要程度 (1-5) */
    private int importance;

    /** 全班平均正确率（百分比 0-100） */
    private double averageCorrectRate;

    /** 正确率较上周变化（百分点） */
    private double correctRateTrend;

    /** 涉及学生人数 */
    private int affectedStudentCount;

    /** 该知识点下题目总数 */
    private long questionCount;

    /** 累计作答人次 */
    private long totalAnswerCount;

    /** 错误频次 Top 错因 */
    private String topErrorType;

    /** 建议行动 */
    private String suggestion;

    public WeakKnowledgeDto() {
    }

    // ──── Builder-style setters ────

    public WeakKnowledgeDto knowledgePointId(String knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
        return this;
    }

    public WeakKnowledgeDto knowledgePointName(String knowledgePointName) {
        this.knowledgePointName = knowledgePointName;
        return this;
    }

    public WeakKnowledgeDto courseName(String courseName) {
        this.courseName = courseName;
        return this;
    }

    public WeakKnowledgeDto difficulty(int difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public WeakKnowledgeDto importance(int importance) {
        this.importance = importance;
        return this;
    }

    public WeakKnowledgeDto averageCorrectRate(double averageCorrectRate) {
        this.averageCorrectRate = averageCorrectRate;
        return this;
    }

    public WeakKnowledgeDto correctRateTrend(double correctRateTrend) {
        this.correctRateTrend = correctRateTrend;
        return this;
    }

    public WeakKnowledgeDto affectedStudentCount(int affectedStudentCount) {
        this.affectedStudentCount = affectedStudentCount;
        return this;
    }

    public WeakKnowledgeDto questionCount(long questionCount) {
        this.questionCount = questionCount;
        return this;
    }

    public WeakKnowledgeDto totalAnswerCount(long totalAnswerCount) {
        this.totalAnswerCount = totalAnswerCount;
        return this;
    }

    public WeakKnowledgeDto topErrorType(String topErrorType) {
        this.topErrorType = topErrorType;
        return this;
    }

    public WeakKnowledgeDto suggestion(String suggestion) {
        this.suggestion = suggestion;
        return this;
    }

    // ──── Getters / Setters ────

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

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getImportance() {
        return importance;
    }

    public void setImportance(int importance) {
        this.importance = importance;
    }

    public double getAverageCorrectRate() {
        return averageCorrectRate;
    }

    public void setAverageCorrectRate(double averageCorrectRate) {
        this.averageCorrectRate = averageCorrectRate;
    }

    public double getCorrectRateTrend() {
        return correctRateTrend;
    }

    public void setCorrectRateTrend(double correctRateTrend) {
        this.correctRateTrend = correctRateTrend;
    }

    public int getAffectedStudentCount() {
        return affectedStudentCount;
    }

    public void setAffectedStudentCount(int affectedStudentCount) {
        this.affectedStudentCount = affectedStudentCount;
    }

    public long getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(long questionCount) {
        this.questionCount = questionCount;
    }

    public long getTotalAnswerCount() {
        return totalAnswerCount;
    }

    public void setTotalAnswerCount(long totalAnswerCount) {
        this.totalAnswerCount = totalAnswerCount;
    }

    public String getTopErrorType() {
        return topErrorType;
    }

    public void setTopErrorType(String topErrorType) {
        this.topErrorType = topErrorType;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}
