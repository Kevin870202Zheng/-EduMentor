package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 路径节点重排请求 DTO。
 * <p>
 * nodeIds 为路径节点（learning_path_nodes.id）的新顺序。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class ReorderNodesRequest {

    /** 有序的路径节点 ID 列表 */
    @NotNull(message = "节点顺序列表不能为空")
    private List<UUID> nodeIds;
}
