package com.edumentor.classroom.entity.enums;

/**
 * 庭审消息角色。
 */
public enum MootCourtRole {
    /** 系统/书记员（开庭词、流程播报） */
    CLERK,
    /** 原告（AI 扮演） */
    PLAINTIFF_AI,
    /** 被告（AI 扮演） */
    DEFENDANT_AI,
    /** 法官（学生） */
    JUDGE_STUDENT
}
