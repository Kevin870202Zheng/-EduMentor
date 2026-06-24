package com.edumentor.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识点掌握度 DTO。
 *
 * <p>描述学生对某个知识点的掌握情况，包含尝试次数、正确数和掌握度评估。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识点掌握度")
public class KnowledgeMasteryDTO {

    @Schema(description = "知识点 ID")
    private UUID knowledgePointId;

    @Schema(description = "知识点名称")
    private String knowledgePointName;

    @Schema(description = "掌握度 (0.00 ~ 1.00)", example = "0.75")
    private BigDecimal masteryLevel;

    @Schema(description = "总尝试次数", example = "20")
    private int totalAttempts;

    @Schema(description = "正确次数", example = "15")
    private int correctCount;

    @Schema(description = "最近答题时间")
    private LocalDateTime lastAttemptedAt;
}
