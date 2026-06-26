package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 路径规划请求 DTO — 规划个性化学习路径。
 *
 * <p>
 * 包含学生、课程信息及路径规划参数。
 * adaptStrategy 支持四种策略：
 * <ul>
 *   <li><b>REORDER</b> — 均衡推荐：跳过已掌握，按先修+难度排序</li>
 *   <li><b>SHORTEN</b> — 最短路径：跳过已掌握，精简高效</li>
 *   <li><b>FOCUS_WEAK</b> — 拓展探索：不跳过，薄弱点优先排序</li>
 *   <li><b>EXPAND</b> — 拓展补充：为薄弱知识点追加补充节点</li>
 * </ul>
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class PathPlanRequest {

    @NotNull(message = "学生 ID 不能为空")
    private UUID studentId;

    @NotNull(message = "课程 ID 不能为空")
    private UUID courseId;

    @NotBlank(message = "路径名称不能为空")
    private String name;

    private String description;

    /** 是否跳过已掌握的知识点 */
    private Boolean skipMastered = true;

    /** 每日建议学习时长（分钟） */
    private Integer dailyMinutes;

    /** 聚焦知识点 ID（可选，聚焦到某个具体知识点） */
    private UUID focusKpId;

    /** 距离考试的天数（可选，用于倒排学习计划） */
    private Integer examDaysLeft;

    /**
     * 适配策略: REORDER / SHORTEN / FOCUS_WEAK / EXPAND
     * 默认 REORDER（均衡推荐）
     */
    private String adaptStrategy = "REORDER";
}
