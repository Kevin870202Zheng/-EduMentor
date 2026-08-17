package com.edumentor.arbitration.entity.enums;

/**
 * 仲裁会话状态机。
 */
public enum ArbitrationStatus {
    /** 案件生成中（仅 PRE 首次进入短暂存在） */
    CASE_GENERATING,
    /** 已开庭（记录员播报案情） */
    OPENING,
    /** 庭审对话中（仲裁人提问，AI 原/被告应答） */
    HEARING,
    /** 待仲裁人提交裁决书 */
    AWARD_READY,
    /** 已裁决 */
    AWARDED,
    /** 双裁决对比报告已生成 */
    REPORTED
}
