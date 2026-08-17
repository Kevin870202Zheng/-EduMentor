package com.edumentor.moment.service;

import com.edumentor.diagnosis.repository.StudentProfileRepository;
import com.edumentor.student.entity.StudentProfile;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.moment.dto.LegalReviewResult;
import com.edumentor.moment.entity.Moment;
import com.edumentor.moment.entity.MomentComment;
import com.edumentor.moment.entity.MomentLike;
import com.edumentor.moment.repository.MomentCommentRepository;
import com.edumentor.moment.repository.MomentLikeRepository;
import com.edumentor.moment.repository.MomentRepository;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 同学圈服务：动态发布（含 AI 法律检测）、列表、删除、点赞、评论、图片上传。
 * 设计文档: .youcoder/plans/moments-legal-review-design.html (v1.0) §6
 */
@Service
public class MomentService {

    private static final Logger log = LoggerFactory.getLogger(MomentService.class);

    private static final int MAX_CONTENT_LEN = 500;
    private static final int MAX_COMMENT_LEN = 200;
    private static final int MAX_IMAGES = 9;
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final MomentRepository momentRepository;
    private final MomentLikeRepository likeRepository;
    private final MomentCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LegalReviewService legalReviewService;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public MomentService(MomentRepository momentRepository,
                         MomentLikeRepository likeRepository,
                         MomentCommentRepository commentRepository,
                         UserRepository userRepository,
                         StudentProfileRepository studentProfileRepository,
                         LegalReviewService legalReviewService,
                         LLMService llmService,
                         ObjectMapper objectMapper) {
        this.momentRepository = momentRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.legalReviewService = legalReviewService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════
    //  发布 / 列表 / 删除
    // ═══════════════════════════════════════════════════════════

    /**
     * 发布动态：校验 → AI 法律检测（同步）→ 保存。
     */
    @Transactional
    public Map<String, Object> create(UUID studentId, String content, List<String> images) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("动态内容不能为空");
        }
        if (content.trim().length() > MAX_CONTENT_LEN) {
            throw new IllegalArgumentException("动态内容不能超过 " + MAX_CONTENT_LEN + " 字");
        }
        if (images != null && images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("最多上传 " + MAX_IMAGES + " 张图片");
        }

        // AI 法律检测（同步，失败降级为不提示，不阻塞发布）
        LegalReviewResult review = legalReviewService.review(content.trim());

