package com.edumentor.entity.enums;

/**
 * 复习类型枚举 — 对应 {@code review_records.review_type} 列。
 * <p>
 * 用于区分不同类型的复习任务，帮助系统按排程策略组织复习计划。
 * </p>
 *
 * @author EduMentor Team
 */
public enum ReviewType {

    /** 定期复习 — 基于艾宾浩斯遗忘曲线排程的周期性复习 */
    SCHEDULED_REVIEW,

    /** 错题复习 — 针对已记录的错题进行的针对性复习 */
    ERROR_REVIEW,

    /** 自定义复习 — 学生或教师自定义的复习任务 */
    CUSTOM_REVIEW,

    /** 考前复习 — 针对考试的专项复习 */
    EXAM_REVIEW
}
