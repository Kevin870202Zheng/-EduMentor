package com.edumentor.classroom.entity.enums;

/**
 * 模拟法庭会话状态机。
 * CASE_GENERATING → OPENING → HEARING → JUDGMENT_READY → JUDGED → REPORTED
 */
public enum MootCourtStatus {
    /** 案件生成中（LLM 按课堂知识点构造案件，约 10-20s） */
    CASE_GENERATING,
    /** 已宣布开庭（书记员开场 + 原告首次陈述） */
    OPENING,
    /** 庭审对抗中（原被告交替发言，法官可随时干预） */
    HEARING,
    /** 庭审结束，等待法官（学生）提交判决 */
    JUDGMENT_READY,
    /** 已判决（若 PRE+POST 两份齐全，报告可生成） */
    JUDGED,
    /** 分析报告已生成（两份判决对比） */
    REPORTED
}
