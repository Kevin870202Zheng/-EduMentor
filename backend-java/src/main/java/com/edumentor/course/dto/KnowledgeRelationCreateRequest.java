package com.edumentor.course.dto;

import com.edumentor.course.entity.enums.RelationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 创建知识点关系请求 DTO。
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Data
public class KnowledgeRelationCreateRequest {

    @NotNull(message = "源知识点不能为空")
    private UUID sourceKpId;

    @NotNull(message = "目标知识点不能为空")
    private UUID targetKpId;

    @NotNull(message = "关系类型不能为空")
    private RelationType relationType;

    @DecimalMin(value = "0.0", message = "权重最小为 0")
    @DecimalMax(value = "1.0", message = "权重最大为 1")
    private BigDecimal weight;

    private String description;
}
