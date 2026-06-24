package com.edumentor.student.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.student.dto.StudentProfileUpdateRequest;
import com.edumentor.student.service.StudentProfileService;
import com.edumentor.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<Map<String, Object>> getProfile(@PathVariable UUID userId) {
        return ApiResponse.success(studentProfileService.getProfile(userId));
    }

    @PutMapping("/{userId}/profile")
    public ApiResponse<Map<String, Object>> updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody StudentProfileUpdateRequest request) {
        return ApiResponse.success(studentProfileService.updateProfile(userId, request), "个人信息已更新");
    }
}
