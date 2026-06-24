package com.edumentor.engine.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Mock RAG 引擎实现 — 开发/调试用。
 * <p>
 * 当 {@link RAGEngine} 接口无其他实现时自动生效。
 * 返回模拟的知识库内容，不执行实际检索。
 * </p>
 *
 * <p><strong>注意：</strong> 本实现仅为开发阶段使用。
 * 生产环境应替换为真正的 RAG 实现（由 D1 任务提供）。
 * 当 D1 的 {@code @Service("ragEngineImpl")} 实现注册后，
 * {@code @ConditionalOnMissingBean} 会使本 Mock 自动失效。
 * </p>
 */
@Component("ragEngineImpl")
@ConditionalOnMissingBean(name = "ragEngineImpl")
public class MockRAGEngine implements RAGEngine {

    private static final Logger log = LoggerFactory.getLogger(MockRAGEngine.class);

    /** 模拟知识库 */
    private static final List<MockDocument> MOCK_DOCUMENTS = Arrays.asList(
        new MockDocument("doc-1", "Java 基础", "Java 是一种面向对象的编程语言，具有跨平台特性。核心概念包括类、对象、继承、多态、封装。",
            "textbook", "course-1", "kp-1"),
        new MockDocument("doc-2", "Spring Boot 入门", "Spring Boot 是 Spring 框架的扩展，提供了自动配置、起步依赖和嵌入式服务器等特性。",
            "textbook", "course-1", "kp-2"),
        new MockDocument("doc-3", "REST API 设计", "RESTful API 使用 HTTP 方法（GET/POST/PUT/DELETE）操作资源，遵循无状态、统一接口等约束。",
            "reference", "course-1", "kp-3"),
        new MockDocument("doc-4", "数据库基础", "PostgreSQL 是功能强大的开源关系型数据库，支持 JSONB、事务、索引等高级特性。",
            "textbook", "course-2", "kp-4"),
        new MockDocument("doc-5", "JPA 实体映射", "JPA 提供 @Entity、@Table、@Column 等注解实现对象关系映射，支持 @OneToMany、@ManyToOne 等关联。",
            "reference", "course-1", "kp-5"),
        new MockDocument("doc-6", "JWT 认证", "JWT（JSON Web Token）是一种无状态认证方案，由 Header、Payload、Signature 三部分组成。",
            "textbook", "course-1", "kp-6"),
        new MockDocument("doc-7", "微服务架构", "微服务架构将应用拆分为多个独立服务，每个服务可独立部署、扩展和维护。",
            "reference", "course-3", "kp-7"),
        new MockDocument("doc-8", "Python 基础", "Python 是一种动态类型、解释型编程语言，以其简洁的语法和丰富的生态著称。",
            "textbook", "course-4", "kp-8"),
        new MockDocument("doc-9", "Flask 框架", "Flask 是 Python 的微框架，轻量灵活，适合 REST API 开发。",
            "textbook", "course-4", "kp-9"),
        new MockDocument("doc-10", "SQL 查询优化", "索引优化、查询计划分析、连接策略选择是 SQL 性能优化的三个核心方向。",
            "reference", "course-2", "kp-10")
    );

    @Override
    public List<DocumentChunk> retrieve(String query, int topK) {
        log.info("[MockRAG] retrieve() - query: {}, topK: {}", query, topK);
        return searchDocuments(query, topK, null, null);
    }

    @Override
    public List<DocumentChunk> retrieveByCourse(String query, String courseId, int topK) {
        log.info("[MockRAG] retrieveByCourse() - query: {}, course: {}", query, courseId);
        return searchDocuments(query, topK, courseId, null);
    }

    @Override
    public List<DocumentChunk> retrieveByKnowledgePoint(String query, String knowledgePointId, int topK) {
        log.info("[MockRAG] retrieveByKnowledgePoint() - query: {}, kp: {}", query, knowledgePointId);
        return searchDocuments(query, topK, null, knowledgePointId);
    }

    @Override
    public String buildEnhancedContext(String question, int topK) {
        List<DocumentChunk> docs = retrieve(question, topK);
        return formatContext(docs);
    }

    @Override
    public String buildEnhancedContext(String question, String courseId, int topK) {
        List<DocumentChunk> docs = retrieveByCourse(question, courseId, topK);
        return formatContext(docs);
    }

    @Override
    public List<SourceInfo> retrieveSources(String query, int topK) {
        List<DocumentChunk> docs = retrieve(query, topK);
        List<SourceInfo> sources = new ArrayList<>();
        for (DocumentChunk doc : docs) {
            SourceInfo info = new SourceInfo();
            info.setTitle(doc.getTitle());
            info.setSnippet(doc.getContent().substring(0, Math.min(100, doc.getContent().length())));
            info.setScore(doc.getScore());
            info.setSourceType(doc.getSourceType());
            sources.add(info);
        }
        return sources;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", true);
        status.put("documentCount", MOCK_DOCUMENTS.size());
        status.put("type", "mock");
        return status;
    }

    // ──── 私有方法 ────

    private synchronized List<DocumentChunk> searchDocuments(String query, int topK,
                                                              String courseId, String knowledgePointId) {
        List<MockDocument> candidates = MOCK_DOCUMENTS;

        // 按课程过滤
        if (courseId != null) {
            candidates = candidates.stream()
                .filter(d -> courseId.equals(d.courseId))
                .toList();
        }

        // 按知识点过滤
        if (knowledgePointId != null) {
            candidates = candidates.stream()
                .filter(d -> knowledgePointId.equals(d.knowledgePointId))
                .toList();
        }

        // 简单关键词匹配以模拟相关性评分
        List<DocumentChunk> results = new ArrayList<>();
        String lowerQuery = query != null ? query.toLowerCase() : "";

        for (MockDocument doc : candidates) {
            double score = 0.0;
            if (!lowerQuery.isBlank()) {
                String lowerContent = doc.content.toLowerCase();
                String lowerTitle = doc.title.toLowerCase();

                // 计算关键词匹配数量
                for (String keyword : lowerQuery.split("\\s+")) {
                    if (lowerContent.contains(keyword) || lowerTitle.contains(keyword)) {
                        score += 0.2;
                    }
                }
                // 完整匹配加分
                if (lowerContent.contains(lowerQuery)) {
                    score += 0.5;
                }
                if (lowerTitle.contains(lowerQuery)) {
                    score += 0.3;
                }
            } else {
                score = 0.5; // 无查询时给默认分
            }

            if (score > 0 || lowerQuery.isBlank()) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setId(doc.id);
                chunk.setDocumentId(doc.id);
                chunk.setTitle(doc.title);
                chunk.setContent(doc.content);
                chunk.setScore(Math.min(1.0, score));
                chunk.setSourceType(doc.sourceType);
                chunk.setCourseId(doc.courseId);
                chunk.setKnowledgePointId(doc.knowledgePointId);
                results.add(chunk);
            }
        }

        // 按评分降序排列
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 取 topK
        return results.subList(0, Math.min(topK, results.size()));
    }

    private String formatContext(List<DocumentChunk> docs) {
        if (docs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            DocumentChunk doc = docs.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append(doc.getTitle()).append(" (").append(doc.getSourceType()).append(")\n");
            sb.append(doc.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 内部模拟文档类。
     */
    private static class MockDocument {
        final String id;
        final String title;
        final String content;
        final String sourceType;
        final String courseId;
        final String knowledgePointId;

        MockDocument(String id, String title, String content,
                     String sourceType, String courseId, String knowledgePointId) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.sourceType = sourceType;
            this.courseId = courseId;
            this.knowledgePointId = knowledgePointId;
        }
    }
}
