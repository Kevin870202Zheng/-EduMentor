package com.edumentor.course.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.common.response.PaginatedResponse;
import com.edumentor.course.dto.*;
import com.edumentor.course.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识管理控制器 — 课程、知识点、知识点关系 REST API。
 * <p>
 * 提供课程 CRUD、知识点 CRUD（含树形结构）、知识点关系管理和知识图谱查询功能。
 * 根据端点不同具有不同的权限控制：
 * <ul>
 *   <li>课程管理：创建/更新/删除需 TEACHER 或 ADMIN 角色</li>
 *   <li>知识点管理：创建/更新/删除需 TEACHER 或 ADMIN 角色</li>
 *   <li>知识图谱查询：任何认证用户均可访问</li>
 * </ul>
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/knowledge")
@Tag(name = "知识管理", description = "课程管理、知识点管理、知识点关系管理、知识图谱查询")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  课程 API (Course API)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 创建课程（教师/管理员权限）。
     */
    @PostMapping("/courses")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "创建课程", description = "创建新课程，需要教师或管理员权限")
    public ApiResponse<CourseDto> createCourse(
            @Valid @RequestBody CourseCreateRequest request,
            @AuthenticationPrincipal com.edumentor.user.entity.User principal) {
        UUID userId = principal.getId();
        log.info("REST 创建课程: name={}, userId={}", request.getName(), userId);
        CourseDto course = knowledgeService.createCourse(request, userId);
        return ApiResponse.success(course, "课程创建成功");
    }

    /**
     * 更新课程（教师/管理员权限）。
     */
    @PutMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "更新课程", description = "部分更新课程信息，需要教师或管理员权限")
    public ApiResponse<CourseDto> updateCourse(
            @Parameter(description = "课程 ID") @PathVariable UUID id,
            @Valid @RequestBody CourseUpdateRequest request) {
        CourseDto course = knowledgeService.updateCourse(id, request);
        return ApiResponse.success(course, "课程更新成功");
    }

    /**
     * 获取课程详情。
     */
    @GetMapping("/courses/{id}")
    @Operation(summary = "获取课程详情", description = "按 ID 获取课程详细信息")
    public ApiResponse<CourseDto> getCourse(
            @Parameter(description = "课程 ID") @PathVariable UUID id) {
        CourseDto course = knowledgeService.getCourse(id);
        return ApiResponse.success(course);
    }

    /**
     * 分页查询课程列表。
     */
    @GetMapping("/courses")
    @Operation(summary = "课程列表", description = "分页查询课程列表，支持学科、学段筛选、名称搜索和发布状态过滤")
    public ApiResponse<PaginatedResponse<CourseDto>> listCourses(
            @Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "学科筛选") @RequestParam(required = false) String subject,
            @Parameter(description = "名称关键字搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "仅显示已发布课程") @RequestParam(defaultValue = "false") boolean publishedOnly,
            @Parameter(description = "学段筛选（PRIMARY/JUNIOR/SENIOR/UNIVERSITY）") @RequestParam(required = false) String stage) {
        Page<CourseDto> coursePage = knowledgeService.listCourses(page, size, subject, keyword, publishedOnly, stage);
        return PaginatedResponse.of(coursePage).toApiResponse();
    }

    /**
     * 获取教师创建的课程列表。
     */
    @GetMapping("/courses/teacher/{teacherId}")
    @Operation(summary = "教师课程列表", description = "获取指定教师创建的所有课程")
    public ApiResponse<List<CourseDto>> listCoursesByTeacher(
            @Parameter(description = "教师用户 ID") @PathVariable UUID teacherId) {
        List<CourseDto> courses = knowledgeService.listCoursesByTeacher(teacherId);
        return ApiResponse.success(courses);
    }

    /**
     * 删除课程（教师/管理员权限）。
     */
    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "删除课程", description = "删除指定课程（会级联删除知识点和关系），需要教师或管理员权限")
    public ApiResponse<Void> deleteCourse(
            @Parameter(description = "课程 ID") @PathVariable UUID id) {
        knowledgeService.deleteCourse(id);
        return ApiResponse.ok();
    }

    /**
     * 发布/下架课程（教师/管理员权限）。
     */
    @PutMapping("/courses/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "发布/下架课程", description = "切换课程的发布状态")
    public ApiResponse<CourseDto> publishCourse(
            @Parameter(description = "课程 ID") @PathVariable UUID id,
            @Parameter(description = "发布状态") @RequestParam boolean published) {
        CourseDto course = knowledgeService.publishCourse(id, published);
        return ApiResponse.success(course, published ? "课程已发布" : "课程已下架");
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识点 API (Knowledge Point API)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 一键标注学段（教师端内容管理，PRD v4.0 §10.4 / §14）。
     * <p>将课程的 stage 回填到该课程下未标注的知识点，difficulty 近似回填 depth_level。</p>
     */
    @PostMapping("/courses/{courseId}/backfill-stage")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "一键标注学段", description = "将课程学段批量回填到该课程下未标注的知识点（幂等，不覆盖已标注结果），需要教师或管理员权限")
    public ApiResponse<Map<String, Object>> backfillCourseStage(
            @Parameter(description = "课程 ID") @PathVariable UUID courseId) {
        Map<String, Object> result = knowledgeService.backfillCourseStage(courseId);
        return ApiResponse.success(result, "学段标注完成");
    }

    /**
     * 创建知识点（教师/管理员权限）。
     */
    @PostMapping("/points")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "创建知识点", description = "创建新知识点，需要教师或管理员权限")
    public ApiResponse<KnowledgePointDto> createKnowledgePoint(
            @Valid @RequestBody KnowledgePointCreateRequest request) {
        KnowledgePointDto kp = knowledgeService.createKnowledgePoint(request);
        return ApiResponse.success(kp, "知识点创建成功");
    }

    /**
     * 更新知识点（教师/管理员权限）。
     */
    @PutMapping("/points/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "更新知识点", description = "部分更新知识点信息，需要教师或管理员权限")
    public ApiResponse<KnowledgePointDto> updateKnowledgePoint(
            @Parameter(description = "知识点 ID") @PathVariable UUID id,
            @Valid @RequestBody KnowledgePointUpdateRequest request) {
        KnowledgePointDto kp = knowledgeService.updateKnowledgePoint(id, request);
        return ApiResponse.success(kp, "知识点更新成功");
    }

    /**
     * 获取知识点详情。
     */
    @GetMapping("/points/{id}")
    @Operation(summary = "获取知识点详情", description = "按 ID 获取知识点详细信息")
    public ApiResponse<KnowledgePointDto> getKnowledgePoint(
            @Parameter(description = "知识点 ID") @PathVariable UUID id) {
        KnowledgePointDto kp = knowledgeService.getKnowledgePoint(id);
        return ApiResponse.success(kp);
    }

    /**
     * 按课程查询知识点列表。
     */
    @GetMapping("/courses/{courseId}/points")
    @Operation(summary = "课程知识点列表", description = "查询指定课程的所有知识点（按排序序号的扁平列表）")
    public ApiResponse<List<KnowledgePointDto>> listKnowledgePointsByCourse(
            @Parameter(description = "课程 ID") @PathVariable UUID courseId) {
        List<KnowledgePointDto> kps = knowledgeService.listKnowledgePointsByCourse(courseId);
        return ApiResponse.success(kps);
    }

    /**
     * 获取课程的知识点树形结构。
     */
    @GetMapping("/courses/{courseId}/points/tree")
    @Operation(summary = "知识点树形结构", description = "获取指定课程的知识点树形结构（含层级信息）")
    public ApiResponse<List<KnowledgeService.KnowledgePointTreeNode>> getKnowledgePointTree(
            @Parameter(description = "课程 ID") @PathVariable UUID courseId) {
        List<KnowledgeService.KnowledgePointTreeNode> tree =
                knowledgeService.getKnowledgePointTree(courseId);
        return ApiResponse.success(tree);
    }

    /**
     * AI 生成/更新课程知识点树结构（教师/管理员权限）。
     */
    @PostMapping("/courses/{courseId}/points/tree/generate")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "AI 生成树结构", description = "利用 AI 生成课程知识点的编→卷→章→节树状结构，支持增量更新")
    public ApiResponse<TreeGenerateResult> generateTreeStructure(
            @Parameter(description = "课程 ID") @PathVariable UUID courseId,
            @Valid @RequestBody TreeGenerateRequest request) {
        TreeGenerateResult result = knowledgeService.generateTreeStructure(courseId, request);
        return ApiResponse.success(result, "树结构生成成功");
    }

    /**
     * 移动知识点（教师/管理员权限）。
     */
    @PutMapping("/points/{id}/move")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "移动知识点", description = "将知识点移动到新的父节点或调整排序位置")
    public ApiResponse<KnowledgePointDto> moveKnowledgePoint(
            @Parameter(description = "知识点 ID") @PathVariable UUID id,
            @Parameter(description = "新的父节点 ID（null 表示根层级）") @RequestParam(required = false) UUID parentKpId,
            @Parameter(description = "排序序号") @RequestParam(required = false) Integer orderIndex) {
        KnowledgePointDto kp = knowledgeService.moveKnowledgePoint(id, parentKpId, orderIndex);
        return ApiResponse.success(kp, "知识点已移动");
    }

    /**
     * 删除知识点（教师/管理员权限）。
     */
    @DeleteMapping("/points/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "删除知识点", description = "删除指定知识点（需先删除子节点），需要教师或管理员权限")
    public ApiResponse<Void> deleteKnowledgePoint(
            @Parameter(description = "知识点 ID") @PathVariable UUID id) {
        knowledgeService.deleteKnowledgePoint(id);
        return ApiResponse.ok();
    }

    /**
     * 批量查询知识点详情。
     */
    @GetMapping("/points/batch")
    @Operation(summary = "批量查询知识点", description = "根据 ID 列表批量查询知识点详情")
    public ApiResponse<List<KnowledgePointDto>> getKnowledgePointsBatch(
            @Parameter(description = "知识点 ID 列表（逗号分隔）")
            @RequestParam("ids") List<UUID> ids) {
        List<KnowledgePointDto> kps = knowledgeService.getKnowledgePointsByIds(ids);
        return ApiResponse.success(kps);
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识点关系 API (Knowledge Relation API)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 创建知识点关系（教师/管理员权限）。
     */
    @PostMapping("/relations")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "创建知识点关系", description = "在两个知识点之间创建语义关系，需要教师或管理员权限")
    public ApiResponse<KnowledgeRelationDto> createRelation(
            @Valid @RequestBody KnowledgeRelationCreateRequest request) {
        KnowledgeRelationDto relation = knowledgeService.createRelation(request);
        return ApiResponse.success(relation, "知识点关系创建成功");
    }

    /**
     * 删除知识点关系（教师/管理员权限）。
     */
    @DeleteMapping("/relations/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "删除知识点关系", description = "删除指定知识点关系，需要教师或管理员权限")
    public ApiResponse<Void> deleteRelation(
            @Parameter(description = "关系 ID") @PathVariable UUID id) {
        knowledgeService.deleteRelation(id);
        return ApiResponse.ok();
    }

    /**
     * 查询指定知识点的所有关系。
     */
    @GetMapping("/points/{id}/relations")
    @Operation(summary = "知识点关系列表", description = "查询指定知识点的所有关联关系（包括作为源和目标）")
    public ApiResponse<List<KnowledgeRelationDto>> getRelationsForKnowledgePoint(
            @Parameter(description = "知识点 ID") @PathVariable UUID id) {
        List<KnowledgeRelationDto> relations =
                knowledgeService.getRelationsForKnowledgePoint(id);
        return ApiResponse.success(relations);
    }

    /**
     * 查询指定知识点的前置依赖。
     */
    @GetMapping("/points/{id}/prerequisites")
    @Operation(summary = "前置依赖查询", description = "查询指定知识点的所有前置依赖知识点")
    public ApiResponse<List<KnowledgeRelationDto>> getPrerequisites(
            @Parameter(description = "知识点 ID") @PathVariable UUID id) {
        List<KnowledgeRelationDto> prerequisites = knowledgeService.getPrerequisites(id);
        return ApiResponse.success(prerequisites);
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识图谱 API (Knowledge Graph API)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 获取课程的知识图谱。
     */
    @GetMapping("/courses/{courseId}/graph")
    @Operation(summary = "知识图谱", description = "获取指定课程的知识图谱结构（节点+边），用于前端可视化展示")
    public ApiResponse<KnowledgeGraphDto> getKnowledgeGraph(
            @Parameter(description = "课程 ID") @PathVariable UUID courseId) {
        KnowledgeGraphDto graph = knowledgeService.getKnowledgeGraph(courseId);
        return ApiResponse.success(graph);
    }
}
