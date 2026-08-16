package com.edumentor.classroom.controller;

import com.edumentor.classroom.dto.MootCourtJudgmentRequest;
import com.edumentor.classroom.entity.enums.MootCourtPhase;
import com.edumentor.classroom.service.MootCourtService;
import com.edumentor.common.response.ApiResponse;
import com.edumentor.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟法庭 API（设计文档 moot-court-design.html v1.1）。
 * <p>学生端功能：启动/查询法庭会话、庭审对话（法官发言）、提交判决、生成分析报告。</p>
 */
@RestController
@RequestMapping("/api/moot-courts")
@Tag(name = "模拟法庭", description = "课前/课后双阶段模拟法庭（学生扮演法官，AI 扮演原被告）")
public class MootCourtController {

    private static final Logger log = LoggerFactory.getLogger(MootCourtController.class);

    private final MootCourtService mootCourtService;

    public MootCourtController(MootCourtService mootCourtService) {
        this.mootCourtService = mootCourtService;
    }

    /** 启动（或获取）法庭会话：首次进入触发案例生成 + 开庭 */
    @Operation(summary = "启动法庭会话", description = "获取或创建法庭会话；首次进入时按课堂知识点生成案件并开庭")
    @PostMapping("/{classroomId}/start")
    public ApiResponse<Map<String, Object>> start(@PathVariable UUID classroomId,
                                                  @RequestParam(defaultValue = "PRE") MootCourtPhase phase,
                                                  Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(mootCourtService.start(classroomId, studentId, phase));
    }

    /** 查询法庭会话（含案件 + 庭审消息） */
    @Operation(summary = "查询法庭会话")
    @GetMapping("/{classroomId}/session")
    public ApiResponse<Map<String, Object>> session(@PathVariable UUID classroomId,
                                                    @RequestParam(defaultValue = "PRE") MootCourtPhase phase,
                                                    Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(mootCourtService.getSession(classroomId, studentId, phase));
    }

    /** 法官（学生）发言 → AI 原/被告回应 */
    @Operation(summary = "庭审对话", description = "以法官身份发言，AI 自动以原告/被告身份回应")
    @PostMapping("/{classroomId}/message")
    public ApiResponse<Map<String, Object>> message(@PathVariable UUID classroomId,
                                                    @RequestParam(defaultValue = "PRE") MootCourtPhase phase,
                                                    @RequestBody Map<String, Object> body,
                                                    Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        String content = body.get("content") != null ? body.get("content").toString() : "";
        return ApiResponse.success(mootCourtService.sendMessage(classroomId, studentId, phase, content));
    }

    /** 进入下一庭审环节（书记员播报 + 对应角色自动发言） */
    @Operation(summary = "进入下一庭审环节")
    @PostMapping("/{classroomId}/next-stage")
    public ApiResponse<Map<String, Object>> nextStage(@PathVariable UUID classroomId,
                                                      @RequestParam(defaultValue = "PRE") MootCourtPhase phase,
                                                      Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(mootCourtService.nextStage(classroomId, studentId, phase));
    }

    /** 提交判决（结构化：SUPPORT/REJECT/PARTIAL + 理由） */
    @Operation(summary = "提交判决")
    @PostMapping("/{classroomId}/judgment")
    public ApiResponse<Map<String, Object>> judgment(@PathVariable UUID classroomId,
                                                     @RequestBody MootCourtJudgmentRequest request,
                                                     Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(mootCourtService.submitJudgment(classroomId, studentId, request));
    }

    /** 生成分析报告（需 PRE + POST 两份判决齐全） */
    @Operation(summary = "生成分析报告", description = "对比课前/课后两次判决生成学习分析报告")
    @PostMapping("/{classroomId}/report")
    public ApiResponse<Map<String, Object>> generateReport(@PathVariable UUID classroomId,
                                                           Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(mootCourtService.generateReport(classroomId, studentId));
    }

    /** 获取分析报告（含两份判决 + 报告正文） */
    @Operation(summary = "获取分析报告")
    @GetMapping("/{classroomId}/report")
    public ApiResponse<Map<String, Object>> report(@PathVariable UUID classroomId,
                                                   Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(mootCourtService.getReport(classroomId, studentId));
    }

    // ═══════════════════════════════════════════════════════════

    private UUID getUserIdFromPrincipal(Principal principal) {
        if (principal != null && principal.getName() != null) {
            try {
                return UUID.fromString(principal.getName());
            } catch (Exception ignored) {
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new IllegalArgumentException("无法识别当前用户身份");
    }
}
