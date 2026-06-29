package com.edumentor.course.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.course.entity.Course;
import com.edumentor.course.entity.CourseMaterial;
import com.edumentor.course.repository.CourseMaterialRepository;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.course.service.CourseExtractionService;
import com.edumentor.user.entity.User;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 课程内容管理 REST API。
 * <p>
 * 提供教师端课程资料上传、AI 提取、提取结果发布等功能。
 * 使用课程编号（courseCode）作为业务标识。
 * </p>
 */
@RestController
@RequestMapping("/api/courses/{courseCode}")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class CourseContentController {

    private static final Logger log = LoggerFactory.getLogger(CourseContentController.class);

    private final CourseRepository courseRepository;
    private final CourseMaterialRepository courseMaterialRepository;
    private final CourseExtractionService courseExtractionService;

    public CourseContentController(CourseRepository courseRepository,
                                   CourseMaterialRepository courseMaterialRepository,
                                   CourseExtractionService courseExtractionService) {
        this.courseRepository = courseRepository;
        this.courseMaterialRepository = courseMaterialRepository;
        this.courseExtractionService = courseExtractionService;
    }

    /**
     * 获取课程信息（按课程编号）。
     * 允许所有已认证用户访问（学生、教师、管理员）。
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getCourseInfo(@PathVariable String courseCode) {
        Course course = courseRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在: " + courseCode));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", course.getId());
        info.put("courseCode", course.getCourseCode());
        info.put("name", course.getName());
        info.put("description", course.getDescription());
        info.put("subject", course.getSubject());
        info.put("gradeLevel", course.getGradeLevel());
        info.put("isPublished", course.getIsPublished());
        return ApiResponse.success(info);
    }

    /**
     * 上传课程资料。
     */
    @PostMapping(value = "/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadMaterial(
            @PathVariable String courseCode,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {

        Course course = courseRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在: " + courseCode));

        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        String fileType = detectFileType(fileName, contentType);

        log.info("上传课程资料: courseCode={}, fileName={}, fileType={}", courseCode, fileName, fileType);

        // 解析文件内容（文本格式直接读取，二进制格式用 Apache Tika 解析）
        String rawText;
        try {
            rawText = parseFileContent(file, fileType);
        } catch (Exception e) {
            return ApiResponse.error(400, "文件解析失败: " + e.getMessage());
        }

        if (rawText.isBlank()) {
            return ApiResponse.error(400, "文件内容为空");
        }

        // 创建资料记录
        CourseMaterial material = new CourseMaterial();
        material.setCourseId(course.getId());
        material.setCourseCode(courseCode);
        material.setTitle(fileName);
        material.setFileType(fileType);
        material.setRawText(rawText);
        material.setStatus("pending");
        material.setCreatedBy(currentUser.getId());

        CourseMaterial saved = courseMaterialRepository.save(material);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("title", saved.getTitle());
        result.put("fileType", saved.getFileType());
        result.put("status", saved.getStatus());
        result.put("textLength", rawText.length());
        result.put("createdAt", saved.getCreatedAt());

        return ApiResponse.success(result, "资料上传成功");
    }

    /**
     * 获取课程资料列表。
     */
    @GetMapping("/materials")
    public ApiResponse<List<Map<String, Object>>> listMaterials(@PathVariable String courseCode) {
        List<CourseMaterial> materials = courseMaterialRepository.findByCourseCodeOrderByCreatedAtDesc(courseCode);
        List<Map<String, Object>> result = materials.stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("title", m.getTitle());
            item.put("fileType", m.getFileType());
            item.put("status", m.getStatus());
            item.put("textLength", m.getRawText() != null ? m.getRawText().length() : 0);
            item.put("hasExtractionResult", m.getExtractionResult() != null);
            item.put("errorMessage", m.getErrorMessage());
            item.put("createdAt", m.getCreatedAt());
            return item;
        }).toList();
        return ApiResponse.success(result);
    }

    /**
     * 对资料执行 AI 提取。
     */
    @PostMapping("/materials/{materialId}/extract")
    public ApiResponse<Map<String, Object>> extractMaterial(
            @PathVariable String courseCode,
            @PathVariable UUID materialId) {

        log.info("触发 AI 提取: courseCode={}, materialId={}", courseCode, materialId);

        String resultJson = courseExtractionService.extract(materialId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materialId", materialId);
        result.put("status", "extracted");
        result.put("result", resultJson);

        return ApiResponse.success(result, "AI 提取完成");
    }

    /**
     * 获取资料的提取结果缓存。
     */
    @GetMapping("/materials/{materialId}/extraction")
    public ApiResponse<Map<String, Object>> getExtractionResult(
            @PathVariable String courseCode,
            @PathVariable UUID materialId) {

        CourseMaterial material = courseMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在: " + materialId));

        if (material.getExtractionResult() == null) {
            return ApiResponse.error(404, "提取结果不存在，请先执行 AI 提取");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materialId", materialId);
        result.put("result", material.getExtractionResult());

        return ApiResponse.success(result);
    }

    /**
     * 发布提取结果（确认后持久化到业务表）。
     */
    @PostMapping("/materials/{materialId}/publish")
    public ApiResponse<Void> publishExtraction(
            @PathVariable String courseCode,
            @PathVariable UUID materialId) {

        Course course = courseRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在: " + courseCode));

        courseExtractionService.publishExtraction(course.getId(), materialId);
        return ApiResponse.success(null, "提取结果已发布");
    }

    /**
     * 检测文件类型。
     */
    private String detectFileType(String fileName, String contentType) {
        if (fileName == null) return "txt";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "docx";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "xlsx";
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) return "pptx";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "md";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".csv")) return "csv";
        return "txt";
    }

    /**
     * 使用 Apache Tika 解析文件内容。
     * 文本格式直接 UTF-8 读取，二进制格式（PDF/Word/Excel/PPT）由 Tika 提取文本。
     */
    private String parseFileContent(MultipartFile file, String fileType) throws IOException {
        if (List.of("txt", "md", "html", "json", "csv").contains(fileType)) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream is = file.getInputStream()) {
            return new Tika().parseToString(is);
        } catch (TikaException e) {
            throw new IOException("Tika 解析失败: " + e.getMessage(), e);
        }
    }
}
