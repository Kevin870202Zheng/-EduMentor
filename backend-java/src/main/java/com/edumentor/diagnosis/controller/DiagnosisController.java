package com.edumentor.diagnosis.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.common.exception.UnauthorizedException;
import com.edumentor.diagnosis.dto.*;
import com.edumentor.diagnosis.service.DiagnosisService;
import com.edumentor.entity.enums.UserRole;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 学情诊断控制器 — 提供诊断分析、认知画像、雷达图、热力图等 REST API。
 *
 * <p>
 * 所有接口路径以 {@code /api/diagnosis/} 开头，返回统一的 {@link ApiResponse} 包装。
 * 学生用户只能查询自己的学情数据，教师和管理员可以查询任意学生的数据。
 * </p>
 */
@RestController
@RequestMapping("/api/diagnosis")
@Tag(name = "学情诊断", description = "学情诊断分析、认知画像、雷达图、热力图等")
public class DiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisController.class);

    private final DiagnosisService diagnosisService;
    private final UserRepository userRepository;

    public DiagnosisController(DiagnosisService diagnosisService, UserRepository userRepository) {
        this.diagnosisService = diagnosisService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/diagnosis/analyze
     * 学情诊断分析 — 根据学生作答记录进行全面的学情分析。
     *
     * @param studentId 学生 ID（查询参数，可选）
     * @param courseId  课程 ID（查询参数，可选）
     * @param daysBack  回溯天数（查询参数，默认 30）
     * @param currentUser 当前认证用户
     * @return 诊断分析结果
     */
    @GetMapping("/analyze")
    @Operation(summary = "学情诊断分析", description = "基于作答记录进行全面的学情诊断分析，" +
            "包含正确率统计、薄弱知识点识别、近期趋势和建议。")
    public ApiResponse<DiagnosisResponse> analyze(
            @Parameter(description = "学生 ID（不传则使用当前认证用户）")
            @RequestParam(required = false) UUID studentId,

            @Parameter(description = "课程 ID（可选，限定分析范围）")
            @RequestParam(required = false) UUID courseId,

            @Parameter(description = "回溯天数（默认 30，最大 365）")
            @RequestParam(defaultValue = "30") int daysBack,

            @AuthenticationPrincipal User currentUser) {

        UUID targetStudentId = resolveStudentId(studentId, currentUser);
        log.info("诊断分析请求: studentId={}, courseId={}, daysBack={}", targetStudentId, courseId, daysBack);

        DiagnosisResponse result = diagnosisService.diagnose(targetStudentId, courseId, daysBack);
        return ApiResponse.success(result, "诊断分析完成");
    }

    /**
     * GET /api/diagnosis/profile
     * 认知画像 — 构建学生各知识维度的掌握度画像。
     *
     * @param studentId   学生 ID（查询参数，可选）
     * @param currentUser 当前认证用户
     * @return 认知画像
     */
    @GetMapping("/profile")
    @Operation(summary = "认知画像", description = "构建学生各知识维度的掌握度画像，" +
            "包含知识点掌握详情、整体掌握度统计和雷达图数据。")
    public ApiResponse<CognitiveProfile> cognitiveProfile(
            @Parameter(description = "学生 ID（不传则使用当前认证用户）")
            @RequestParam(required = false) UUID studentId,

            @AuthenticationPrincipal User currentUser) {

        UUID targetStudentId = resolveStudentId(studentId, currentUser);
        log.info("认知画像请求: studentId={}", targetStudentId);

        CognitiveProfile profile = diagnosisService.buildCognitiveProfile(targetStudentId);
        return ApiResponse.success(profile, "认知画像构建完成");
    }

    /**
     * GET /api/diagnosis/radar
     * 雷达图 — 生成学生 6 维能力雷达图数据。
     *
     * @param studentId   学生 ID（查询参数，可选）
     * @param currentUser 当前认证用户
     * @return 雷达图数据
     */
    @GetMapping("/radar")
    @Operation(summary = "雷达图数据", description = "生成学生 6 大维度的能力分布雷达图数据，" +
            "用于前端可视化展示学生的能力结构。")
    public ApiResponse<RadarChartData> radarChart(
            @Parameter(description = "学生 ID（不传则使用当前认证用户）")
            @RequestParam(required = false) UUID studentId,

            @AuthenticationPrincipal User currentUser) {

        UUID targetStudentId = resolveStudentId(studentId, currentUser);
        log.info("雷达图请求: studentId={}", targetStudentId);

        RadarChartData radarData = diagnosisService.generateRadarChart(targetStudentId);
        return ApiResponse.success(radarData, "雷达图数据生成完成");
    }

    /**
     * GET /api/diagnosis/heatmap
     * 学习热力图 — 按天统计学习强度，生成热力图数据。
     *
     * @param studentId   学生 ID（查询参数，可选）
     * @param daysBack    回溯天数（查询参数，默认 30）
     * @param currentUser 当前认证用户
     * @return 热力图数据
     */
    @GetMapping("/heatmap")
    @Operation(summary = "学习热力图", description = "按天统计学生的学习活动强度，" +
            "包含每日答题数、正确率、学习时长和专注度等维度。")
    public ApiResponse<HeatMapData> heatMap(
            @Parameter(description = "学生 ID（不传则使用当前认证用户）")
            @RequestParam(required = false) UUID studentId,

            @Parameter(description = "回溯天数（默认 30，最大 365）")
            @RequestParam(defaultValue = "30") int daysBack,

            @AuthenticationPrincipal User currentUser) {

        UUID targetStudentId = resolveStudentId(studentId, currentUser);
        log.info("热力图请求: studentId={}, daysBack={}", targetStudentId, daysBack);

        HeatMapData heatMap = diagnosisService.generateHeatMap(targetStudentId, daysBack);
        return ApiResponse.success(heatMap, "热力图数据生成完成");
    }

    /**
     * GET /api/diagnosis/overview
     * 诊断总览 — 统一返回诊断分析、认知画像、雷达图、热力图数据。
     *
     * @param studentId   学生 ID（查询参数，可选）
     * @param daysBack    回溯天数（查询参数，默认 30）
     * @param currentUser 当前认证用户
     * @return 综合诊断数据
     */
    @GetMapping("/overview")
    @Operation(summary = "诊断总览", description = "一次性返回诊断分析、认知画像、雷达图、热力图等所有诊断数据，" +
            "减少前端请求次数。")
    public ApiResponse<Map<String, Object>> overview(
            @Parameter(description = "学生 ID（不传则使用当前认证用户）")
            @RequestParam(required = false) UUID studentId,

            @Parameter(description = "回溯天数（默认 30，最大 365）")
            @RequestParam(defaultValue = "30") int daysBack,

            @AuthenticationPrincipal User currentUser) {

        UUID targetStudentId = resolveStudentId(studentId, currentUser);
        log.info("诊断总览请求: studentId={}, daysBack={}", targetStudentId, daysBack);

        DiagnosisResponse diagnosis = diagnosisService.diagnose(targetStudentId, null, daysBack);
        CognitiveProfile profile = diagnosisService.buildCognitiveProfile(targetStudentId);
        RadarChartData radar = diagnosisService.generateRadarChart(targetStudentId);
        HeatMapData heatmap = diagnosisService.generateHeatMap(targetStudentId, daysBack);

        Map<String, Object> overview = Map.of(
                "diagnosis", diagnosis,
                "profile", profile,
                "radar", radar,
                "heatmap", heatmap
        );

        return ApiResponse.success(overview, "诊断总览获取成功");
    }

    /**
     * 解析目标学生 ID。
     * <p>
     * 如果请求中未指定 studentId，则使用当前认证用户的 ID（学生只能查自己）。
     * 如果指定了 studentId，检查当前用户角色：只有 TEACHER 或 ADMIN 可以查询其他学生的数据，
     * 普通 STUDENT 只能查询自己的数据。
     * </p>
     *
     * @param studentId   请求中的学生 ID
     * @param currentUser 当前认证用户
     * @return 实际要查询的学生 ID
     * @throws IllegalArgumentException 如果权限不足
     */
    private UUID resolveStudentId(UUID studentId, User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("未认证，请先登录");
        }
        if (studentId == null || studentId.equals(currentUser.getId())) {
            return currentUser.getId();
        }
        // 查询其他学生数据需要 TEACHER 或 ADMIN 角色
        UserRole role = currentUser.getRole();
        if (role == UserRole.TEACHER || role == UserRole.ADMIN) {
            return studentId;
        }
        throw new IllegalArgumentException("权限不足：学生只能查看自己的学情数据");
    }
}
