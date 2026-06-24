package com.edumentor.course.entity;

import com.edumentor.course.entity.enums.RelationType;
import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "knowledge_relations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_kp_relation", columnNames = {"source_kp_id", "target_kp_id", "relation_type"})
})
public class KnowledgeRelation extends BaseEntity {

    @Column(name = "source_kp_id", nullable = false)
    private UUID sourceKpId;

    @Column(name = "target_kp_id", nullable = false)
    private UUID targetKpId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    private RelationType relationType;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(columnDefinition = "text")
    private String description;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("sourceKpId", sourceKpId);
        dto.put("targetKpId", targetKpId);
        dto.put("relationType", relationType != null ? relationType.name() : null);
        dto.put("weight", weight);
        dto.put("description", description);
        dto.put("createdAt", getCreatedAt());
        return dto;
    }
}
