package com.edumentor.classroom.entity.enums;

/**
 * 学段协作课堂 — 四学段角色分工（设计文档 §5.1）。
 * <ul>
 *   <li>STORY_PICKER：小学 PRIMARY 学生从故事库选定故事</li>
 *   <li>CHARACTER_DESIGNER：初中 JUNIOR 学生设计角色形象</li>
 *   <li>SCRIPT_WRITER：高中 SENIOR 学生设计台词</li>
 *   <li>LEGAL_MAPPER：大学 UNIVERSITY 学生映射法律知识</li>
 * </ul>
 */
public enum CollabRoleType {
    STORY_PICKER,
    CHARACTER_DESIGNER,
    SCRIPT_WRITER,
    LEGAL_MAPPER
}
