package com.edumentor.classroom.controller;

import com.edumentor.classroom.entity.StoryLibrary;
import com.edumentor.classroom.repository.StoryLibraryRepository;
import com.edumentor.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 中华传统故事库 API（设计文档 §5.4）。
 * 本期为预置数据只读；后续演进为公共知识库由教师维护。
 */
@RestController
@RequestMapping("/api/stories")
@Tag(name = "故事库", description = "中华传统故事库（学段协作课堂素材）")
public class StoryController {

    private final StoryLibraryRepository storyRepository;

    public StoryController(StoryLibraryRepository storyRepository) {
        this.storyRepository = storyRepository;
    }

    @GetMapping
    @Operation(summary = "故事列表", description = "返回全部已发布故事")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> items = storyRepository.findByStatusOrderByCreatedAtDesc("published")
                .stream().map(StoryLibrary::toDto).toList();
        return ApiResponse.success(items);
    }

    @GetMapping("/{id}")
    @Operation(summary = "故事详情", description = "按 ID 获取单个故事")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        return storyRepository.findById(UUID.fromString(id))
                .map(s -> ApiResponse.<Map<String, Object>>success(s.toDto()))
                .orElseGet(() -> ApiResponse.error(404, "故事不存在"));
    }
}
