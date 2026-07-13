package com.edumentor.engine.rag;

import com.edumentor.engine.embedding.EmbeddingService;
import com.edumentor.engine.embedding.KpEmbedding;
import com.edumentor.engine.embedding.KpEmbeddingRepository;
import com.edumentor.engine.embedding.VectorizationService;
import jakarta.annotation.PostConstruct;
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
 * P1 优化：启动时预加载所有嵌入向量到内存（{@link #embeddingCache}），
 * 避免每次查询从数据库加载 JSON 并反序列化。
 * 同时加入 {@link #searchCache} 对相同查询结果做 LRU 缓存（30 分钟 TTL）。
 * </p>
 *
 * @author EduMentor Team
 */
@Component("ragEngineImpl")
@ConditionalOnProperty(name = "rag.vector-engine", havingValue = "true", matchIfMissing = false)
public class VectorRAGEngine implements RAGEngine {

    private static final Logger log = LoggerFactory.getLogger(VectorRAGEngine.class);

    private static final int SEARCH_CACHE_MAX = 200;
    private static final long SEARCH_CACHE_TTL_MS = 30 * 60 * 1000L; // 30 分钟

    private final EmbeddingService embeddingService;
    private final KpEmbeddingRepository kpEmbeddingRepository;
    private final VectorizationService vectorizationService;

    /** 嵌入向量缓存：kpEmbeddingId → float[]，启动时预加载，避免每次查询 JSON 反序列化 */
    private volatile Map<UUID, float[]> embeddingCache = Collections.emptyMap();

    /** 全量嵌入数据缓存，用于构建 DocumentChunk 返回结果 */
    private volatile List<KpEmbedding> embeddingList = Collections.emptyList();

    /** 检索结果 LRU 缓存 */
    private final LinkedHashMap<String, CacheEntry> searchCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > SEARCH_CACHE_MAX;
        }
    };

    public VectorRAGEngine(EmbeddingService embeddingService,
                           KpEmbeddingRepository kpEmbeddingRepository,
                           VectorizationService vectorizationService) {
        this.embeddingService = embeddingService;
        this.kpEmbeddingRepository = kpEmbeddingRepository;
        this.vectorizationService = vectorizationService;
    }

    /**
     * 启动时预加载嵌入向量到内存。
     * 仅在 Embedding 服务可用时执行。
     */
    @PostConstruct
    public void initCache() {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding 服务不可用，跳过预加载向量缓存");
            return;
        }
        try {
            List<KpEmbedding> all = kpEmbeddingRepository.findAll();
            if (all.isEmpty()) {
                log.info("向量库中无数据，跳过预加载");
                return;
            }
            Map<UUID, float[]> cache = new HashMap<>(all.size());
            for (KpEmbedding entry : all) {
                float[] vec = vectorizationService.jsonToFloatArray(entry.getEmbedding());
                if (vec.length > 0) {
                    cache.put(entry.getId(), vec);
                }
            }
            this.embeddingCache = cache;
            this.embeddingList = all;
            log.info("嵌入向量预加载完成：共 {} 条，有效向量 {} 条", all.size(), cache.size());
        } catch (Exception e) {
            log.error("预加载嵌入向量失败，将使用按需加载：{}", e.getMessage());
        }
    }

    @Override
    public List<DocumentChunk> retrieve(String question, int topK) {
        return search(question, topK, null, null);
    }

    @Override
    public List<DocumentChunk> retrieveByCourse(String question, String courseId, int topK) {
        return search(question, topK, courseId, null);
    }

    @Override
    public List<DocumentChunk> retrieveByKnowledgePoint(String question, String kpId, int topK) {
        return search(question, topK, null, kpId);
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
        List<DocumentChunk> docs = search(query, topK, null, null);
        return docs.stream().map(doc -> {
            SourceInfo info = new SourceInfo();
            info.setTitle(doc.getTitle());
            info.setSnippet(doc.getContent());
            info.setScore(doc.getScore());
            info.setSourceType(doc.getSourceType());
            return info;
        }).collect(Collectors.toList());
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
        status.put("cacheSize", embeddingCache.size());
        status.put("totalEmbeddings", embeddingList.size());
        return status;
    }

    /**
     * 核心检索逻辑：将查询转为向量 → 余弦相似度匹配 → 返回 topK。
     * 使用预加载的内存缓存 {@link #embeddingCache} 避免每次 JSON 反序列化，
     * 并用 {@link #searchCache} 缓存相同查询的结果。
     */
    private List<DocumentChunk> search(String query, int topK, String courseId, String kpId) {
        // 1. 检查结果缓存
        String cacheKey = query + "::" + courseId + "::" + kpId + "::" + topK;
        synchronized (searchCache) {
            CacheEntry cached = searchCache.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - cached.timestamp < SEARCH_CACHE_TTL_MS) {
                log.debug("搜索缓存命中: query={}", query);
                return cached.results;
            }
        }

        // 2. Embedding 可用性检查
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding 服务不可用，无法执行向量检索");
            return Collections.emptyList();
        }

        // 3. 将查询转为向量
        float[] queryVec = embeddingService.embed(query);
        if (queryVec.length == 0) {
            log.warn("查询向量化为空: query={}", query);
            return Collections.emptyList();
        }

        // 4. 从内存缓存中获取候选向量（无 DB 查询 + 无 JSON 反序列化）
        List<KpEmbedding> candidates = filterCandidates(courseId, kpId);
        if (candidates.isEmpty()) {
            log.info("向量库中无数据，返回空结果");
            return Collections.emptyList();
        }

        // 5. 计算余弦相似度
        List<ScoredChunk> scored = new ArrayList<>();
        for (KpEmbedding entry : candidates) {
            float[] docVec = embeddingCache.get(entry.getId());
            if (docVec == null || docVec.length == 0) continue;

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

        // 6. 按分数排序取 topK
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        int resultSize = Math.min(topK, scored.size());
        List<DocumentChunk> results = scored.subList(0, resultSize).stream()
                .map(s -> s.chunk)
                .collect(Collectors.toList());

        log.info("向量检索完成: query={}, 候选={}, 返回={}, 缓存={}",
                query, candidates.size(), resultSize, embeddingCache.size());

        // 7. 写入结果缓存
        synchronized (searchCache) {
            searchCache.put(cacheKey, new CacheEntry(results, System.currentTimeMillis()));
        }
        return results;
    }

    /**
     * 从内存缓存中按 courseId/kpId 过滤候选嵌入数据。
     * 当 embeddingCache 未初始化（为空）时回退到从 DB 按需加载+反序列化。
     */
    private List<KpEmbedding> filterCandidates(String courseId, String kpId) {
        // 优先使用内存缓存
        if (!embeddingCache.isEmpty()) {
            List<KpEmbedding> list = embeddingList;
            try {
                if (courseId != null && !courseId.isBlank()) {
                    UUID cid = UUID.fromString(courseId);
                    list = list.stream()
                            .filter(e -> e.getCourseId().equals(cid))
                            .collect(Collectors.toList());
                } else if (kpId != null && !kpId.isBlank()) {
                    UUID kid = UUID.fromString(kpId);
                    list = list.stream()
                            .filter(e -> e.getKpId() != null && e.getKpId().equals(kid))
                            .collect(Collectors.toList());
                }
            } catch (IllegalArgumentException e) {
                log.warn("无效的 courseId/kpId: {} / {}", courseId, kpId);
            }
            return list;
        }

        // 回退：从 DB 按需加载（缓存未初始化时）
        log.warn("嵌入向量缓存为空，回退到 DB 按需加载");
        try {
            if (courseId != null && !courseId.isBlank()) {
                UUID cid = UUID.fromString(courseId);
                return kpEmbeddingRepository.findByCourseId(cid);
            } else if (kpId != null && !kpId.isBlank()) {
                UUID kid = UUID.fromString(kpId);
                return kpEmbeddingRepository.findByKpId(kid);
            } else {
                return kpEmbeddingRepository.findAll();
            }
        } catch (IllegalArgumentException e) {
            log.warn("无效的 courseId/kpId: {} / {}", courseId, kpId);
            return kpEmbeddingRepository.findAll();
        }
    }

    /**
     * 计算余弦相似度。
     */
    private double cosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA.length != vecB.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dot += (double) vecA[i] * vecB[i];
            normA += (double) vecA[i] * vecA[i];
            normB += (double) vecB[i] * vecB[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    /**
     * 将文档片段列表格式化为文本上下文。
     */
    private String formatContext(List<DocumentChunk> docs) {
        if (docs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            DocumentChunk doc = docs.get(i);
            sb.append("[").append(i + 1).append("] ");
            if (doc.getTitle() != null && !doc.getTitle().isBlank()) {
                sb.append(doc.getTitle()).append(" — ");
            }
            sb.append(doc.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 搜索结果缓存条目。
     */
    private static class CacheEntry {
        final List<DocumentChunk> results;
        final long timestamp;

        CacheEntry(List<DocumentChunk> results, long timestamp) {
            this.results = results;
            this.timestamp = timestamp;
        }
    }

    /**
     * 带分数的文档片段（内部排序用）。
     */
    private static class ScoredChunk {
        final DocumentChunk chunk;
        final double score;

        ScoredChunk(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
