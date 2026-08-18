package com.edumentor.classroom.controller;

import com.edumentor.classroom.dto.DiscussionReplyRequest;
import com.edumentor.classroom.dto.DiscussionReplyResponse;
import com.edumentor.classroom.service.DiscussionService;
import com.edumentor.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小E 讨论控制器 — AI同学小E 对讨论观点的实时点评。
 * <p>
 * API 路径前缀: /api/v2/classrooms/discussion
 * </p>
 */
@RestController
@RequestMapping("/api/v2/classrooms/discussion")
@Tag(name = "小E 讨论", description = "AI同学小E 对讨论观点的点评")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    /**
     * 提交学生观点，获取小E 的回应。
     */
    @PostMapping("/reply")
    @Operation(summary = "小E 点评", description = "提交讨论话题+学生观点，返回AI同学小E的回应")
    public ApiResponse<DiscussionReplyResponse> reply(@RequestBody DiscussionReplyRequest request) {
        if (request.getStudentView() == null || request.getStudentView().isBlank()) {
            return ApiResponse.error(400, "请先表达你的观点");
        }
        return ApiResponse.success(discussionService.reply(request));
    }
}
