package com.edumentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagConfig {
    private boolean enabled = true;
    private int topK = 5;
    private int maxContextChars = 3000;
    private String embeddingModel = "text-embedding-3-small";
    private VectorDbConfig vectorDb = new VectorDbConfig();
    private ChunkingConfig chunking = new ChunkingConfig();
    private KeywordIndexConfig keywordIndex = new KeywordIndexConfig();
    private String dataDir = "data/rag";

    @Data
    public static class VectorDbConfig {
        private String type = "memory";
        private String path = "data/vector_store.json";
    }

    @Data
    public static class ChunkingConfig {
        private String strategy = "recursive";
        private int maxChunkSize = 500;
        private int chunkOverlap = 50;
    }

    @Data
    public static class KeywordIndexConfig {
        private boolean enabled = true;
        private List<String> stopWords = List.of();
    }
}
