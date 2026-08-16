package com.edumentor.classroom.entity.enums;

/**
 * 学段协作课堂 — 项目状态机（设计文档 §5.2）。
 * DRAFT → INVITING → COLLECTING → REVIEW → GENERATING → PUBLISHED
 * （ARCHIVED 为任意阶段可归档）
 */
public enum CollabProjectStatus {
    DRAFT,
    INVITING,
    COLLECTING,
    REVIEW,
    GENERATING,
    PUBLISHED,
    ARCHIVED
}
