package com.edumentor.classroom.dto;

import com.edumentor.course.entity.KnowledgePoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聚合内容 DTO — 记录章级课堂生成时递归聚合的子知识点内容。
 * <p>
 * 当用户在 CHAPTER 层级点击"沉浸课堂"时，系统会自动聚合该章下
 * 所有 SECTION 和 LEAF 节点的名称、描述、详细内容，形成此结构。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedContent {

    /** 章节点信息 */
    private KnowledgePoint chapter;

    /** 按节分组的子知识点 */
    private List<AggregatedSection> sections;

    /** 总子知识点数量 */
    private int totalKnowledgePoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AggregatedSection {
        private String sectionName;
        private String sectionDescription;
        private List<KnowledgePoint> knowledgePoints;
    }

    /**
     * 将聚合内容格式化为 LLM 可读的文本。
     */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();

        sb.append("## 本章知识结构\n");
        sb.append("本章包含 ").append(sections.size()).append(" 节");
        sb.append("，共 ").append(totalKnowledgePoints).append(" 个知识点。\n\n");

        for (int i = 0; i < sections.size(); i++) {
            AggregatedSection section = sections.get(i);
            sb.append("【").append(i + 1).append(". ").append(section.getSectionName()).append("】\n");
            if (section.getSectionDescription() != null && !section.getSectionDescription().isEmpty()) {
                sb.append("概述：").append(section.getSectionDescription()).append("\n");
            }
            for (KnowledgePoint kp : section.getKnowledgePoints()) {
                sb.append("  - ").append(kp.getName()).append("\n");
                if (kp.getContent() != null && !kp.getContent().isEmpty()) {
                    sb.append("    内容：").append(kp.getContent()).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
