package com.edumentor.courseteacher.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.courseteacher.dto.AssignTeacherRequest;
import com.edumentor.courseteacher.entity.CourseTeacher;
import com.edumentor.courseteacher.service.CourseTeacherService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/course-teachers")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class CourseTeacherController {

    private final CourseTeacherService courseTeacherService;

    public CourseTeacherController(CourseTeacherService courseTeacherService) {
        this.courseTeacherService = courseTeacherService;
    }

    @PostMapping
    public ApiResponse<CourseTeacher> assignTeacher(@Valid @RequestBody AssignTeacherRequest request) {
        CourseTeacher ct = courseTeacherService.assignTeacher(request);
        return ApiResponse.success(ct, "教师分配成功");
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<Map<String, Object>>> listTeachers(@PathVariable UUID courseId) {
        return ApiResponse.success(courseTeacherService.listCourseTeachers(courseId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> removeTeacher(@PathVariable UUID id) {
        courseTeacherService.removeTeacher(id);
        return ApiResponse.success(null, "教师已移除");
    }
}
