package com.edumentor.learningpath.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.learningpath.dto.*;
import com.edumentor.learningpath.service.AiPlanService;
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
    private final AiPlanService aiPlanService;

    public PathController(PathService pathService, AiPlanService aiPlanService) {
        this.pathService = pathService;
        this.aiPlanService = aiPlanService;
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
     * GET /api/paths/templates — 获取课程可用的预设路径模板列表。
     *
     * @param courseId 课程 ID
     * @return 模板列表（推荐卡片）
     */
    @GetMapping("/templates")
    @Operation(summary = "获取路径模板列表", description = "获取课程下可见的预设路径模板（课程考试/纠纷解决/兴趣拓展/师范生备课）")
    public ApiResponse<List<PathTemplateDto>> getTemplates(@RequestParam UUID courseId) {
        List<PathTemplateDto> result = pathService.getTemplates(courseId);
        return ApiResponse.success(result);
    }

    /**
     * GET /api/paths/templates/{id}/preview — 预览模板节点内容。
     * <p>
     * 师范生备课（TEACHING）模板需传 stage（目标学段），返回按"课"分组的预览。
     * </p>
     *
     * @param id       模板 ID
     * @param stage    目标学段（可选，TEACHING 必填）
     * @param themeIds 主题过滤（可选）
     * @return 模板预览
     */
    @GetMapping("/templates/{id}/preview")
    @Operation(summary = "预览路径模板", description = "预览模板节点内容；师范生备课模板按学段/主题动态计算并分课")
    public ApiResponse<PathTemplatePreviewDto> previewTemplate(
            @PathVariable UUID id,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) List<UUID> themeIds) {
        PathTemplatePreviewDto result = pathService.previewTemplate(id, stage, themeIds);
        return ApiResponse.success(result);
    }

    /**
     * POST /api/paths/from-template — 从模板生成学生路径（source=TEMPLATE）。
     *
     * @param request 生成请求（含 studentId/courseId/templateId/stage）
     * @return 生成的 DRAFT 路径
     */
    @PostMapping("/from-template")
    @Operation(summary = "从模板生成路径", description = "复制模板节点生成学生 DRAFT 路径，模板改动不影响已生成路径")
    public ApiResponse<LearningPathDto> createPathFromTemplate(@Valid @RequestBody FromTemplateRequest request) {
        LearningPathDto result = pathService.createPathFromTemplate(request);
        return ApiResponse.success(result, "模板路径生成成功");
    }

    /**
     * POST /api/paths/custom — 手动勾选创建路径（source=CUSTOM）。
     *
     * @param request 创建请求（含有序知识点 ID 列表）
     * @return 创建的 DRAFT 路径
     */
    @PostMapping("/custom")
    @Operation(summary = "手动勾选创建路径", description = "按学生勾选的知识点顺序生成自定义路径（CUSTOM）")
    public ApiResponse<LearningPathDto> createCustomPath(@Valid @RequestBody CustomPathRequest request) {
        LearningPathDto result = pathService.createCustomPath(request);
        return ApiResponse.success(result, "自定义路径创建成功");
    }

    /**
     * POST /api/paths/{id}/nodes — 向路径追加节点（手动编辑，source 置 CUSTOM）。
     *
     * @param id      路径 ID
     * @param request 追加请求
     * @return 更新后的路径
     */
    @PostMapping("/{id}/nodes")
    @Operation(summary = "追加路径节点", description = "向路径追加知识点节点，可指定插入位置")
    public ApiResponse<LearningPathDto> addPathNode(@PathVariable UUID id,
                                                    @Valid @RequestBody AddPathNodeRequest request) {
        LearningPathDto result = pathService.addPathNode(id, request);
        return ApiResponse.success(result, "节点已追加");
    }

    /**
     * DELETE /api/paths/{id}/nodes/{nodeId} — 移除路径节点。
     *
     * @param id     路径 ID
     * @param nodeId 路径节点 ID
     * @return 更新后的路径
     */
    @DeleteMapping("/{id}/nodes/{nodeId}")
    @Operation(summary = "移除路径节点", description = "从路径中删除指定节点并重排顺序")
    public ApiResponse<LearningPathDto> removePathNode(@PathVariable UUID id, @PathVariable UUID nodeId) {
        LearningPathDto result = pathService.removePathNode(id, nodeId);
        return ApiResponse.success(result, "节点已移除");
    }

    /**
     * PUT /api/paths/{id}/nodes/reorder — 调整路径节点顺序。
     *
     * @param id      路径 ID
     * @param request 新顺序（节点 ID 完整列表）
     * @return 更新后的路径
     */
    @PutMapping("/{id}/nodes/reorder")
    @Operation(summary = "调整路径节点顺序", description = "按给定节点 ID 顺序重排路径节点")
    public ApiResponse<LearningPathDto> reorderPathNodes(@PathVariable UUID id,
                                                         @Valid @RequestBody ReorderNodesRequest request) {
        LearningPathDto result = pathService.reorderPathNodes(id, request);
        return ApiResponse.success(result, "节点顺序已调整");
    }

    /**
     * POST /api/paths/ai-plan/start — 开启 AI 对话规划会话。
     *
     * @param request 开启请求（含学习目标）
     * @return 会话响应（sessionId + 首轮回复 + 候选知识点）
     */
    @PostMapping("/ai-plan/start")
    @Operation(summary = "开启 AI 规划会话", description = "RAG 检索候选知识点 + LLM 首轮追问/建议，返回 sessionId")
    public ApiResponse<AiPlanResponse> startAiPlan(@Valid @RequestBody AiPlanStartRequest request) {
        AiPlanResponse result = aiPlanService.start(request);
        return ApiResponse.success(result, "AI 规划会话已开启");
    }

    /**
     * POST /api/paths/ai-plan/chat — AI 规划多轮对话。
     * <p>
     * generatePath=true 时 LLM 输出结构化路径 JSON 并落库 DRAFT 路径。
     * </p>
     *
     * @param request 对话请求
     * @return 会话响应（回复文本 + 可选生成的路径）
     */
    @PostMapping("/ai-plan/chat")
    @Operation(summary = "AI 规划对话", description = "多轮对话澄清需求；确认后生成结构化路径并落库 DRAFT")
    public ApiResponse<AiPlanResponse> chatAiPlan(@Valid @RequestBody AiPlanChatRequest request) {
        AiPlanResponse result = aiPlanService.chat(request);
        return ApiResponse.success(result);
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
