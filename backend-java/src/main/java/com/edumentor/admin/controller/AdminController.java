package com.edumentor.admin.controller;

import com.edumentor.admin.dto.AdminStatsDto;
import com.edumentor.admin.dto.AdminUserDto;
import com.edumentor.admin.service.AdminService;
import com.edumentor.common.response.ApiResponse;
import com.edumentor.entity.enums.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员管理 API — 用户管理、系统统计。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsDto> getStats() {
        return ApiResponse.success(adminService.getStats());
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserDto>> listUsers(@RequestParam UserRole role) {
        return ApiResponse.success(adminService.listUsersByRole(role));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserDto> getUser(@PathVariable UUID id) {
        return ApiResponse.success(adminService.getUser(id));
    }

    @PostMapping("/users/teacher")
    public ApiResponse<AdminUserDto> createTeacher(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String displayName = body.get("displayName");
        String email = body.get("email");

        if (username == null || password == null) {
            return ApiResponse.error(400, "用户名和密码不能为空");
        }
        if (password.length() < 6) {
            return ApiResponse.error(400, "密码长度不能少于6位");
        }

        try {
            AdminUserDto dto = adminService.createTeacher(username, password, displayName, email);
            return ApiResponse.success(dto, "教师账号创建成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable UUID id) {
        try {
            adminService.deleteUser(id);
            return ApiResponse.success(null, "用户已删除");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<AdminUserDto> toggleStatus(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        boolean active = body.getOrDefault("active", true);
        try {
            AdminUserDto dto = adminService.toggleUserStatus(id, active);
            return ApiResponse.success(dto, active ? "用户已启用" : "用户已禁用");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/users/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ApiResponse.error(400, "新密码长度不能少于6位");
        }
        adminService.resetPassword(id, newPassword);
        return ApiResponse.success(null, "密码已重置");
    }
}
