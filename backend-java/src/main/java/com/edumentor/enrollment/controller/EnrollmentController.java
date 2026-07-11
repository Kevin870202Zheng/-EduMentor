package com.edumentor.enrollment.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.enrollment.dto.EnrollmentDto;
import com.edumentor.enrollment.dto.EnrollRequest;
import com.edumentor.enrollment.service.EnrollmentService;
import com.edumentor.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 选课管理 REST API。
 */
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * 学生选课。
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<EnrollmentDto> enroll(
            @Valid @RequestBody EnrollRequest request,
            @AuthenticationPrincipal User currentUser) {
        EnrollmentDto dto = enrollmentService.enroll(request.getStudentId(), request.getCourseId());
        return ApiResponse.success(dto, "选课成功");
    }

    /**
     * 退课。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<EnrollmentDto> dropCourse(@PathVariable UUID id) {
        EnrollmentDto dto = enrollmentService.dropCourse(id);
        return ApiResponse.success(dto, "退课成功");
    }

    /**
     * 获取学生的选课列表。
     */
    @GetMapping("/student/{studentId}")
    public ApiResponse<List<EnrollmentDto>> listStudentCourses(@PathVariable UUID studentId) {
        List<EnrollmentDto> courses = enrollmentService.listStudentCourses(studentId);
        return ApiResponse.success(courses);
    }

    /**
     * 获取学生已退课列表。
     */
    @GetMapping("/student/{studentId}/dropped")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<EnrollmentDto>> listDroppedCourses(@PathVariable UUID studentId) {
        List<EnrollmentDto> courses = enrollmentService.listDroppedCourses(studentId);
        return ApiResponse.success(courses);
    }

    /**
     * 获取课程下的学生数量。
     */
    @GetMapping("/course/{courseId}/count")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Long> countCourseStudents(@PathVariable UUID courseId) {
        long count = enrollmentService.countCourseStudents(courseId);
        return ApiResponse.success(count);
    }
}
