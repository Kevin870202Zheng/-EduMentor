package com.edumentor.course.dto;

import com.edumentor.course.entity.Course;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 课程响应 DTO。
 * <p>
 * 用于返回课程信息的标准格式，不包含敏感或内部字段。
 * 可从 {@link Course} 实体通过静态工厂方法转换。
 * </p>
 *
 * @param id          课程 ID
 * @param courseCode  课程编号（业务唯一标识）
 * @param name        课程名称
 * @param description 课程描述
 * @param subject     学科分类
 * @param gradeLevel  适用年级
 * @param stage       所属学段（PRIMARY/JUNIOR/SENIOR/UNIVERSITY）
 * @param coverUrl    封面图片 URL
 * @param isPublished 是否已发布
 * @param createdBy   创建人 ID
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 *
 * @author EduMentor Team
 * @version 1.0
 */
public record CourseDto(
        UUID id,
        String courseCode,
        String name,
        String description,
        String subject,
        String gradeLevel,
        String stage,
        String coverUrl,
        boolean isPublished,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 从 {@link Course} 实体转换为 DTO。
     *
     * @param entity 课程实体
     * @return 课程 DTO
     */
    public static CourseDto fromEntity(Course entity) {
        return new CourseDto(
                entity.getId(),
                entity.getCourseCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getSubject(),
                entity.getGradeLevel(),
                entity.getStage(),
                entity.getCoverUrl(),
                Boolean.TRUE.equals(entity.getIsPublished()),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
