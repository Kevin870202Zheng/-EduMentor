package com.edumentor.alert.controller;

import com.edumentor.alert.dto.AlertDto;
import com.edumentor.alert.dto.AlertHandleRequest;
import com.edumentor.alert.dto.AlertStatisticsDto;
import com.edumentor.alert.service.AlertService;
import com.edumentor.common.response.ApiResponse;
import com.edumentor.common.response.PaginatedResponse;
import com.edumentor.entity.enums.AlertSeverity;
import com.edumentor.entity.enums.AlertType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 预警系统控制器 — 提供预警管理相关的 REST API。
 * <p>
 * 包含预警列表查询、详情查看、状态标记、处理操作和聚合统计。
 * 教师角色可处理预警，学生角色只能查看自己的预警。
 * </p>
 *
 * <h3>权限说明</h3>
 * <ul>
 *   <li>预警查看：STUDENT（自己）/ TEACHER（所有）/ ADMIN（所有）</li>
 *   <li>预警处理：TEACHER / ADMIN</li>
 *   <li>预警统计：TEACHER / ADMIN</li>
 * </ul>
 *
 * @author EduMentor Team
 */
@RestController
@RequestMapping("/api/alerts")
@Tag(name = "预警管理", description = "多级预警系统 — 预警查询、处理、统计")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // ══════════════════════════════════════════════════════════════
    //  预警查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取预警列表（支持分页、筛选）。
     */
    @GetMapping
    @Operation(summary = "预警列表", description = "按条件分页查询预警记录，支持按级别、类型、状态筛选")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功返回预警分页列表",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
    ))
    public ApiResponse<PaginatedResponse<AlertDto>> getAlerts(
            @Parameter(description = "页码（1-based）") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "按严重级别过滤") @RequestParam(required = false) AlertSeverity severity,
            @Parameter(description = "按预警类型过滤") @RequestParam(required = false) AlertType type,
            @Parameter(description = "按处理状态过滤（true=已处理, false=未处理）")
            @RequestParam(required = false) Boolean resolved) {

        Page<AlertDto> alertPage = alertService.getAlerts(page, size, severity, type, resolved);
        return PaginatedResponse.of(alertPage).toApiResponse();
    }

    /**
     * 获取预警详情。
     */
    @GetMapping("/{id}")
    @Operation(summary = "预警详情", description = "根据 ID 获取单条预警的详细信息")
    public ApiResponse<AlertDto> getAlertById(
            @Parameter(description = "预警 ID") @PathVariable UUID id) {
        AlertDto alert = alertService.getAlertById(id);
        return ApiResponse.success(alert);
    }

    /**
     * 获取学生的预警列表。
     */
    @GetMapping("/student/{studentId}")
    @Operation(summary = "学生预警列表", description = "获取指定学生的所有预警记录（分页）")
    public ApiResponse<PaginatedResponse<AlertDto>> getAlertsByStudent(
            @Parameter(description = "学生 ID") @PathVariable UUID studentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AlertDto> alertPage = alertService.getAlertsByStudent(studentId, page, size);
        return PaginatedResponse.of(alertPage).toApiResponse();
    }

    /**
     * 获取所有未处理的预警（按级别优先级排序）。
     */
    @GetMapping("/unresolved")
    @Operation(summary = "未处理预警", description = "获取所有未处理的预警，按级别优先级降序排列")
    public ApiResponse<List<AlertDto>> getUnresolvedAlerts() {
        List<AlertDto> alerts = alertService.getUnresolvedAlerts();
        return ApiResponse.success(alerts);
    }

    /**
     * 获取学生的未处理预警。
     */
    @GetMapping("/student/{studentId}/unresolved")
    @Operation(summary = "学生未处理预警", description = "获取指定学生当前未处理的预警列表")
    public ApiResponse<List<AlertDto>> getUnresolvedAlertsByStudent(
            @Parameter(description = "学生 ID") @PathVariable UUID studentId) {
        List<AlertDto> alerts = alertService.getUnresolvedAlertsByStudent(studentId);
        return ApiResponse.success(alerts);
    }

    /**
     * 获取教师的未处理预警。
     */
    @GetMapping("/teacher/{teacherId}/unresolved")
    @Operation(summary = "教师预警看板", description = "获取指定教师名下所有学生的未处理预警")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<List<AlertDto>> getUnresolvedAlertsByTeacher(
            @Parameter(description = "教师 ID") @PathVariable UUID teacherId) {
        List<AlertDto> alerts = alertService.getUnresolvedAlertsByTeacher(teacherId);
        return ApiResponse.success(alerts);
    }

    // ══════════════════════════════════════════════════════════════
    //  预警处理
    // ══════════════════════════════════════════════════════════════

    /**
     * 标记预警为已读。
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "标记已读", description = "将指定预警标记为已读状态")
    public ApiResponse<AlertDto> markAsRead(
            @Parameter(description = "预警 ID") @PathVariable UUID id) {
        AlertDto alert = alertService.markAsRead(id);
        return ApiResponse.success(alert, "已标记为已读");
    }

    /**
     * 处理预警（解决/忽略/升级）。
     * <p>
     * action 参数：
     * <ul>
     *   <li><b>RESOLVE</b> — 解决预警</li>
     *   <li><b>DISMISS</b> — 忽略预警</li>
     *   <li><b>ESCALATE</b> — 升级预警级别</li>
     * </ul>
     * </p>
     */
    @PutMapping("/{id}/handle")
    @Operation(summary = "处理预警", description = "教师处理预警：解决(RESOLVE)/忽略(DISMISS)/升级(ESCALATE)")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<AlertDto> handleAlert(
            @Parameter(description = "预警 ID") @PathVariable UUID id,
            @Valid @RequestBody AlertHandleRequest request) {
        request.setAlertId(id);
        AlertDto alert = alertService.handleAlert(request);
        String actionMsg = switch (request.getAction().toUpperCase()) {
            case "RESOLVE" -> "预警已解决";
            case "DISMISS" -> "预警已忽略";
            case "ESCALATE" -> "预警已升级";
            default -> "预警已处理";
        };
        return ApiResponse.success(alert, actionMsg);
    }

    /**
     * 批量处理预警。
     */
    @PostMapping("/batch-handle")
    @Operation(summary = "批量处理预警", description = "批量处理多条预警（解决/忽略/升级）")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Integer> batchHandleAlerts(@Valid @RequestBody AlertHandleRequest request) {
        int count = alertService.batchHandleAlerts(request);
        return ApiResponse.success(count, "成功处理 " + count + " 条预警");
    }

    /**
     * 更新预警备注。
     */
    @PutMapping("/{id}/note")
    @Operation(summary = "更新备注", description = "教师补充或修改预警的处理备注")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<AlertDto> updateAlertNote(
            @Parameter(description = "预警 ID") @PathVariable UUID id,
            @Parameter(description = "处理备注") @RequestBody String note) {
        AlertDto alert = alertService.updateAlertNote(id, note);
        return ApiResponse.success(alert, "备注已更新");
    }

    // ══════════════════════════════════════════════════════════════
    //  预警统计
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取预警统计信息。
     */
    @GetMapping("/statistics")
    @Operation(summary = "预警统计", description = "获取预警系统的聚合统计信息（用于 Dashboard 展示）")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<AlertStatisticsDto> getAlertStatistics() {
        AlertStatisticsDto stats = alertService.getAlertStatistics();
        return ApiResponse.success(stats);
    }

    /**
     * 获取学生的预警统计。
     */
    @GetMapping("/student/{studentId}/statistics")
    @Operation(summary = "学生预警统计", description = "获取指定学生的预警统计信息")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<AlertStatisticsDto> getStudentAlertStatistics(
            @Parameter(description = "学生 ID") @PathVariable UUID studentId) {
        AlertStatisticsDto stats = alertService.getStudentAlertStatistics(studentId);
        return ApiResponse.success(stats);
    }

    // ══════════════════════════════════════════════════════════════
    //  系统管理
    // ══════════════════════════════════════════════════════════════

    /**
     * 清理过期预警（管理员接口）。
     */
    @PostMapping("/clean-expired")
    @Operation(summary = "清理过期预警", description = "清理所有已过期的预警记录（管理员操作）")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Integer> cleanExpiredAlerts() {
        int count = alertService.cleanExpiredAlerts();
        return ApiResponse.success(count, "已清理 " + count + " 条过期预警");
    }
}
