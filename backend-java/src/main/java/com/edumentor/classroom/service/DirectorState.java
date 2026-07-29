package com.edumentor.classroom.service;

/**
 * 课堂导演状态枚举。
 * 表示当前课堂的交互模式。
 */
public enum DirectorState {
    /** AI教师主讲 */
    LECTURE,
    /** 随堂练习/Quiz */
    QUIZ,
    /** 讨论（AI同学提问） */
    DISCUSSION,
    /** 苏格拉底式引导（答错后） */
    TUTOR,
    /** 学生打断/提问 */
    INTERRUPTED
}
