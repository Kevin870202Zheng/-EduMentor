package com.edumentor.course.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.course.entity.EducationStage;
import com.edumentor.course.service.StageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学段管理 API（PRD v4.0 §11.2 / §16）。
 * <p>
 * 提供学段定义的查询，供学生端 StageSelector 学段切换器使用。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/stages")
@Tag(name = "学段管理", description = "学段定义查询（小学/初中/高中/大学）")
public class StageController {

    private static final Logger log = LoggerFactory.getLogger(StageController.class);

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    /**
     * 获取所有学段定义。
     */
    @GetMapping
    @Operation(summary = "获取学段列表", description = "返回全部学段定义（小学 → 初中 → 高中 → 大学），按 sort_order 升序")
    public ApiResponse<List<EducationStage>> listStages() {
        List<EducationStage> stages = stageService.listStages();
        return ApiResponse.success(stages);
    }
}
