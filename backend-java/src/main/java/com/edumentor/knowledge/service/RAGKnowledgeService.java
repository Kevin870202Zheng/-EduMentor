package com.edumentor.knowledge.service;

import com.edumentor.engine.rag.RAGEngine;
import com.edumentor.knowledge.dto.RAGDocumentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RAG 知识库管理服务。
 *
 * <p>包装 {@link RAGEngine} 接口，提供知识库文档的 CRUD 管理能力。
 * 文档数据在内存中维护（与 MockRAGEngine 保持一致），
 * 便于开发和测试。生产环境可替换为基于数据库或向量存储的实现。</p>
 *
 * @author EduMentor Team
 */
@Service
public class RAGKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(RAGKnowledgeService.class);

    private final RAGEngine ragEngine;
    private final Map<String, RAGDocumentDto> documents = new ConcurrentHashMap<>();

    public RAGKnowledgeService(Optional<RAGEngine> ragEngine) {
        this.ragEngine = ragEngine.orElse(null);
    }

    // ════════════════════════════════════════════
    //  文档管理
    // ════════════════════════════════════════════

    /**
     * 列出所有文档。
     *
     * @param courseId 可选的课程 ID 过滤
     * @return 文档列表
     */
    public List<RAGDocumentDto> listDocuments(String courseId) {
        return documents.values().stream()
                .filter(doc -> courseId == null || courseId.equals(doc.getCourseId()))
                .sorted(Comparator.comparing(RAGDocumentDto::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 添加文档到知识库。
     *
     * @param title    文档标题
     * @param content  文档内容
     * @param source   来源（教材/讲义/试题/笔记等）
     * @param courseId 所属课程 ID（可选）
     * @param metadata 附加元数据
     * @return 创建的文档 DTO
     */
    public RAGDocumentDto addDocument(String title, String content,
                                       String source, String courseId,
                                       Map<String, Object> metadata) {
        String id = UUID.randomUUID().toString().replace("-", "");

        RAGDocumentDto doc = new RAGDocumentDto();
        doc.setId(id);
        doc.setTitle(title);
        doc.setContent(content);
        doc.setSource(source != null ? source : "manual");
        doc.setCourseId(courseId);
        doc.setCreatedAt(LocalDateTime.now());

        documents.put(id, doc);
        log.info("RAG document added: id={}, title={}, source={}", id, title, source);
        return doc;
    }

    /**
     * 获取文档详情。
     *
     * @param docId 文档 ID
     * @return 文档 DTO，不存在时返回 null
     */
    public RAGDocumentDto getDocument(String docId) {
        return documents.get(docId);
    }

    /**
     * 删除文档。
     *
     * @param docId 文档 ID
     * @return true 如果删除成功
     */
    public boolean removeDocument(String docId) {
        RAGDocumentDto removed = documents.remove(docId);
        if (removed != null) {
            log.info("RAG document removed: id={}, title={}", docId, removed.getTitle());
            return true;
        }
        return false;
    }

    // ════════════════════════════════════════════
    //  检索
    // ════════════════════════════════════════════

    /**
     * 检索知识库。
     *
     * @param query           查询文本
     * @param topK            返回结果数量
     * @param metadataFilter  元数据过滤条件
     * @param scoreThreshold  相似度阈值
     * @return 检索结果列表
     */
    public List<Map<String, Object>> search(String query, int topK,
                                             Map<String, Object> metadataFilter,
                                             double scoreThreshold) {
        if (ragEngine == null) {
            log.warn("RAGEngine not available, returning empty results");
            return Collections.emptyList();
        }

        List<RAGEngine.DocumentChunk> results;

        // 按课程过滤
        if (metadataFilter != null && metadataFilter.containsKey("courseId")) {
            String courseId = (String) metadataFilter.get("courseId");
            results = ragEngine.retrieveByCourse(query, courseId, topK);
        } else {
            results = ragEngine.retrieve(query, topK);
        }

        return results.stream()
                .filter(chunk -> chunk.getScore() >= scoreThreshold)
                .map(chunk -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", chunk.getId());
                    item.put("documentId", chunk.getDocumentId());
                    item.put("title", chunk.getTitle());
                    item.put("content", chunk.getContent());
                    item.put("score", chunk.getScore());
                    item.put("sourceType", chunk.getSourceType());
                    item.put("courseId", chunk.getCourseId());
                    item.put("knowledgePointId", chunk.getKnowledgePointId());
                    return item;
                })
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════
    //  统计
    // ════════════════════════════════════════════

    /**
     * 获取知识库统计信息。
     *
     * @return 统计数据
     */
    public Map<String, Object> getStats() {
        int chunkCount = 0;
        if (ragEngine != null) {
            Map<String, Object> status = ragEngine.getStatus();
            chunkCount = (int) status.getOrDefault("documentCount", 0);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("documentCount", documents.size());
        stats.put("chunkCount", chunkCount);

        // 文档概要
        stats.put("documents", documents.values().stream()
                .map(doc -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("id", doc.getId());
                    summary.put("title", doc.getTitle());
                    summary.put("source", doc.getSource());
                    summary.put("courseId", doc.getCourseId());
                    summary.put("contentLength", doc.getContentLength());
                    summary.put("createdAt", doc.getCreatedAt());
                    return summary;
                })
                .collect(Collectors.toList()));

        return stats;
    }

    /**
     * 获取文档数量。
     *
     * @return 文档数量
     */
    public int documentCount() {
        return documents.size();
    }
}