        Moment moment = new Moment();
        moment.setAuthorId(studentId);
        moment.setContent(content.trim());
        moment.setImages(images == null ? new ArrayList<>() : new ArrayList<>(images));
        if (review != null && Boolean.TRUE.equals(review.getInvolvesLegal())) {
            moment.setAiReview(toJson(review));
        }
        moment = momentRepository.save(moment);
        return momentDetail(moment, studentId);
    }

    /**
     * 动态流（分页，10/页；含作者信息 + 是否本人点赞）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(UUID studentId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 30);
        int safePage = Math.max(page, 0);
        List<Moment> moments = momentRepository.findActive(PageRequest.of(safePage, safeSize));
        List<Map<String, Object>> items = moments.stream()
                .map(m -> momentDetail(m, studentId))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("hasMore", items.size() == safeSize);
        return result;
    }

    /**
     * 删除动态（仅作者本人；软删除）。
     */
    @Transactional
    public void delete(UUID studentId, UUID momentId) {
        Moment moment = requireMoment(momentId);
        if (!moment.getAuthorId().equals(studentId)) {
            throw new IllegalArgumentException("只能删除自己发布的动态");
        }
        moment.setDeleted(true);
        momentRepository.save(moment);
    }

    // ═══════════════════════════════════════════════════════════
    //  点赞 / 评论
    // ═══════════════════════════════════════════════════════════

    /**
     * 点赞/取消（toggle），返回最新状态。
     */
    @Transactional
    public Map<String, Object> toggleLike(UUID studentId, UUID momentId) {
        Moment moment = requireMoment(momentId);
        boolean liked;
        if (likeRepository.existsByMomentIdAndUserId(momentId, studentId)) {
            likeRepository.findByMomentIdAndUserId(momentId, studentId).ifPresent(likeRepository::delete);
            liked = false;
        } else {
            MomentLike like = new MomentLike();
            like.setMomentId(momentId);
            like.setUserId(studentId);
            likeRepository.save(like);
            liked = true;
        }
        long count = likeRepository.countByMomentId(momentId);
        moment.setLikeCount((int) count);
        momentRepository.updateLikeCount(momentId, (int) count);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("liked", liked);
        result.put("likeCount", count);
        return result;
    }

    /**
     * 评论列表（含评论者昵称/学段）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listComments(UUID momentId) {
        requireMoment(momentId);
        return commentRepository.findByMomentIdOrderByCreatedAtAsc(momentId).stream()
                .map(this::commentDetail)
                .toList();
    }

    /**
     * 发表评论。
     */
    @Transactional
    public Map<String, Object> addComment(UUID studentId, UUID momentId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (content.trim().length() > MAX_COMMENT_LEN) {
            throw new IllegalArgumentException("评论不能超过 " + MAX_COMMENT_LEN + " 字");
        }
        Moment moment = requireMoment(momentId);
        MomentComment comment = new MomentComment();
        comment.setMomentId(momentId);
        comment.setUserId(studentId);
        comment.setContent(content.trim());
        comment = commentRepository.save(comment);

        long count = commentRepository.countByMomentId(momentId);
        moment.setCommentCount((int) count);
        momentRepository.updateCommentCount(momentId, (int) count);

        return commentDetail(comment);
    }

    // ═══════════════════════════════════════════════════════════
    //  重新分析（AI 补检）
    // ═══════════════════════════════════════════════════════════

    /**
     * 重新执行 AI 法律检测（仅作者本人）。
     */
    @Transactional
    public Map<String, Object> reReview(UUID studentId, UUID momentId) {
        Moment moment = requireMoment(momentId);
        if (!moment.getAuthorId().equals(studentId)) {
            throw new IllegalArgumentException("只能重新分析自己发布的动态");
        }
        LegalReviewResult review = legalReviewService.review(moment.getContent());
        moment.setAiReview(review != null && Boolean.TRUE.equals(review.getInvolvesLegal())
                ? toJson(review) : null);
        momentRepository.save(moment);
        return momentDetail(moment, studentId);
    }

    // ═══════════════════════════════════════════════════════════
    //  图片上传
    // ═══════════════════════════════════════════════════════════

    /**
     * 保存本地图片，返回可访问 URL（/uploads/moments/xxx.jpg）。
     */
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 10MB");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_IMAGE_EXT.contains(ext)) {
            throw new IllegalArgumentException("仅支持 jpg/png/gif/webp 格式图片");
        }
        try {
            Path dir = Path.of("uploads", "moments");
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "." + ext;
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            log.info("同学圈图片已保存: {}", target);
            return "/uploads/moments/" + filename;
        } catch (IOException e) {
            log.error("保存同学圈图片失败: {}", e.getMessage());
            throw new IllegalArgumentException("图片保存失败，请重试");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  内部
    // ═══════════════════════════════════════════════════════════

    private Moment requireMoment(UUID momentId) {
        return momentRepository.findById(momentId)
                .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("动态不存在或已删除"));
    }

    private Map<String, Object> momentDetail(Moment moment, UUID viewerId) {
        Map<String, Object> dto = moment.toDto();
        dto.put("author", authorBrief(moment.getAuthorId()));
        dto.put("likedByMe", likeRepository.existsByMomentIdAndUserId(moment.getId(), viewerId));
        dto.put("aiReview", parseReview(moment.getAiReview()));
        return dto;
    }

    private Map<String, Object> commentDetail(MomentComment comment) {
        Map<String, Object> dto = comment.toDto();
        dto.put("author", authorBrief(comment.getUserId()));
        return dto;
    }

    /** 作者简要信息：昵称 / 头像 / 学段 */
    private Map<String, Object> authorBrief(UUID userId) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("id", userId);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            brief.put("displayName", "未知同学");
            brief.put("avatarUrl", null);
            brief.put("stage", null);
            return brief;
        }
        brief.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        brief.put("avatarUrl", user.getAvatarUrl());
        brief.put("stage", studentProfileRepository.findByUserId(userId)
                .map(StudentProfile::getStage)
                .orElse(null));
        return brief;
    }

    private Object parseReview(String aiReviewJson) {
        if (aiReviewJson == null || aiReviewJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(aiReviewJson, Object.class);
        } catch (Exception e) {
            log.warn("解析 AI 检测结果失败: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
