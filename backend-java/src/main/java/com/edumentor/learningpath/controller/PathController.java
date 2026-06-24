package com.edumentor.learningpath.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.learningpath.dto.*;
import com.edumentor.learningpath.service.PathService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 学习路径规划控制器 — 提供个性化学习路径的规划、查询、状态管理和智能适配接口。
 * <p>
 * 所有接口路径以 {@code /api/paths/} 开头。
 * </p>
 *
 * @author EduMentor Team
 */
@RestController
@RequestMapping("/api/paths")
@Tag(name = "学习路径规划", description = "个性化学习路径规划、知识图谱、智能适配")
public class PathController {

    private static final Logger log = LoggerFactory.getLogger(PathController.class);

    private final PathService pathService;

    public PathController(PathService pathService) {
        this.pathService = pathService;
    }

    /**
     * POST /api/paths/plan — 规划个性化学习路径。
     * <p>
     * 根据学生的学情信息、课程知识图谱和前置关系，
     * 使用 Kahn 拓扑排序自动生成最优的知识点学习顺序。
     * </p>
     *
     * @param request 路径规划请求体
     * @return 创建好的学习路径
     */
    @PostMapping("/plan")
    @Operation(summary = "规划学习路径", description = "根据学生学情和课程知识图谱自动生成个性化学习路径")
    public ApiResponse<LearningPathDto> planPath(@Valid @RequestBody PathPlanRequest request) {
        log.info("规划学习路径: studentId={}, courseId={}, name={}",
                request.getStudentId(), request.getCourseId(), request.getName());
        LearningPathDto result = pathService.planPath(request);
        return ApiResponse.success(result, "学习路径规划成功");
    }

    /**
     * GET /api/paths/{id} — 获取学习路径详情（含节点列表）。
     *
     * @param id 路径 ID
     * @return 路径详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取路径详情", description = "获取学习路径的详细信息，包括所有节点")
    public ApiResponse<LearningPathDto> getPath(@PathVariable UUID id) {
        LearningPathDto result = pathService.getPath(id);
        return ApiResponse.success(result);
    }

    /**
     * GET /api/paths — 获取指定学生的所有学习路径。
     *
     * @param studentId 学生用户 ID（查询参数）
     * @return 路径列表
     */
    @GetMapping
    @Operation(summary = "获取学生路径列表", description = "获取指定学生的所有学习路径")
    public ApiResponse<List<LearningPathDto>> getStudentPaths(@RequestParam UUID studentId) {
        List<LearningPathDto> paths = pathService.getStudentPaths(studentId);
        return ApiResponse.success(paths);
    }

