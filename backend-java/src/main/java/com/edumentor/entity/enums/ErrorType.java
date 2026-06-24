package com.edumentor.entity.enums;

/**
 * 错因分类枚举 — 对应 {@code error_records.error_type} 列。
 * <p>
 * 用于标识学生答题出错的根本原因，帮助系统进行针对性分析和推荐复习策略。
 * </p>
 *
 * @author EduMentor Team
 */
public enum ErrorType {

    /** 知识盲区 — 学生对相关知识点完全未掌握 */
    KNOWLEDGE_GAP,

    /** 粗心大意 — 因疏忽导致的非知识性错误 */
    CARELESS,

    /** 理解偏差 — 对概念或题意理解有误 */
    MISUNDERSTANDING,

    /** 方法不当 — 解题方法或思路错误 */
    METHOD_ERROR,

    /** 时间不足 — 因超时或时间压力导致的错误 */
    TIME_OUT,

    /** 其他原因 — 无法归类的错误类型 */
    OTHER
}
