package com.edumentor.admin.service;

import com.edumentor.admin.dto.AdminStatsDto;
import com.edumentor.admin.dto.AdminUserDto;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.entity.enums.UserRole;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 管理员服务 — 用户管理、系统统计。
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        CourseRepository courseRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AdminStatsDto getStats() {
        List<User> allUsers = userRepository.findAll();
        long teacherCount = allUsers.stream().filter(u -> u.getRole() == UserRole.TEACHER).count();
        long studentCount = allUsers.stream().filter(u -> u.getRole() == UserRole.STUDENT).count();
        long activeTeachers = allUsers.stream()
                .filter(u -> u.getRole() == UserRole.TEACHER && u.getIsActive()).count();
        long activeStudents = allUsers.stream()
                .filter(u -> u.getRole() == UserRole.STUDENT && u.getIsActive()).count();
        long courseCount = courseRepository.count();

        return new AdminStatsDto(
                allUsers.size(), teacherCount, studentCount,
                courseCount, activeTeachers, activeStudents);
    }

    public List<AdminUserDto> listUsersByRole(UserRole role) {
        return userRepository.findByRole(role).stream()
                .map(this::toDto)
                .toList();
    }

    public AdminUserDto getUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户", id));
        return toDto(user);
    }

    @Transactional
    public AdminUserDto createTeacher(String username, String password, String displayName, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        if (email != null && !email.isBlank() && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已被使用: " + email);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName != null ? displayName : username);
        user.setEmail(email);
        user.setRole(UserRole.TEACHER);
        user.setIsActive(true);
        user = userRepository.save(user);

        log.info("管理员创建教师账号: username={}, id={}", username, user.getId());
        return toDto(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户", id));
        if (user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("不能删除管理员账号");
        }
        userRepository.delete(user);
        log.info("管理员删除用户: id={}, username={}, role={}", id, user.getUsername(), user.getRole());
    }

    @Transactional
    public AdminUserDto toggleUserStatus(UUID id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户", id));
        if (user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("不能修改管理员账号状态");
        }
        user.setIsActive(active);
        user = userRepository.save(user);
        log.info("管理员{}用户: id={}, username={}", active ? "启用" : "禁用", id, user.getUsername());
        return toDto(user);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户", id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("管理员重置用户密码: id={}, username={}", id, user.getUsername());
    }

    private AdminUserDto toDto(User user) {
        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole().name(),
                user.getIsActive(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
