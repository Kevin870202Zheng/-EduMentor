package com.edumentor.knowledge.controller;

import com.edumentor.common.response.ApiResponse;
import com.edumentor.knowledge.dto.ImportRequest;
import com.edumentor.knowledge.dto.RAGDocumentDto;
import com.edumentor.knowledge.dto.SearchRequest;
import com.edumentor.knowledge.service.RAGKnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库管理 REST API。
 *
 * <p>提供知识库文档的增删查管接口，以及知识检索功能。</p>
 *
 * @author EduMentor Team
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RAGKnowledgeController {

    private final RAGKnowledgeService ragKnowledgeService;

    public RAGKnowledgeController(RAGKnowledgeService ragKnowledgeService) {
        this.ragKnowledgeService = ragKnowledgeService;
    }

    // ════════════════════════════════════════════
    //  文档管理
    // ════════════════════════════════════════════

    /**
     * 列出所有文档。
     *
     * @param courseId 可选，按课程过滤
     * @return 文档列表
     */
    @GetMapping("/documents")
    public ApiResponse<Map<String, Object>> listDocuments(
            @RequestParam(required = false) String courseId) {
        List<RAGDocumentDto> docs = ragKnowledgeService.listDocuments(courseId);
        return ApiResponse.success(Map.of(
                "documents", docs,
                "total", docs.size(),
                "chunkCount", ragKnowledgeService.documentCount()
        ));
    }

    /**
     * 添加文档到知识库。
     *
     * @param request 文档数据
     * @return 创建的文档
     */
    @PostMapping("/documents")
    public ApiResponse<RAGDocumentDto> addDocument(@RequestBody Map<String, Object> request) {
        String title = (String) request.getOrDefault("title", "");
        String content = (String) request.getOrDefault("content", "");
        String source = (String) request.getOrDefault("source", "manual");
        String courseId = (String) request.get("courseId");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.getOrDefault("metadata", Map.of());

        if (title == null || title.isBlank()) {
            return ApiResponse.error(400, "文档标题不能为空");
        }
        if (content == null || content.isBlank()) {
            return ApiResponse.error(400, "文档内容不能为空");
        }
        if (content.length() < 20) {
            return ApiResponse.error(400, "文档内容太少（至少 20 个字符）");
        }

        RAGDocumentDto doc = ragKnowledgeService.addDocument(title.trim(), content.trim(),
                source, courseId, metadata);
        return ApiResponse.success(doc, "文档添加成功");
    }

    /**
     * 获取文档详情。
     *
     * @param docId 文档 ID
     * @return 文档详情
     */
    @GetMapping("/documents/{docId}")
    public ApiResponse<Map<String, Object>> getDocument(@PathVariable String docId) {
        RAGDocumentDto doc = ragKnowledgeService.getDocument(docId);
        if (doc == null) {
            return ApiResponse.error(404, "文档不存在");
        }
        return ApiResponse.success(Map.of(
                "document", doc,
                "chunkCount", 0
        ));
    }

    /**
     * 删除文档。
     *
     * @param docId 文档 ID
     * @return 操作结果
     */
    @DeleteMapping("/documents/{docId}")
    public ApiResponse<Void> deleteDocument(@PathVariable String docId) {
        boolean removed = ragKnowledgeService.removeDocument(docId);
        if (!removed) {
            return ApiResponse.error(404, "文档不存在");
        }
        return ApiResponse.success(null, "文档已删除");
    }

    // ════════════════════════════════════════════
    //  检索
    // ════════════════════════════════════════════

    /**
     * 检索知识库。
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @PostMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @Valid @RequestBody SearchRequest request) {
        Map<String, Object> metadataFilter = new java.util.LinkedHashMap<>();
        if (request.getCourseId() != null) {
            metadataFilter.put("courseId", request.getCourseId());
        }

        List<Map<String, Object>> results = ragKnowledgeService.search(
                request.getQuery(),
                request.getTopK() != null ? request.getTopK() : 10,
                metadataFilter,
                request.getScoreThreshold() != null ? request.getScoreThreshold() : 0.0
        );

        return ApiResponse.success(Map.of(
                "query", request.getQuery(),
                "results", results,
                "total", results.size()
        ));
    }

    // ════════════════════════════════════════════
    //  统计
    // ════════════════════════════════════════════

    /**
     * 获取知识库统计信息。
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.success(ragKnowledgeService.getStats());
    }

    // ════════════════════════════════════════════
    //  批量导入
    // ════════════════════════════════════════════

    /**
     * 批量导入文档。
     *
     * @param request 批量导入请求
     * @return 导入结果
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importDocuments(
            @Valid @RequestBody ImportRequest request) {
        List<Map<String, String>> results = new ArrayList<>();

        for (ImportRequest.DocumentItem item : request.getDocuments()) {
            if (item.getTitle() == null || item.getTitle().isBlank()) continue;
            if (item.getContent() == null || item.getContent().isBlank()) continue;

            RAGDocumentDto doc = ragKnowledgeService.addDocument(
                    item.getTitle().trim(),
                    item.getContent().trim(),
                    item.getSource() != null ? item.getSource() : "batch_import",
                    item.getCourseId(),
                    Map.of()
            );

            results.add(Map.of(
                    "id", doc.getId(),
                    "title", doc.getTitle(),
                    "status", "success"
            ));
        }

        return ApiResponse.success(Map.of(
                "imported", results.size(),
                "documents", results
        ), "成功导入 " + results.size() + " 个文档");
    }
}
