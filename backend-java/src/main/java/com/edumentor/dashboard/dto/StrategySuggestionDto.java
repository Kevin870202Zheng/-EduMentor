package com.edumentor.dashboard.dto;

/**
 * 策略建议 DTO — 教师驾驶舱中基于数据分析给出的教学策略建议。
 * <p>
 * 建议按优先级排序，每个建议包含问题描述、原因分析和具体行动指南。
 * </p>
 *
 * @author EduMentor Team
 */
public class StrategySuggestionDto {

    /** 建议 ID（唯一标识） */
    private String suggestionId;

    /** 优先级: HIGH / MEDIUM / LOW */
    private String priority;

    /** 建议分类: mastery / engagement / alert / review / path */
    private String category;

    /** 建议标题（如 "三角函数掌握度偏低，建议集中复习"） */
    private String title;

    /** 详细描述 */
    private String description;

    /** 问题根因分析 */
    private String rootCause;

    /** 具体行动建议 */
    private String action;

    /** 预期效果 */
    private String expectedOutcome;

    /** 关联知识点 ID（如果基于某个知识点） */
    private String relatedKnowledgePointId;

    /** 关联知识点名称 */
    private String relatedKnowledgePointName;

    /** 关联学生人数 */
    private int affectedStudentCount;

    /** 影响度评分 (0-100) */
    private int impactScore;

    /** 是否已采纳 */
    private boolean adopted;

    public StrategySuggestionDto() {
    }

    // ──── Builder-style setters ────

    public StrategySuggestionDto suggestionId(String suggestionId) {
        this.suggestionId = suggestionId;
        return this;
    }

    public StrategySuggestionDto priority(String priority) {
        this.priority = priority;
        return this;
    }

    public StrategySuggestionDto category(String category) {
        this.category = category;
        return this;
    }

    public StrategySuggestionDto title(String title) {
        this.title = title;
        return this;
    }

    public StrategySuggestionDto description(String description) {
        this.description = description;
        return this;
    }

    public StrategySuggestionDto rootCause(String rootCause) {
        this.rootCause = rootCause;
        return this;
    }

    public StrategySuggestionDto action(String action) {
        this.action = action;
        return this;
    }

    public StrategySuggestionDto expectedOutcome(String expectedOutcome) {
        this.expectedOutcome = expectedOutcome;
        return this;
    }

    public StrategySuggestionDto relatedKnowledgePointId(String relatedKnowledgePointId) {
        this.relatedKnowledgePointId = relatedKnowledgePointId;
        return this;
    }

    public StrategySuggestionDto relatedKnowledgePointName(String relatedKnowledgePointName) {
        this.relatedKnowledgePointName = relatedKnowledgePointName;
        return this;
    }

    public StrategySuggestionDto affectedStudentCount(int affectedStudentCount) {
        this.affectedStudentCount = affectedStudentCount;
        return this;
    }

    public StrategySuggestionDto impactScore(int impactScore) {
        this.impactScore = impactScore;
        return this;
    }

    public StrategySuggestionDto adopted(boolean adopted) {
        this.adopted = adopted;
        return this;
    }

    // ──── Getters / Setters ────

    public String getSuggestionId() {
        return suggestionId;
    }

    public void setSuggestionId(String suggestionId) {
        this.suggestionId = suggestionId;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public void setExpectedOutcome(String expectedOutcome) {
        this.expectedOutcome = expectedOutcome;
    }

    public String getRelatedKnowledgePointId() {
        return relatedKnowledgePointId;
    }

    public void setRelatedKnowledgePointId(String relatedKnowledgePointId) {
        this.relatedKnowledgePointId = relatedKnowledgePointId;
    }

    public String getRelatedKnowledgePointName() {
        return relatedKnowledgePointName;
    }

    public void setRelatedKnowledgePointName(String relatedKnowledgePointName) {
        this.relatedKnowledgePointName = relatedKnowledgePointName;
    }

    public int getAffectedStudentCount() {
        return affectedStudentCount;
    }

    public void setAffectedStudentCount(int affectedStudentCount) {
        this.affectedStudentCount = affectedStudentCount;
    }

    public int getImpactScore() {
        return impactScore;
    }

    public void setImpactScore(int impactScore) {
        this.impactScore = impactScore;
    }

    public boolean isAdopted() {
        return adopted;
    }

    public void setAdopted(boolean adopted) {
        this.adopted = adopted;
    }
}
