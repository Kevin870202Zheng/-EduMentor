package com.edumentor.moment.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.moment.service.MomentService;
import com.edumentor.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 同学圈 API（设计文档 moments-legal-review-design.html v1.0 §6）。
 * 发布动态时同步进行 AI 法律风险检测；支持本地图片上传、点赞、评论。
 */
@RestController
@RequestMapping("/api/moments")
@Tag(name = "同学圈", description = "学生朋友圈：发布（AI 法律检测）/ 点赞 / 评论 / 图片上传")
public class MomentController {

    private final MomentService momentService;

    public MomentController(MomentService momentService) {
        this.momentService = momentService;
    }

    /** 发布动态（含 AI 法律检测） */
    @Operation(summary = "发布动态", description = "内容同步触发 AI 法律风险检测；不涉及法律则 aiReview 为 null")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                   Principal principal) {
        UUID studentId = getUserId(principal);
        String content = body.get("content") != null ? body.get("content").toString() : "";
        List<String> images = (List<String>) body.getOrDefault("images", List.of());
        return ApiResponse.success(momentService.create(studentId, content, images));
    }

    /** 动态流（分页） */
    @Operation(summary = "动态流")
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 Principal principal) {
        UUID studentId = getUserId(principal);
        return ApiResponse.success(momentService.list(studentId, page, size));
    }

    /** 删除动态（仅作者） */
    @Operation(summary = "删除动态")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, Principal principal) {
        momentService.delete(getUserId(principal), id);
        return ApiResponse.success(null);
    }

    /** 上传图片（返回 URL） */
    @Operation(summary = "上传图片", description = "支持 jpg/png/gif/webp，≤10MB，返回 /uploads/moments/xxx.jpg")
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        String url = momentService.saveImage(file);
        return ApiResponse.success(Map.of("url", url));
    }

    /** 点赞/取消点赞（toggle） */
    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/{id}/like")
    public ApiResponse<Map<String, Object>> like(@PathVariable UUID id, Principal principal) {
        return ApiResponse.success(momentService.toggleLike(getUserId(principal), id));
    }

    /** 评论列表 */
    @Operation(summary = "评论列表")
    @GetMapping("/{id}/comments")
    public ApiResponse<List<Map<String, Object>>> comments(@PathVariable UUID id) {
        return ApiResponse.success(momentService.listComments(id));
    }

    /** 发表评论 */
    @Operation(summary = "发表评论")
    @PostMapping("/{id}/comments")
    public ApiResponse<Map<String, Object>> addComment(@PathVariable UUID id,
                                                       @RequestBody Map<String, Object> body,
                                                       Principal principal) {
        String content = body.get("content") != null ? body.get("content").toString() : "";
        return ApiResponse.success(momentService.addComment(getUserId(principal), id, content));
    }

    /** 重新分析（AI 补检，仅作者） */
    @Operation(summary = "重新分析", description = "仅作者本人可触发，重新执行 AI 法律检测")
    @PostMapping("/{id}/review")
    public ApiResponse<Map<String, Object>> reReview(@PathVariable UUID id, Principal principal) {
        return ApiResponse.success(momentService.reReview(getUserId(principal), id));
    }

    // ═══════════════════════════════════════════════════════════

    private UUID getUserId(Principal principal) {
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
