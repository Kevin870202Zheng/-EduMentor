package com.edumentor.classroom.controller;

import com.edumentor.classroom.service.ClassroomDashboardService;
import com.edumentor.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 教师驾驶舱 — 课堂学情API。
 */
@RestController
@RequestMapping("/api/v2/dashboard")
@Tag(name = "教师驾驶舱-课堂学情", description = "课堂完成度、场景级成绩分布、知识点掌握度")
public class ClassroomDashboardController {

    private static final Logger log = LoggerFactory.getLogger(ClassroomDashboardController.class);

    private final ClassroomDashboardService dashboardService;

    public ClassroomDashboardController(ClassroomDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 课堂学情概览。
     */
    @GetMapping("/classroom-overview")
    @Operation(summary = "课堂学情概览", description = "全班课堂完成度 + Quiz正确率聚合")
    public ApiResponse<ClassroomDashboardService.ClassroomOverview> getClassroomOverview(
            @RequestParam String classroomId) {
        ClassroomDashboardService.ClassroomOverview overview =
                dashboardService.getClassroomOverview(UUID.fromString(classroomId));
        return ApiResponse.success(overview);
    }

    /**
     * 学生课堂详情（场景级数据）。
     */
    @GetMapping("/classroom-detail")
    @Operation(summary = "学生课堂详情", description = "某个学生的课堂详情，包含场景级数据")
    public ApiResponse<ClassroomDashboardService.StudentClassroomDetail> getStudentDetail(
            @RequestParam String studentId,
            @RequestParam String classroomId) {
        var detail = dashboardService.getStudentDetail(
                UUID.fromString(studentId), UUID.fromString(classroomId));
        return ApiResponse.success(detail);
    }

    /**
     * 知识点掌握度分布。
     */
    @GetMapping("/knowledge-point-mastery")
    @Operation(summary = "知识点掌握度", description = "课堂相关知识点的学生Quiz正确率分布")
    public ApiResponse<List<ClassroomDashboardService.KnowledgePointMastery>> getKnowledgePointMastery(
            @RequestParam String classroomId) {
        var mastery = dashboardService.getKnowledgePointMastery(UUID.fromString(classroomId));
        return ApiResponse.success(mastery);
    }
}
