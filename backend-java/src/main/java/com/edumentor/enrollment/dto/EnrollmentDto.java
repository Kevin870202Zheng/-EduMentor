package com.edumentor.enrollment.dto;

import com.edumentor.enrollment.entity.StudentCourse;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 选课信息 DTO。
 */
public record EnrollmentDto(
        UUID id,
        UUID studentId,
        UUID courseId,
        String courseCode,
        String courseName,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime completedAt
) {
    public static EnrollmentDto fromEntity(StudentCourse sc, String courseName) {
        return new EnrollmentDto(
                sc.getId(),
                sc.getStudentId(),
                sc.getCourseId(),
                sc.getCourseCode(),
                courseName,
                sc.getStatus(),
                sc.getEnrolledAt(),
                sc.getCompletedAt()
        );
    }
}
