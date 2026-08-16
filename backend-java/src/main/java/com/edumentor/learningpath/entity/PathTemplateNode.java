package com.edumentor.learningpath.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 路径模板节点实体 — 静态模板（STATIC）的节点快照。
 * <p>
 * 存储 knowledge_point_name 名称快照，生成学生路径时复制到
 * learning_path_nodes，之后模板改动不影响已生成的路径。
 * </p>
 *
 * @author EduMentor Team
 */
@Getter
@Setter
@Entity
@Table(name = "path_template_nodes", indexes = {
    @Index(name = "idx_ptn_template", columnList = "template_id")
})
public class PathTemplateNode extends BaseEntity {

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "knowledge_point_id", nullable = false)
    private UUID knowledgePointId;

    /** 知识点名称快照 */
    @Column(name = "knowledge_point_name", nullable = false, length = 255)
    private String knowledgePointName;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "estimated_minutes", nullable = false)
    private Integer estimatedMinutes = 30;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", getId());
        dto.put("templateId", templateId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("knowledgePointName", knowledgePointName);
        dto.put("orderIndex", orderIndex);
        dto.put("estimatedMinutes", estimatedMinutes);
        return dto;
    }
}
