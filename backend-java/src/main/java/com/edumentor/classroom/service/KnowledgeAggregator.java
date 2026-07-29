package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.AggregatedContent;
import com.edumentor.classroom.dto.AggregatedContent.AggregatedSection;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.KnowledgePointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 知识点递归聚合器 — 将 CHAPTER 层级下的所有子知识点内容聚合成结构化文本。
 * <p>
 * 当用户在 CHAPTER 节点点击"沉浸课堂"时，此服务将：
 * 1. 查找该 CHAPTER 下的所有 SECTION 子节点
 * 2. 递归查找每个 SECTION 下的 LEAF 子节点
 * 3. 聚合所有节点的 name + description + content
 * 4. 返回格式化文本供 prompt 使用
 * </p>
 */
@Component
public class KnowledgeAggregator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAggregator.class);

    private final KnowledgePointRepository knowledgePointRepository;

    public KnowledgeAggregator(KnowledgePointRepository knowledgePointRepository) {
        this.knowledgePointRepository = knowledgePointRepository;
    }

    /**
     * 聚合指定节点的所有子知识点内容。
     *
     * @param nodeId 任意层级节点的 ID（推荐传入 CHAPTER ID）
     * @return 聚合内容，包含所有子节点的结构化信息
     */
    public AggregatedContent aggregate(UUID nodeId) {
        KnowledgePoint node = knowledgePointRepository.findById(nodeId).orElse(null);
        if (node == null) {
            log.warn("KnowledgePoint not found: {}", nodeId);
            return createEmpty(nodeId);
        }

        // 递归查找所有子节点
        List<KnowledgePoint> allChildren = findAllChildren(nodeId);

        // 按 type 分组：SECTION 和 LEAF
        List<KnowledgePoint> sections = allChildren.stream()
                .filter(kp -> "SECTION".equals(kp.getType()))
                .toList();

        List<KnowledgePoint> leaves = allChildren.stream()
                .filter(kp -> "LEAF".equals(kp.getType()))
                .toList();

        // 如果没有 SECTION，所有 LEAF 作为一个虚拟节
        List<AggregatedSection> aggregatedSections;
        if (sections.isEmpty()) {
            aggregatedSections = List.of(AggregatedSection.builder()
                    .sectionName(node.getName())
                    .sectionDescription(node.getDescription())
                    .knowledgePoints(leaves)
                    .build());
        } else {
            aggregatedSections = new ArrayList<>();
            for (KnowledgePoint section : sections) {
                List<KnowledgePoint> sectionLeaves = allChildren.stream()
                        .filter(kp -> section.getId().equals(kp.getParentKpId()))
                        .toList();
                aggregatedSections.add(AggregatedSection.builder()
                        .sectionName(section.getName())
                        .sectionDescription(section.getDescription())
                        .knowledgePoints(sectionLeaves.isEmpty()
                                ? List.of(section)  // 如果节没有子节点，至少包含节本身
                                : sectionLeaves)
                        .build());
            }
        }

        int totalKps = leaves.isEmpty() ? sections.size() : leaves.size();

        return AggregatedContent.builder()
                .chapter(node)
                .sections(aggregatedSections)
                .totalKnowledgePoints(totalKps)
                .build();
    }

    /**
     * 递归查找所有子节点（广度优先）。
     */
    private List<KnowledgePoint> findAllChildren(UUID parentId) {
        List<KnowledgePoint> result = new ArrayList<>();
        List<KnowledgePoint> directChildren = knowledgePointRepository.findByParentKpId(parentId);
        for (KnowledgePoint child : directChildren) {
            result.add(child);
            result.addAll(findAllChildren(child.getId()));
        }
        return result;
    }

    private AggregatedContent createEmpty(UUID nodeId) {
        return AggregatedContent.builder()
                .chapter(null)
                .sections(List.of())
                .totalKnowledgePoints(0)
                .build();
    }
}
