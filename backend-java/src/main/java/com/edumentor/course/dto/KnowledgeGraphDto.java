package com.edumentor.course.dto;

import java.util.List;
import java.util.UUID;

/**
 * 知识图谱结构 DTO。
 * <p>
 * 用于前端知识图谱可视化的数据结构，包含节点和边的列表。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
public class KnowledgeGraphDto {

    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    public KnowledgeGraphDto() {
    }

    public KnowledgeGraphDto(List<GraphNode> nodes, List<GraphEdge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public List<GraphNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphNode> nodes) {
        this.nodes = nodes;
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphEdge> edges) {
        this.edges = edges;
    }

    /**
     * 知识图谱节点。
     *
     * @param id         知识点 ID
     * @param name       知识点名称
     * @param level      层级深度（0 为顶层）
     * @param difficulty 难度等级（用于节点颜色映射）
     */
    public record GraphNode(
            UUID id,
            String name,
            int level,
            int difficulty
    ) {}

    /**
     * 知识图谱边。
     *
     * @param sourceId     源知识点 ID
     * @param targetId     目标知识点 ID
     * @param relationType 关系类型（PREREQUISITE/PARENT_OF/RELATED）
     * @param weight       关系权重
     */
    public record GraphEdge(
            UUID sourceId,
            UUID targetId,
            String relationType,
            double weight
    ) {}
}
