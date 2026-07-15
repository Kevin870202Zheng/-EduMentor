package com.edumentor.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * AI 生成树结构响应 DTO。
 * <p>
 * 包含生成的树结构、统计信息和孤立的未归属知识点列表。
 * </p>
 *
 * @param tree           完整的嵌套树结构（顶层节点列表）
 * @param stats          生成统计信息
 * @param orphanedKpIds  未找到归属的知识点 ID 列表（如果有）
 *
 * @author EduMentor Team
 * @version 1.0
 */
public record TreeGenerateResult(
        List<TreeNode> tree,
        TreeStats stats,
        List<UUID> orphanedKpIds
) {
    /**
     * 树节点（递归嵌套结构）。
     *
     * @param kpId     知识点 ID（LEAF 类型必填，中间节点可能为 null）
     * @param name     节点名称
     * @param type     节点类型：VOLUME / PART / CHAPTER / SECTION / LEAF
     * @param order    在同一层级中的排序序号
     * @param children 子节点列表
     * @param change   相对于上次生成的变更标记：NEW / CHANGED / REMOVED / UNCHANGED
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TreeNode(
            UUID kpId,
            String name,
            String type,
            int order,
            List<TreeNode> children,
            String change
    ) {}

    /**
     * 生成统计。
     *
     * @param totalNodes    总节点数（含所有层级）
     * @param newNodes      本次新增的节点数
     * @param keptNodes     保留的节点数
     * @param removedNodes  标记删除的节点数
     * @param volumes       编的数量
     * @param chapters      章的数量
     * @param sections      节的数量
     * @param leafKps       知识点（LEAF）数量
     */
    public record TreeStats(
            int totalNodes,
            int newNodes,
            int keptNodes,
            int removedNodes,
            int volumes,
            int chapters,
            int sections,
            int leafKps
    ) {}
}
