package com.edumentor.dashboard.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.dashboard.dto.*;
import com.edumentor.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "教师驾驶舱", description = "学情总览、学生列表、薄弱知识点、每日简报、策略建议")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 获取班级学情总览
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "班级学情总览", description = "获取整个班级或指定课程的学情总览数据，含学生统计、正确率、预警、知识点掌握度等")
    public ApiResponse<ClassOverviewDto> getClassOverview(
            @Parameter(description = "课程 ID（可选，不传则查全部）")
            @RequestParam(required = false) String courseId) {
        ClassOverviewDto overview = dashboardService.getClassOverview(courseId);
        return ApiResponse.success(overview, "获取学情总览成功");
    }

    /**
     * 获取学生列表（分页，带学情摘要）
     */
    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "学生列表", description = "获取分页的学生列表，每项包含答题统计、正确率、活跃度、预警等学情摘要")
    public ApiResponse<Map<String, Object>> getStudentList(
            @Parameter(description = "课程 ID（可选）") @RequestParam(required = false) String courseId,
            @Parameter(description = "页码（1-based）") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序字段: correctRate / answers / name / lastActive") @RequestParam(defaultValue = "correctRate") String sortBy,
            @Parameter(description = "排序方向: asc / desc") @RequestParam(defaultValue = "asc") String sortDir) {
        Map<String, Object> result = dashboardService.getStudentList(courseId, page, size, sortBy, sortDir);
        return ApiResponse.success(result, "获取学生列表成功");
    }

    /**
     * 获取薄弱知识点列表
     */
    @GetMapping("/weak-knowledge")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "薄弱知识点", description = "获取全班正确率低于阈值的知识点列表，支持按课程筛选和最小学生数过滤")
    public ApiResponse<List<WeakKnowledgeDto>> getWeakKnowledgePoints(
            @Parameter(description = "课程 ID（可选）") @RequestParam(required = false) String courseId,
            @Parameter(description = "薄弱阈值（百分比，默认60）") @RequestParam(defaultValue = "60") double threshold,
            @Parameter(description = "最少涉及学生数（默认3）") @RequestParam(defaultValue = "3") int minStudents) {
        List<WeakKnowledgeDto> weakPoints = dashboardService.getWeakKnowledgePoints(courseId, threshold, minStudents);
        return ApiResponse.success(weakPoints, "获取薄弱知识点成功");
    }

    /**
     * 获取每日教学简报
     */
    @GetMapping("/daily-brief")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "每日简报", description = "获取今日教学简报，含活跃度、答题统计、预警、错题复盘、进步最快学生等")
    public ApiResponse<DailyBriefDto> getDailyBrief(
            @Parameter(description = "课程 ID（可选）") @RequestParam(required = false) String courseId) {
        DailyBriefDto brief = dashboardService.getDailyBrief(courseId);
        return ApiResponse.success(brief, "获取每日简报成功");
    }

    /**
     * 获取策略建议
     */
    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "策略建议", description = "基于数据分析自动生成教学策略建议，含薄弱知识点、活跃度、预警等多维度建议")
    public ApiResponse<List<StrategySuggestionDto>> getStrategySuggestions(
            @Parameter(description = "课程 ID（可选）") @RequestParam(required = false) String courseId,
            @Parameter(description = "返回条数上限") @RequestParam(defaultValue = "10") int limit) {
        List<StrategySuggestionDto> suggestions = dashboardService.getStrategySuggestions(courseId, limit);
        return ApiResponse.success(suggestions, "获取策略建议成功");
    }
}
