package com.edumentor.engine.rag;

import java.util.List;
import java.util.Map;

/**
 * RAG（检索增强生成）引擎接口。
 * <p>
 * 负责将用户问题与知识库匹配，检索相关文档片段作为 LLM 生成的上下文。
 * 支持向量检索和关键词检索两种模式，并支持混合检索策略。
 * </p>
 *
 * <p><strong>设计说明：</strong>
 * 本接口定义 QAService 所需的 RAG 检索能力。
 * 具体实现由 D1 任务 (RAG 引擎 Java 实现) 完成。
 * </p>
 */
public interface RAGEngine {

    /**
     * 检索与问题相关的文档片段。
     *
     * @param query 查询文本（用户问题）
     * @param topK  返回结果数量
     * @return 文档片段列表（按相关性降序排列）
     */
    List<DocumentChunk> retrieve(String query, int topK);

    /**
     * 在指定课程范围内检索相关知识。
     *
     * @param query    查询文本
     * @param courseId 课程 ID（限定检索范围）
     * @param topK     返回结果数量
     * @return 文档片段列表
     */
    List<DocumentChunk> retrieveByCourse(String query, String courseId, int topK);

    /**
     * 在指定知识点范围内检索知识。
     *
     * @param query             查询文本
     * @param knowledgePointId  知识点 ID
     * @param topK              返回结果数量
     * @return 文档片段列表
     */
    List<DocumentChunk> retrieveByKnowledgePoint(String query, String knowledgePointId, int topK);

    /**
     * 基于问题构建增强后的提示词上下文。
     * <p>
     * 自动执行检索并将结果格式化为 LLM 友好的上下文文本。
     * </p>
     *
     * @param question 用户问题
     * @param topK     检索数量
     * @return 增强后的上下文字符串
     */
    String buildEnhancedContext(String question, int topK);

    /**
     * 构建增强上下文（限定课程范围）。
     *
     * @param question 用户问题
     * @param courseId 课程 ID
     * @param topK     检索数量
     * @return 增强后的上下文字符串
     */
    String buildEnhancedContext(String question, String courseId, int topK);

    /**
     * 检索并返回来源信息（用于前端展示引用来源）。
     *
     * @param query 查询文本
     * @param topK  返回数量
     * @return 来源信息列表
     */
    List<SourceInfo> retrieveSources(String query, int topK);

    /**
     * 判断 RAG 引擎是否已启用。
     *
     * @return true 表示已启用并可检索
     */
    boolean isEnabled();

    /**
     * 获取 RAG 引擎状态信息。
     *
     * @return 状态信息（文档数、索引大小等）
     */
    Map<String, Object> getStatus();

    // ──── Inner Types ────

    /**
     * 文档片段 — RAG 检索结果的最小单元。
     */
    class DocumentChunk {
        private String id;
        private String documentId;
        private String title;
        private String content;
        private float[] embedding;
        private double score;
        private String sourceType;
        private String courseId;
        private String knowledgePointId;
        private Map<String, Object> metadata;

        public DocumentChunk() {
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public float[] getEmbedding() { return embedding; }
        public void setEmbedding(float[] embedding) { this.embedding = embedding; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(String knowledgePointId) { this.knowledgePointId = knowledgePointId; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * 来源信息 — 用于前端展示引用。
     */
    class SourceInfo {
        private String title;
        private String snippet;
        private double score;
        private String sourceType;
        private String url;

        public SourceInfo() {
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSnippet() { return snippet; }
        public void setSnippet(String snippet) { this.snippet = snippet; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
