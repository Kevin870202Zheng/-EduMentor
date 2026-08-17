package com.edumentor.arbitration.controller;

import com.edumentor.arbitration.dto.ArbitrationAwardRequest;
import com.edumentor.arbitration.entity.enums.ArbitrationPhase;
import com.edumentor.arbitration.service.ArbitrationService;
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
 * 仲裁人案例分析 API（设计文档 learning-directory-arbitration-design.html v1.0 §4.8）。
 * <p>加载课堂学习模块：每知识点两阶段（PRE 课前 / POST 课后），学生扮演仲裁人，AI 扮演普通老百姓原/被告。</p>
 */
@RestController
@RequestMapping("/api/arbitrations")
@Tag(name = "模拟仲裁", description = "课前/课后双阶段仲裁人案例分析（学生扮演仲裁人，AI 扮演普通老百姓原被告）")
public class ArbitrationController {

    private static final Logger log = LoggerFactory.getLogger(ArbitrationController.class);

    private final ArbitrationService arbitrationService;

    public ArbitrationController(ArbitrationService arbitrationService) {
        this.arbitrationService = arbitrationService;
    }

    /** 启动（或获取）仲裁会话：PRE 首次进入生成案件 + 开庭；POST 复用 PRE 案件 + 掌握度准入 */
    @Operation(summary = "启动仲裁会话", description = "PRE 按知识点生成案件并开庭；POST 复用课前案件，需掌握度≥50%")
    @PostMapping("/{kpId}/start")
    public ApiResponse<Map<String, Object>> start(@PathVariable UUID kpId,
                                                  @RequestParam(defaultValue = "PRE") ArbitrationPhase phase,
                                                  Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(arbitrationService.start(kpId, studentId, phase));
    }

    /** 查询仲裁会话（含案件 + 庭审消息 + 裁决） */
    @Operation(summary = "查询仲裁会话")
    @GetMapping("/{kpId}/session")
    public ApiResponse<Map<String, Object>> session(@PathVariable UUID kpId,
                                                    @RequestParam(defaultValue = "PRE") ArbitrationPhase phase,
                                                    Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(arbitrationService.getSession(kpId, studentId, phase));
    }

    /** 仲裁人（学生）发言 → AI（老百姓原/被告）回应 */
    @Operation(summary = "仲裁庭对话", description = "以仲裁人身份发言，AI 自动以老百姓原/被告身份回应")
    @PostMapping("/{kpId}/message")
    public ApiResponse<Map<String, Object>> message(@PathVariable UUID kpId,
                                                    @RequestParam(defaultValue = "PRE") ArbitrationPhase phase,
                                                    @RequestBody Map<String, Object> body,
                                                    Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        String content = body.get("content") != null ? body.get("content").toString() : "";
        return ApiResponse.success(arbitrationService.sendMessage(kpId, studentId, phase, content));
    }

    /** 进入下一仲裁环节（记录员播报 + 对应角色自动发言） */
    @Operation(summary = "进入下一仲裁环节")
    @PostMapping("/{kpId}/next-stage")
    public ApiResponse<Map<String, Object>> nextStage(@PathVariable UUID kpId,
                                                      @RequestParam(defaultValue = "PRE") ArbitrationPhase phase,
                                                      Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(arbitrationService.nextStage(kpId, studentId, phase));
    }

    /** 提交裁决书（结构化：SUPPORT/REJECT/PARTIAL + 理由） */
    @Operation(summary = "提交裁决书")
    @PostMapping("/{kpId}/award")
    public ApiResponse<Map<String, Object>> award(@PathVariable UUID kpId,
                                                  @RequestBody ArbitrationAwardRequest request,
                                                  Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(arbitrationService.submitAward(kpId, studentId, request));
    }

    /** 生成分析报告（需 PRE + POST 两份裁决齐全） */
    @Operation(summary = "生成分析报告", description = "对比课前/课后两次裁决生成学习分析报告")
    @PostMapping("/{kpId}/report")
    public ApiResponse<Map<String, Object>> generateReport(@PathVariable UUID kpId,
                                                           Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(arbitrationService.generateReport(kpId, studentId));
    }

    /** 获取分析报告（含案件 + 两份裁决 + 报告正文） */
    @Operation(summary = "获取分析报告")
    @GetMapping("/{kpId}/report")
    public ApiResponse<Map<String, Object>> report(@PathVariable UUID kpId,
                                                   Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(arbitrationService.getReport(kpId, studentId));
    }

    /** 查询仲裁状态（供学习页入口卡片展示三态） */
    @Operation(summary = "查询仲裁状态", description = "返回 PRE/POST 是否已裁决、报告是否可生成")
    @GetMapping("/{kpId}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable UUID kpId,
                                                   Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(arbitrationService.getStatus(kpId, studentId));
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
