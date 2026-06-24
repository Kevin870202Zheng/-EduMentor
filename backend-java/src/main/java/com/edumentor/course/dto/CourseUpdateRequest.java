package com.edumentor.course.dto;

import lombok.Data;
import jakarta.validation.constraints.Pattern;

/**
 * 更新课程请求 DTO。
 * <p>
 * 所有字段均为可选，只更新不为 null 的字段。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Data
public class CourseUpdateRequest {

    @Pattern(regexp = "^[A-Za-z0-9]{3,32}$", message = "课程编号仅允许字母和数字，长度3-32位")
    private String courseCode;

    private String name;
    private String description;
    private String subject;
    private String gradeLevel;
    private String coverUrl;
    private Boolean isPublished;
}
