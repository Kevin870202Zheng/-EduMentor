package com.edumentor.arbitration.entity.enums;

/**
 * 仲裁阶段：PRE 课前 / POST 课后。
 * 同一知识点同一学生两场仲裁，共用同一案件（POST 复用 PRE 的 case_content）。
 */
public enum ArbitrationPhase {
    /** 课前仲裁（开始学习前，随时可进入） */
    PRE,
    /** 课后仲裁（需掌握度 ≥ 0.5，复用课前案件） */
    POST
}
