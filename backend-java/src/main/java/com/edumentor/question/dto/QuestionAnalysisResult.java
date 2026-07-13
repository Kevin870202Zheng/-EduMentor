package com.edumentor.question.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 题目分析结果（结构化输出）。
 * 用于大模型对题目进行深度分析，帮助学生理解题目。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestionAnalysisResult {

    /** 考察知识点 */
    @JsonAlias({"knowledgePoint", "knowledge_point"})
    private String knowledgePoint;

    /** 选项详解 */
    @JsonAlias({"optionAnalysis", "option_analysis"})
    private List<OptionAnalysis> optionAnalysis;

    /** 解题思路 */
    @JsonAlias({"solutionSteps", "solution_steps"})
    private List<String> solutionSteps;

    /** 常见错误 */
    @JsonAlias({"commonMistakes", "common_mistakes"})
    private List<String> commonMistakes;

    /** 相关知识 */
    @JsonAlias({"relatedKnowledge", "related_knowledge"})
    private List<String> relatedKnowledge;

    public QuestionAnalysisResult() {}

    public String getKnowledgePoint() { return knowledgePoint; }
    public void setKnowledgePoint(String knowledgePoint) { this.knowledgePoint = knowledgePoint; }

    public List<OptionAnalysis> getOptionAnalysis() { return optionAnalysis; }
    public void setOptionAnalysis(List<OptionAnalysis> optionAnalysis) { this.optionAnalysis = optionAnalysis; }

    public List<String> getSolutionSteps() { return solutionSteps; }
    public void setSolutionSteps(List<String> solutionSteps) { this.solutionSteps = solutionSteps; }

    public List<String> getCommonMistakes() { return commonMistakes; }
    public void setCommonMistakes(List<String> commonMistakes) { this.commonMistakes = commonMistakes; }

    public List<String> getRelatedKnowledge() { return relatedKnowledge; }
    public void setRelatedKnowledge(List<String> relatedKnowledge) { this.relatedKnowledge = relatedKnowledge; }

    /**
     * 单个选项分析。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionAnalysis {
        private String label;
        private String text;
        @JsonAlias({"correct", "isCorrect", "is_correct"})
        private boolean correct;
        private String reason;

        public OptionAnalysis() {}

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public boolean isCorrect() { return correct; }
        public void setCorrect(boolean correct) { this.correct = correct; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
