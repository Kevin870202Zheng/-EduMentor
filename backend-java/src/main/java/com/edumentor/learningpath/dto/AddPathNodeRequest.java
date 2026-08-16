package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 追加路径节点请求 DTO。
 * <p>
 * orderIndex 可选：缺省追加到路径末尾。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class AddPathNodeRequest {

    @NotNull(message = "知识点 ID 不能为空")
    private UUID knowledgePointId;

    /** 可选：插入位置（缺省追加到末尾） */
    private Integer orderIndex;
}