    /**
     * GET /api/paths/active — 获取学生当前活跃路径。
     *
     * @param studentId 学生用户 ID
     * @return 活跃路径（可能为空）
     */
    @GetMapping("/active")
    @Operation(summary = "获取当前活跃路径", description = "获取学生当前正在学习中的活跃路径")
    public ApiResponse<LearningPathDto> getActivePath(@RequestParam UUID studentId) {
        return pathService.getActivePath(studentId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null, "暂无活跃路径"));
    }

    /**
     * POST /api/paths/{id}/activate — 激活路径。
     * <p>
     * 将 DRAFT 或 PAUSED 状态的路径激活为 ACTIVE。
     * 激活时自动将第一个 PENDING 节点设为 IN_PROGRESS。
     * </p>
     *
     * @param id 路径 ID
     * @return 更新后的路径
     */
    @PostMapping("/{id}/activate")
    @Operation(summary = "激活路径", description = "将 DRAFT 或 PAUSED 状态的路径激活为 ACTIVE")
    public ApiResponse<LearningPathDto> activatePath(@PathVariable UUID id) {
        LearningPathDto result = pathService.activatePath(id);
        return ApiResponse.success(result, "路径已激活");
    }

    /**
     * POST /api/paths/{id}/pause — 暂停路径。
     * <p>
     * 将 ACTIVE 状态的路径暂停为 PAUSED。
     * </p>
     *
     * @param id 路径 ID
     * @return 更新后的路径
     */
    @PostMapping("/{id}/pause")
    @Operation(summary = "暂停路径", description = "暂停 ACTIVE 状态的路径")
    public ApiResponse<LearningPathDto> pausePath(@PathVariable UUID id) {
        LearningPathDto result = pathService.pausePath(id);
        return ApiResponse.success(result, "路径已暂停");
    }

    /**
     * POST /api/paths/{id}/complete — 完成路径。
     * <p>
     * 将所有未完成节点标记为 SKIPPED，路径状态变为 COMPLETED。
     * </p>
     *
     * @param id 路径 ID
     * @return 更新后的路径
     */
    @PostMapping("/{id}/complete")
    @Operation(summary = "完成路径", description = "手动将路径标记为已完成")
    public ApiResponse<LearningPathDto> completePath(@PathVariable UUID id) {
        LearningPathDto result = pathService.completePath(id);
        return ApiResponse.success(result, "路径已完成");
    }

    /**
     * PUT /api/paths/node/progress — 更新节点进度状态。
     * <p>
     * 支持 IN_PROGRESS / COMPLETED / SKIPPED 状态变更。
     * 节点完成时自动重算路径进度，全部完成时自动标记路径完成。
     * </p>
     *
     * @param request 进度更新请求
     * @return 更新后的路径
     */
    @PutMapping("/node/progress")
    @Operation(summary = "更新节点进度", description = "更新路径中某个节点的学习进度状态")
    public ApiResponse<LearningPathDto> updateNodeProgress(
            @Valid @RequestBody PathProgressUpdateRequest request) {
        LearningPathDto result = pathService.updateNodeProgress(request);
        String msg = switch (request.getStatus()) {
            case "IN_PROGRESS" -> "开始学习节点";
            case "COMPLETED" -> "节点学习完成";
            case "SKIPPED" -> "节点已跳过";
            default -> "节点状态已更新";
        };
        return ApiResponse.success(result, msg);
    }

    /**
     * GET /api/paths/{id}/next-node — 获取路径中的下一个待学习节点。
     *
     * @param id 路径 ID
     * @return 下一个节点（可能为空）
     */
    @GetMapping("/{id}/next-node")
    @Operation(summary = "获取下个节点", description = "获取路径中下一个待学习的知识点节点")
    public ApiResponse<LearningPathNodeDto> getNextNode(@PathVariable UUID id) {
        return pathService.getNextNode(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null, "所有节点已完成"));
    }

    /**
     * POST /api/paths/adapt — 智能适配学习路径。
     * <p>
     * 根据最新学情数据动态调整路径节点顺序或内容。
     * 支持 REORDER / SHORTEN / EXPAND / FOCUS_WEAK 四种策略。
     * </p>
     *
     * @param request 适配请求
     * @return 适配后的路径
     */
    @PostMapping("/adapt")
    @Operation(summary = "智能适配路径", description = "根据最新学情数据动态调整学习路径")
    public ApiResponse<LearningPathDto> adaptPath(@Valid @RequestBody PathAdaptRequest request) {
        LearningPathDto result = pathService.adaptPath(request);
        return ApiResponse.success(result, "路径适配完成");
    }

    /**
     * GET /api/paths/knowledge-graph/{courseId} — 获取课程的知识图谱。
     * <p>
     * 返回知识点的节点和关系数据，供前端渲染可视化图谱。
     * 使用 EntityManager 跨模块查询 knowledge_points 和 knowledge_relations 表。
     * </p>
     *
     * @param courseId  课程 ID
     * @param studentId 学生 ID（可选，用于标记掌握状态和薄弱点）
     * @return 知识图谱结构
     */
    @GetMapping("/knowledge-graph/{courseId}")
    @Operation(summary = "获取知识图谱", description = "获取指定课程的知识图谱结构（节点+关系）")
    public ApiResponse<KnowledgeGraphDto> getKnowledgeGraph(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID studentId) {
        KnowledgeGraphDto graph = pathService.getKnowledgeGraph(courseId, studentId);
        return ApiResponse.success(graph);
    }
}
