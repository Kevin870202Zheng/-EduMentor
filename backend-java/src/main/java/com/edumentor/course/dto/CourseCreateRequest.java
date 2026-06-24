package com.edumentor.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建课程请求 DTO。
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Data
public class CourseCreateRequest {

    @NotBlank(message = "课程编号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{3,32}$", message = "课程编号仅允许字母和数字，长度3-32位")
    private String courseCode;

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
