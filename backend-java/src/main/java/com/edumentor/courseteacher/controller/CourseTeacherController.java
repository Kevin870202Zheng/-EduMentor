package com.edumentor.courseteacher.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.courseteacher.dto.AssignTeacherRequest;
import com.edumentor.courseteacher.entity.CourseTeacher;
import com.edumentor.courseteacher.service.CourseTeacherService;
import com.edumentor.entity.enums.UserRole;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/course-teachers")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class CourseTeacherController {

    private final CourseTeacherService courseTeacherService;
    private final UserRepository userRepository;

    public CourseTeacherController(CourseTeacherService courseTeacherService,
                                    UserRepository userRepository) {
        this.courseTeacherService = courseTeacherService;
        this.userRepository = userRepository;
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

    @GetMapping("/available")
    public ApiResponse<List<Map<String, Object>>> listAvailableTeachers() {
        List<User> teachers = userRepository.findByRole(UserRole.TEACHER);
        List<Map<String, Object>> result = teachers.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("displayName", t.getDisplayName() != null ? t.getDisplayName() : t.getUsername());
            m.put("username", t.getUsername());
            return m;
        }).collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> removeTeacher(@PathVariable UUID id) {
        courseTeacherService.removeTeacher(id);
        return ApiResponse.success(null, "教师已移除");
    }
}
