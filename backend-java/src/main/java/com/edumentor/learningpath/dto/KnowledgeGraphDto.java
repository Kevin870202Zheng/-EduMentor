package com.edumentor.learningpath.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * 知识图谱 DTO — 用于前端知识图谱可视化。
 * <p>
 * 包含节点列表（知识点）和连线列表（知识点间关系），
 * 前端可使用 D3.js / vis-network 等库渲染。
 * </p>
 *
 * @author EduMentor Team
 */
@Schema(description = "知识图谱结构 DTO")
public class KnowledgeGraphDto {

    @Schema(description = "知识点节点列表")
    private List<GraphNode> nodes;

    @Schema(description = "知识点关系连线列表")
    private List<GraphEdge> edges;

    public KnowledgeGraphDto() {}

    public KnowledgeGraphDto(List<GraphNode> nodes, List<GraphEdge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    // ──── Getters & Setters ────

    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> nodes) { this.nodes = nodes; }

    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> edges) { this.edges = edges; }

    // ══════════════════════════════════════
    //  内部类
    // ══════════════════════════════════════

    /**
     * 图谱节点 — 代表一个知识点。
     */
    @Schema(description = "知识图谱节点")
    public static class GraphNode {

        @Schema(description = "知识点 ID")
        private UUID id;

        @Schema(description = "知识点名称")
        private String name;

        @Schema(description = "难度等级 (1-5)")
        private Integer difficulty;

        @Schema(description = "重要程度 (1-5)")
        private Integer importance;

        @Schema(description = "掌握度 (0-1，学生已掌握时显示)")
        private Double masteryLevel;

        @Schema(description = "是否为薄弱知识点")
        private boolean isWeak;

        public GraphNode() {}

        public GraphNode(UUID id, String name, Integer difficulty, Integer importance) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.importance = importance;
        }

        // ──── Getters & Setters ────

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getDifficulty() { return difficulty; }
        public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }

        public Integer getImportance() { return importance; }
        public void setImportance(Integer importance) { this.importance = importance; }

        public Double getMasteryLevel() { return masteryLevel; }
        public void setMasteryLevel(Double masteryLevel) { this.masteryLevel = masteryLevel; }

        public boolean isWeak() { return isWeak; }
        public void setWeak(boolean weak) { isWeak = weak; }
    }

    /**
     * 图谱连线 — 代表两个知识点之间的关系。
     */
    @Schema(description = "知识图谱关系连线")
    public static class GraphEdge {

        @Schema(description = "源知识点 ID")
        private UUID sourceId;

        @Schema(description = "目标知识点 ID")
        private UUID targetId;

        @Schema(description = "关系类型: PREREQUISITE / PARENT_OF / RELATED")
        private String relationType;

        @Schema(description = "关系权重 (0-1)")
        private Double weight;

        public GraphEdge() {}

        public GraphEdge(UUID sourceId, UUID targetId, String relationType, Double weight) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.relationType = relationType;
            this.weight = weight;
        }

        // ──── Getters & Setters ────

        public UUID getSourceId() { return sourceId; }
        public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }

        public UUID getTargetId() { return targetId; }
        public void setTargetId(UUID targetId) { this.targetId = targetId; }

        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }

        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }
    }
}
