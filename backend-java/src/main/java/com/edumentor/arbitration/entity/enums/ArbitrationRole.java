package com.edumentor.arbitration.entity.enums;

/**
 * 仲裁消息角色。
 * 学生扮演仲裁人；AI 扮演无法律基础的普通老百姓原/被告（降智人设）。
 */
public enum ArbitrationRole {
    /** 记录员（系统播报） */
    CLERK,
    /** 原告（AI 扮演·普通老百姓） */
    PLAINTIFF_AI,
    /** 被告（AI 扮演·普通老百姓） */
    DEFENDANT_AI,
    /** 仲裁人（学生） */
    ARBITER_STUDENT
}
