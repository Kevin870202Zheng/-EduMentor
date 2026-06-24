package com.edumentor.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建课程请求 DTO。
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Data
public class CourseCreateRequest {

    @NotBlank(message = "课程名称不能为空")
    private String name;

    @NotBlank(message = "课程描述不能为空")
    private String description;

    @NotBlank(message = "学科分类不能为空")
    private String subject;

    @NotBlank(message = "适用年级不能为空")
    private String gradeLevel;

    private String coverUrl;
}
