package com.edumentor.student.dto;

import lombok.Data;

/**
 * 学生个人信息更新请求。
 */
@Data
public class StudentProfileUpdateRequest {
    /** 所属学段（PRIMARY/JUNIOR/SENIOR/UNIVERSITY），可选 */
    private String stage;
    private String grade;
    private String className;
    private String major;
    private String department;
    private String college;
    private String learningStyle;
    private Integer dailyStudyMinutes;
}
