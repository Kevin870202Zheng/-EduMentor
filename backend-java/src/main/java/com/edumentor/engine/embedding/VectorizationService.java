package com.edumentor.engine.embedding;

import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量化服务 — 将课程知识点内容向量化并写入 kp_embeddings 表。
 *
 * <p>
 * 在线程中调用 {@link EmbeddingService} 生成向量，然后写入数据库。
 * 支持单课程向量化和全量重建。
 * </p>
 *
 * @author EduMentor Team
 */
@Service
public class VectorizationService {

    private static final Logger log = LoggerFactory.getLogger(VectorizationService.class);

    private final EmbeddingService embeddingService;
    private final KpEmbeddingRepository kpEmbeddingRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;

    public VectorizationService(EmbeddingService embeddingService,
                                KpEmbeddingRepository kpEmbeddingRepository,
                                KnowledgePointRepository knowledgePointRepository,
                                CourseRepository courseRepository,
                                ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.kpEmbeddingRepository = kpEmbeddingRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.courseRepository = courseRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 增量向量化 — 只处理新增的知识点，已有的跳过。
     * 发布新资料时调用此方法，避免全量重建。
     *
     * @param courseId   课程 ID
     * @param courseCode 课程编号
     * @return 新增的向量化记录数
     */
    @Transactional
    public int vectorizeIncremental(UUID courseId, String courseCode) {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding 服务不可用，跳过增量向量化");
            return 0;
        }

        // 获取该课程的所有知识点
        List<KnowledgePoint> allKps = knowledgePointRepository.findByCourseId(courseId);
        if (allKps.isEmpty()) {
            log.info("课程 {} 无知识点，跳过增量向量化", courseCode);
            return 0;
        }

        // 获取已向量化的知识点 ID 集合
        Set<UUID> existingKpIds = kpEmbeddingRepository.findByCourseId(courseId).stream()
                .map(KpEmbedding::getKpId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 只筛选新增的知识点
        List<KnowledgePoint> newKps = allKps.stream()
                .filter(kp -> !existingKpIds.contains(kp.getId()))
                .collect(Collectors.toList());

        if (newKps.isEmpty()) {
            log.info("课程 {} 无新增知识点，跳过向量化", courseCode);
            return 0;
        }

        log.info("增量向量化课程 {}: 已有 {} 条，新增 {} 条",
                courseCode, existingKpIds.size(), newKps.size());

        // 构建待向量化的文本
        List<String> texts = new ArrayList<>();
        List<UUID> kpIds = new ArrayList<>();
        for (KnowledgePoint kp : newKps) {
            texts.add(buildKpText(kp));
            kpIds.add(kp.getId());
        }

        // 批量生成向量
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // 只写入新增的记录
        int count = 0;
        for (int i = 0; i < texts.size(); i++) {
            if (i >= embeddings.size()) break;
            float[] vec = embeddings.get(i);
            if (vec.length == 0) continue;

            KpEmbedding record = new KpEmbedding();
            record.setKpId(kpIds.get(i));
            record.setContentType("kp_content");
            record.setChunkText(texts.get(i));
            record.setEmbedding(floatArrayToJson(vec));
            record.setCourseId(courseId);
            record.setCourseCode(courseCode);
            record.setMetadata("{}");
            kpEmbeddingRepository.save(record);
            count++;
        }

        log.info("课程 {} 增量向量化完成: 新增 {} 条", courseCode, count);
        return count;
    }

    /**
     * 判断 Embedding 引擎是否可用。
     */
    public boolean isEmbeddingAvailable() {
        return embeddingService.isAvailable();
    }

    /**
     * 向量化指定课程的所有知识点内容。
     *
     * @param courseId   课程 ID
     * @param courseCode 课程编号
     * @return 向量化的记录数
     */
    @Transactional
    public int vectorizeCourse(UUID courseId, String courseCode) {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding 服务不可用（当前供应商不支持），跳过向量化");
            return 0;
        }

        List<KnowledgePoint> kps = knowledgePointRepository.findByCourseId(courseId);
        if (kps.isEmpty()) {
            log.info("课程 {} 无知识点，跳过向量化", courseCode);
            return 0;
        }

        // 清除旧的向量数据
        kpEmbeddingRepository.deleteByCourseId(courseId);

        // 构建待向量化的文本列表
        List<String> texts = new ArrayList<>();
        List<String> contentTypes = new ArrayList<>();
        List<UUID> kpIds = new ArrayList<>();

        for (KnowledgePoint kp : kps) {
            // 知识点内容
            String kpText = buildKpText(kp);
            texts.add(kpText);
            contentTypes.add("kp_content");
            kpIds.add(kp.getId());
        }

        // 批量生成向量
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // 写入数据库
        int count = 0;
        for (int i = 0; i < texts.size(); i++) {
            if (i >= embeddings.size()) break;
            float[] vec = embeddings.get(i);
            if (vec.length == 0) continue; // 跳过生成失败的

            KpEmbedding record = new KpEmbedding();
            record.setKpId(kpIds.get(i));
            record.setContentType(contentTypes.get(i));
            record.setChunkText(texts.get(i));
            record.setEmbedding(floatArrayToJson(vec));
            record.setCourseId(courseId);
            record.setCourseCode(courseCode);
            record.setMetadata("{}");
            kpEmbeddingRepository.save(record);
            count++;
        }

        log.info("课程 {} 向量化完成: {} 条记录", courseCode, count);
        return count;
    }

    /**
     * 构建知识点的向量化文本（名称 + 描述 + 内容）。
     */
    private String buildKpText(KnowledgePoint kp) {
        StringBuilder sb = new StringBuilder();
        sb.append("知识点：").append(kp.getName());
        if (kp.getDescription() != null && !kp.getDescription().isBlank()) {
            sb.append("\n描述：").append(kp.getDescription());
        }
        if (kp.getContent() != null && !kp.getContent().isBlank()) {
            sb.append("\n内容：").append(kp.getContent());
        }
        return sb.toString();
    }

    /**
     * 将 float[] 转为 JSON 数组字符串（用于存储到 kp_embeddings.embedding）。
     */
    private String floatArrayToJson(float[] arr) {
        try {
            double[] doubleArr = new double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                doubleArr[i] = arr[i];
            }
            return objectMapper.writeValueAsString(doubleArr);
        } catch (Exception e) {
            log.warn("向量序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 将 JSON 数组字符串转为 float[]。
     */
    public float[] jsonToFloatArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return new float[0];
        try {
            double[] doubleArr = objectMapper.readValue(json, double[].class);
            float[] result = new float[doubleArr.length];
            for (int i = 0; i < doubleArr.length; i++) {
                result[i] = (float) doubleArr[i];
            }
            return result;
        } catch (Exception e) {
            log.warn("向量反序列化失败: {}", e.getMessage());
            return new float[0];
        }
    }
}
