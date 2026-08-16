package com.edumentor.classroom.entity.enums;

/**
 * 模拟法庭阶段：PRE=课前法庭（学完前），POST=课后法庭（学完后）。
 * 同一课堂同一学生各一个会话，共用同一份案件（case_content）。
 */
public enum MootCourtPhase {
    PRE,
    POST
}
