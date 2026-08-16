package com.edumentor.course.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.course.dto.KnowledgePointGroupDto;
import com.edumentor.course.dto.ThemeDto;
import com.edumentor.course.service.ThemeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 跨学段主题 API（PRD v4.0 §11.3 / §11.4 / §16）。
 * <p>
 * 提供主题列表（含学段知识点计数）与主题下知识阶梯（按深度分层）查询，
 * 供学生端 ThemeGrid 与 KnowledgeLadder 组件使用。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/themes")
@Tag(name = "跨学段主题", description = "法律主题查询与知识阶梯（按学段×深度分层）")
public class ThemeController {

    private static final Logger log = LoggerFactory.getLogger(ThemeController.class);

    private final ThemeService themeService;

    public ThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    /**
     * 获取主题列表。
     */
    @GetMapping
    @Operation(summary = "获取主题列表", description = "返回跨学段法律主题列表，支持按学段过滤（仅返回该学段下有知识点的主题）并附带知识点计数")
    public ApiResponse<List<ThemeDto>> listThemes(
            @Parameter(description = "学段代码（PRIMARY/JUNIOR/SENIOR/UNIVERSITY），可选")
            @RequestParam(required = false) String stage) {
        List<ThemeDto> themes = themeService.listThemes(stage);
        return ApiResponse.success(themes);
    }

    /**
     * 获取主题下的知识阶梯（按深度分层）。
     */
    @GetMapping("/{themeId}/kps")
    @Operation(summary = "获取主题知识阶梯", description = "按深度等级分层返回主题下的知识点，支持按学段过滤")
    public ApiResponse<List<KnowledgePointGroupDto>> listKpsByTheme(
            @Parameter(description = "主题 ID") @PathVariable UUID themeId,
            @Parameter(description = "学段代码（PRIMARY/JUNIOR/SENIOR/UNIVERSITY），可选")
            @RequestParam(required = false) String stage) {
        List<KnowledgePointGroupDto> groups = themeService.listKpsByTheme(themeId, stage);
        return ApiResponse.success(groups);
    }
}
