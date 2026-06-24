package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 智能适配请求 DTO。
 * <p>
 * 根据学生最新的学情数据，动态调整当前学习路径的节点顺序或内容。
 * 支持重新排序、缩短、扩展、聚焦薄弱点等策略。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class PathAdaptRequest {

    @NotNull(message = "路径 ID 不能为空")
    private UUID pathId;

    /** 适配策略: REORDER / SHORTEN / EXPAND / FOCUS_WEAK */
    private String adaptStrategy = "REORDER";

    /** 替换或新增的知识点 ID 列表（EXPAND 策略时使用） */
    private List<UUID> newKpIds;
}
