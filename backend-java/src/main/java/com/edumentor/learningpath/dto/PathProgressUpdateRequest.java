package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 路径节点进度更新请求 DTO。
 * <p>
 * 用于学生在学习过程中更新某个节点的进度状态。
 * 例如：开始学习、完成学习、跳过节点。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class PathProgressUpdateRequest {

    @NotNull(message = "节点 ID 不能为空")
    private UUID nodeId;

    /** 节点状态: IN_PROGRESS / COMPLETED / SKIPPED */
    @NotNull(message = "目标状态不能为空")
    private String status;

    /** 实际花费时长（分钟），完成时可选填写 */
    private Integer actualMinutes;
}
