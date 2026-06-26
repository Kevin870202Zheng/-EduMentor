package com.edumentor.course.controller;

import com.edumentor.course.repository.CourseRepository;
import com.edumentor.engine.embedding.VectorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 向量化测试端点 — 用于手动触发课程内容向量化。
 * <p>
 * 仅开发/测试使用，后续可移除或集成到课程管理页面。
 * </p>
 */
@RestController
@RequestMapping("/api/dev")
public class VectorizeController {

    private static final Logger log = LoggerFactory.getLogger(VectorizeController.class);

    private final VectorizationService vectorizationService;
    private final CourseRepository courseRepository;

    public VectorizeController(VectorizationService vectorizationService,
                               CourseRepository courseRepository) {
        this.vectorizationService = vectorizationService;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /api/dev/vectorize/{courseCode} — 向量化指定课程的知识点。
     */
    @PostMapping("/vectorize/{courseCode}")
    public ResponseEntity<Map<String, Object>> vectorize(@PathVariable String courseCode) {
        try {
            var courseOpt = courseRepository.findByCourseCode(courseCode);
            if (courseOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false, "message", "课程不存在: " + courseCode));
            }

            var course = courseOpt.get();
            int count = vectorizationService.vectorizeCourse(course.getId(), courseCode);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "向量化完成",
                    "courseCode", courseCode,
                    "count", count));
        } catch (Exception e) {
            log.error("向量化失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/dev/vectorize/status — 查看向量化引擎状态。
     */
    @GetMapping("/vectorize/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "engine", "VectorRAGEngine",
                "available", vectorizationService.isEmbeddingAvailable()));
    }
}
