package com.edumentor.timemachine.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.timemachine.dto.AnswerLetterRequest;
import com.edumentor.timemachine.dto.ArchiveRequest;
import com.edumentor.timemachine.dto.TimeMachineLetterRequest;
import com.edumentor.timemachine.service.TimeMachineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 成长时光机 API — 跨学段自我对话与成长记录。
 */
@RestController
@RequestMapping("/api/time-machine")
public class TimeMachineController {

    private final TimeMachineService timeMachineService;

    public TimeMachineController(TimeMachineService timeMachineService) {
        this.timeMachineService = timeMachineService;
    }

    /** 总览：成长档案 + 信件 + 当前学段 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestParam UUID studentId) {
        return ApiResponse.success(timeMachineService.overview(studentId));
    }

    /** 信件列表 */
    @GetMapping("/letters")
    public ApiResponse<List<Map<String, Object>>> letters(@RequestParam UUID studentId) {
        return ApiResponse.success(timeMachineService.listLetters(studentId));
    }

    /** 创建信件（question 留空则 AI 生成） */
    @PostMapping("/letters")
    public ApiResponse<Map<String, Object>> createLetter(@Valid @RequestBody TimeMachineLetterRequest request) {
        return ApiResponse.success(timeMachineService.createLetter(request), "信件已寄出");
    }

    /** 回答信件 */
    @PostMapping("/letters/{id}/answer")
    public ApiResponse<Map<String, Object>> answerLetter(@PathVariable UUID id,
                                                         @Valid @RequestBody AnswerLetterRequest request) {
        return ApiResponse.success(timeMachineService.answerLetter(id, request.getAnswer()), "回信已保存");
    }

    /** 手动归档成长快照 */
    @PostMapping("/archive")
    public ApiResponse<Map<String, Object>> archive(@Valid @RequestBody ArchiveRequest request) {
        return ApiResponse.success(timeMachineService.archive(request), "成长档案已归档");
    }

    /** 学段学习报告（AI 生成） */
    @GetMapping("/stage-report")
    public ApiResponse<Map<String, Object>> stageReport(@RequestParam UUID studentId,
                                                        @RequestParam(required = false) String stage) {
        return ApiResponse.success(timeMachineService.stageReport(studentId, stage));
    }
}
