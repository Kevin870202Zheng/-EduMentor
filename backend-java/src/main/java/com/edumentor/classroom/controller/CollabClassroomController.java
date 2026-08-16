package com.edumentor.classroom.controller;

import com.edumentor.classroom.entity.CollabClassroomProject;
import com.edumentor.classroom.entity.CollabProjectTask;
import com.edumentor.classroom.entity.Classroom;
import com.edumentor.classroom.entity.enums.CollabRoleType;
import com.edumentor.classroom.service.CollabClassroomService;
import com.edumentor.common.response.ApiResponse;
import com.edumentor.entity.enums.UserRole;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 学段协作课堂 API（设计文档 §5.4）。
 * 仅教师可创建/邀请/复核/生成；被邀学生提交自己的任务。
 */
@RestController
@RequestMapping("/api/collab-classrooms")
@Tag(name = "学段协作课堂", description = "跨学段学生协作共创智慧课堂（教师发起）")
public class CollabClassroomController {

    private static final Logger log = LoggerFactory.getLogger(CollabClassroomController.class);

    private final CollabClassroomService collabService;
    private final UserRepository userRepository;

    public CollabClassroomController(CollabClassroomService collabService, UserRepository userRepository) {
        this.collabService = collabService;
        this.userRepository = userRepository;
    }

    /** 创建项目（仅教师） */
    @PostMapping
    @Operation(summary = "创建协作项目", description = "教师创建学段协作课堂项目（DRAFT）")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, Principal principal) {
        UUID creatorId = getUserIdFromPrincipal(principal);
        requireTeacher(creatorId);
        CollabClassroomProject project = collabService.create(
                creatorId,
                (String) body.get("title"),
                (String) body.get("description"),
                parseUuid(body.get("courseId")),
                (Integer) body.get("difficulty"));
        return ApiResponse.success(collabService.getDetail(project.getId()), "项目创建成功");
    }

    /** 我的协作项目（发起的 + 参与的） */
    @GetMapping
    @Operation(summary = "我的协作项目", description = "我发起的 + 我参与的项目列表（含任务进度）")
    public ApiResponse<List<Map<String, Object>>> listMine(Principal principal) {
        UUID userId = getUserIdFromPrincipal(principal);
        return ApiResponse.success(collabService.listMine(userId));
    }

    /** 项目详情 */
    @GetMapping("/{id}")
    @Operation(summary = "项目详情", description = "项目信息 + 四角色任务 + 学生姓名 + 故事信息")
    public ApiResponse<Map<String, Object>> getDetail(@PathVariable String id) {
        return ApiResponse.success(collabService.getDetail(UUID.fromString(id)));
    }

    /** 更新项目基础信息 */
    @PutMapping("/{id}")
    @Operation(summary = "更新项目", description = "修改标题/描述/课程/难度（生成前）")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                   @RequestBody Map<String, Object> body,
                                                   Principal principal) {
        UUID creatorId = getUserIdFromPrincipal(principal);
        requireTeacher(creatorId);
        CollabClassroomProject project = collabService.update(
                UUID.fromString(id),
                (String) body.get("title"),
                (String) body.get("description"),
                parseUuid(body.get("courseId")),
                (Integer) body.get("difficulty"));
        return ApiResponse.success(collabService.getDetail(project.getId()), "更新成功");
    }

    /** 该学段可邀学生 */
    @GetMapping("/{id}/candidates")
    @Operation(summary = "候选学生", description = "按学段返回可邀学生列表")
    public ApiResponse<List<Map<String, Object>>> candidates(@PathVariable String id,
                                                             @RequestParam String stage) {
        return ApiResponse.success(collabService.candidates(stage.toUpperCase()));
    }

    /** 邀请学生到角色任务 */
    @PostMapping("/{id}/invite")
    @Operation(summary = "邀请学生", description = "将学生分配到指定角色任务（校验学段匹配）")
    public ApiResponse<Map<String, Object>> invite(@PathVariable String id,
                                                   @RequestBody Map<String, Object> body,
                                                   Principal principal) {
        UUID creatorId = getUserIdFromPrincipal(principal);
        requireTeacher(creatorId);
        CollabRoleType roleType = CollabRoleType.valueOf(((String) body.get("roleType")).toUpperCase());
        UUID studentId = UUID.fromString((String) body.get("userId"));
        CollabProjectTask task = collabService.invite(UUID.fromString(id), roleType, studentId);
        return ApiResponse.success(task.toDto(), "邀请成功");
    }

    /** 被邀学生提交任务 */
    @PostMapping("/{id}/tasks/{taskId}/submit")
    @Operation(summary = "提交任务", description = "被邀学生提交自己的角色产出")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String id,
                                                   @PathVariable String taskId,
                                                   @RequestBody Map<String, Object> body,
                                                   Principal principal) {
        UUID studentId = getUserIdFromPrincipal(principal);
        CollabProjectTask task = collabService.submit(
                UUID.fromString(id), UUID.fromString(taskId), studentId, (String) body.get("content"));
        return ApiResponse.success(task.toDto(), "提交成功");
    }

    /** 教师复核/修改任务 */
    @PostMapping("/{id}/tasks/{taskId}/review")
    @Operation(summary = "复核任务", description = "教师查看并修改任务内容，标记已复核")
    public ApiResponse<Map<String, Object>> review(@PathVariable String id,
                                                   @PathVariable String taskId,
                                                   @RequestBody Map<String, Object> body,
                                                   Principal principal) {
        UUID creatorId = getUserIdFromPrincipal(principal);
        requireTeacher(creatorId);
        CollabProjectTask task = collabService.review(
                UUID.fromString(id), UUID.fromString(taskId), creatorId, (String) body.get("content"));
        return ApiResponse.success(task.toDto(), "已复核");
    }

    /** 确认生成课堂 */
    @PostMapping("/{id}/generate")
    @Operation(summary = "生成课堂", description = "教师确认后聚合素材并生成智慧课堂（同步）")
    public ApiResponse<Map<String, Object>> generate(@PathVariable String id, Principal principal) {
        UUID creatorId = getUserIdFromPrincipal(principal);
        requireTeacher(creatorId);
        Classroom classroom = collabService.generate(UUID.fromString(id), creatorId);
        Map<String, Object> result = classroom.toDto();
        result.put("mode", "collaborative");
        return ApiResponse.success(result, "协作课堂生成成功");
    }

    // ═══════════════════════════════════════════════════

    private void requireTeacher(UUID userId) {
        userRepository.findById(userId).ifPresentOrElse(u -> {
            if (u.getRole() != UserRole.TEACHER && u.getRole() != UserRole.ADMIN) {
                throw new IllegalArgumentException("仅教师可以执行此操作");
            }
        }, () -> {
            throw new IllegalArgumentException("用户不存在");
        });
    }

    private UUID parseUuid(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try {
            return UUID.fromString(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

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
