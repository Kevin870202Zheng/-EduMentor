package com.edumentor.learningpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 学习路径规划请求 DTO。
 * <p>
 * 请求系统为学生规划个性化的学习路径。
 * 系统会根据学生的学情诊断结果、知识薄弱点、知识图谱结构自动生成路径。
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
}
