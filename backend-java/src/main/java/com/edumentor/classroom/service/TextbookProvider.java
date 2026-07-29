package com.edumentor.classroom.service;

import com.edumentor.course.entity.Course;
import com.edumentor.course.entity.CourseMaterial;
import com.edumentor.course.repository.CourseMaterialRepository;
import com.edumentor.course.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 教材原文检索器 — 从 course_materials 表中检索与指定章节相关的教材原文。
 * <p>
 * 用于在课堂生成时作为 prompt 上下文，帮助 LLM 生成与教材一致的内容。
 * </p>
 */
@Component
public class TextbookProvider {

    private static final Logger log = LoggerFactory.getLogger(TextbookProvider.class);

    /** 教材原文最大长度（字符数） */
    private static final int MAX_EXCERPT_LENGTH = 8000;

    private final CourseMaterialRepository courseMaterialRepository;
    private final CourseRepository courseRepository;

    public TextbookProvider(CourseMaterialRepository courseMaterialRepository,
                            CourseRepository courseRepository) {
        this.courseMaterialRepository = courseMaterialRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * 获取指定课程的课程名称。
     *
     * @param courseId 课程 ID
     * @return 课程名称，未找到时返回 "未知课程"
     */
    public String getCourseName(UUID courseId) {
        return courseRepository.findById(courseId)
                .map(Course::getName)
                .orElse("未知课程");
    }

    /**
     * 获取指定课程的教材原文节选。
     * <p>
     * 按课程 ID 查找所有已上传的教材，拼接其 rawText 字段，
     * 截取前 MAX_EXCERPT_LENGTH 个字符。
     * </p>
     *
     * @param courseId 课程 ID
     * @return 教材原文节选，无教材时返回空字符串
     */
    public String getTextbookExcerpt(UUID courseId) {
        try {
            List<CourseMaterial> materials = courseMaterialRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
            if (materials.isEmpty()) {
                log.info("No course materials found for course: {}", courseId);
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (CourseMaterial material : materials) {
                if (material.getRawText() != null && !material.getRawText().isEmpty()) {
                    if (material.getTitle() != null) {
                        sb.append("【").append(material.getTitle()).append("】\n");
                    }
                    sb.append(material.getRawText()).append("\n\n");
                    if (sb.length() > MAX_EXCERPT_LENGTH) {
                        sb.setLength(MAX_EXCERPT_LENGTH);
                        sb.append("\n\n...（以下内容已截断）");
                        break;
                    }
                }
            }

            String result = sb.toString().trim();
            log.info("Retrieved textbook excerpt for course {}, length: {}", courseId, result.length());
            return result;
        } catch (Exception e) {
            log.error("Failed to retrieve textbook excerpt for course: {}", courseId, e);
            return "";
        }
    }
}
