package com.edumentor.engine.rag;

import com.edumentor.engine.embedding.EmbeddingService;
import com.edumentor.engine.embedding.KpEmbedding;
import com.edumentor.engine.embedding.KpEmbeddingRepository;
import com.edumentor.engine.embedding.VectorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量化 RAG 引擎 — 使用 PGVector 余弦相似度检索知识库。
 *
 * <p>
 * 替换 MockRAGEngine，通过 {@code rag.vector-engine=true} 启用。
 * 使用 {@link EmbeddingService} 将用户问题转为向量，然后在 {@code kp_embeddings}
 * 表中进行余弦相似度搜索，找出最相关的知识点内容作为 LLM 上下文。
 * </p>
 *
 * @author EduMentor Team
 */
@Component("ragEngineImpl")
@ConditionalOnProperty(name = "rag.vector-engine", havingValue = "true", matchIfMissing = false)
public class VectorRAGEngine implements RAGEngine {

    private static final Logger log = LoggerFactory.getLogger(VectorRAGEngine.class);

    private final EmbeddingService embeddingService;
    private final KpEmbeddingRepository kpEmbeddingRepository;
    private final VectorizationService vectorizationService;

    public VectorRAGEngine(EmbeddingService embeddingService,
                           KpEmbeddingRepository kpEmbeddingRepository,
                           VectorizationService vectorizationService) {
        this.embeddingService = embeddingService;
        this.kpEmbeddingRepository = kpEmbeddingRepository;
        this.vectorizationService = vectorizationService;
    }

    @Override
    public List<DocumentChunk> retrieve(String query, int topK) {
        return search(query, topK, null, null);
    }

    @Override
    public List<DocumentChunk> retrieveByCourse(String query, String courseId, int topK) {
        if (courseId == null || courseId.isBlank()) {
            return retrieve(query, topK);
        }
        return search(query, topK, courseId, null);
    }

    @Override
    public List<DocumentChunk> retrieveByKnowledgePoint(String query, String knowledgePointId, int topK) {
        if (knowledgePointId == null || knowledgePointId.isBlank()) {
            return retrieve(query, topK);
        }
        return search(query, topK, null, knowledgePointId);
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
        return docs.stream()
                .map(d -> {
                    SourceInfo info = new SourceInfo();
                    info.setTitle(d.getTitle());
                    info.setSnippet(d.getContent());
                    info.setScore(d.getScore());
                    info.setSourceType(d.getSourceType());
                    return info;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean isEnabled() {
        return embeddingService.isAvailable();
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("engine", "VectorRAGEngine");
        status.put("enabled", embeddingService.isAvailable());
        status.put("type", "pgvector_cosine");
        return status;
    }

    /**
     * 核心检索逻辑：将查询转为向量 → 余弦相似度匹配 → 返回 topK。
     */
    private List<DocumentChunk> search(String query, int topK, String courseId, String kpId) {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding 服务不可用，无法执行向量检索");
            return Collections.emptyList();
        }

        // 1. 将查询转为向量
        float[] queryVec = embeddingService.embed(query);
        if (queryVec.length == 0) {
            log.warn("查询向量化为空: query={}", query);
            return Collections.emptyList();
        }

        // 2. 获取候选向量
        List<KpEmbedding> candidates;
        if (courseId != null && !courseId.isBlank()) {
            try {
                UUID cid = UUID.fromString(courseId);
                candidates = kpEmbeddingRepository.findByCourseId(cid);
            } catch (IllegalArgumentException e) {
                log.warn("无效的 courseId: {}", courseId);
                candidates = kpEmbeddingRepository.findAll();
            }
        } else if (kpId != null && !kpId.isBlank()) {
            try {
                UUID kid = UUID.fromString(kpId);
                candidates = kpEmbeddingRepository.findByKpId(kid);
            } catch (IllegalArgumentException e) {
                log.warn("无效的 kpId: {}", kpId);
                candidates = Collections.emptyList();
            }
        } else {
            candidates = kpEmbeddingRepository.findAll();
        }

        if (candidates.isEmpty()) {
            log.info("向量库中无数据，返回空结果");
            return Collections.emptyList();
        }

        // 3. 计算余弦相似度
        List<ScoredChunk> scored = new ArrayList<>();
        for (KpEmbedding entry : candidates) {
            float[] docVec = vectorizationService.jsonToFloatArray(entry.getEmbedding());
            if (docVec.length == 0) continue;

            double similarity = cosineSimilarity(queryVec, docVec);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(entry.getId().toString());
            chunk.setContent(entry.getChunkText());
            chunk.setScore(similarity);
            chunk.setSourceType(entry.getContentType());
            chunk.setCourseId(entry.getCourseId().toString());
            chunk.setKnowledgePointId(entry.getKpId() != null ? entry.getKpId().toString() : null);
            chunk.setTitle(entry.getContentType().equals("kp_content")
                    ? "知识点内容" : entry.getContentType());
            chunk.setMetadata(Map.of(
                    "courseCode", entry.getCourseCode(),
                    "contentType", entry.getContentType()
            ));

            scored.add(new ScoredChunk(chunk, similarity));
        }

        // 4. 按分数排序取 topK
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        int resultSize = Math.min(topK, scored.size());

        log.info("向量检索完成: query={}, 候选={}, 返回={}", query, candidates.size(), resultSize);

        return scored.subList(0, resultSize).stream()
                .map(s -> s.chunk)
                .collect(Collectors.toList());
    }

    /**
     * 计算余弦相似度。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) return 0;

        double dotProduct = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dotProduct / denom;
    }

    /**
     * 将文档片段格式化为 LLM 上下文文本。
     */
    private String formatContext(List<DocumentChunk> docs) {
        if (docs.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("以下是相关的参考资料，请基于这些内容回答学生的问题：\n\n");

        for (int i = 0; i < docs.size(); i++) {
            DocumentChunk doc = docs.get(i);
            sb.append("---\n");
            sb.append("【参考 ").append(i + 1).append("】");
            if (doc.getKnowledgePointId() != null) {
                sb.append(" 知识点：").append(doc.getTitle());
            }
            sb.append("\n");
            sb.append(doc.getContent()).append("\n");
        }

        sb.append("---\n");
        sb.append("请仅基于上述参考资料回答问题。如果参考资料不足以回答问题，请如实告知。");

        return sb.toString();
    }

    /** 带分数的文档片段（排序临时用） */
    private static class ScoredChunk {
        final DocumentChunk chunk;
        final double score;

        ScoredChunk(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
