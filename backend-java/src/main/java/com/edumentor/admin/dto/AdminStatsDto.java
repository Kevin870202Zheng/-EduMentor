package com.edumentor.admin.dto;

/**
 * 管理员端系统统计 DTO。
 */
public record AdminStatsDto(
        long totalUsers,
        long teacherCount,
        long studentCount,
        long courseCount,
        long activeTeachers,
        long activeStudents
) {}
