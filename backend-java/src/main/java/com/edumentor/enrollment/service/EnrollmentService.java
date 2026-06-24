package com.edumentor.enrollment.service;

import com.edumentor.common.exception.DuplicateResourceException;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.course.entity.Course;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.enrollment.dto.EnrollmentDto;
import com.edumentor.enrollment.entity.StudentCourse;
import com.edumentor.enrollment.repository.StudentCourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 选课服务 — 学生选课、退课、课程列表查询。
 */
@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final StudentCourseRepository studentCourseRepository;
    private final CourseRepository courseRepository;

    public EnrollmentService(StudentCourseRepository studentCourseRepository,
                             CourseRepository courseRepository) {
        this.studentCourseRepository = studentCourseRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * 学生选课。
     */
    @Transactional
    public EnrollmentDto enroll(UUID studentId, UUID courseId) {
        log.info("学生选课: studentId={}, courseId={}", studentId, courseId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("课程", courseId));

        if (studentCourseRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new DuplicateResourceException("选课", "该学生已选此课程");
        }

        StudentCourse sc = new StudentCourse();
        sc.setStudentId(studentId);
        sc.setCourseId(courseId);
        sc.setCourseCode(course.getCourseCode());
        sc.setStatus("active");
        sc.setEnrolledAt(LocalDateTime.now());

        StudentCourse saved = studentCourseRepository.save(sc);
        log.info("选课成功: enrollmentId={}", saved.getId());

        return EnrollmentDto.fromEntity(saved, course.getName());
    }

    /**
     * 退课。
     */
    @Transactional
    public void dropCourse(UUID enrollmentId) {
        StudentCourse sc = studentCourseRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("选课记录", enrollmentId));

        sc.setStatus("dropped");
        studentCourseRepository.save(sc);
        log.info("退课成功: enrollmentId={}", enrollmentId);
    }

    /**
     * 获取学生的选课列表。
     */
    @Transactional(readOnly = true)
    public List<EnrollmentDto> listStudentCourses(UUID studentId) {
        return studentCourseRepository.findByStudentIdAndStatus(studentId, "active")
                .stream()
                .map(sc -> {
                    String courseName = courseRepository.findById(sc.getCourseId())
                            .map(Course::getName)
                            .orElse("未知课程");
                    return EnrollmentDto.fromEntity(sc, courseName);
                })
                .toList();
    }

    /**
     * 获取课程下的学生列表。
     */
    @Transactional(readOnly = true)
    public List<UUID> listCourseStudents(UUID courseId) {
        return studentCourseRepository.findByCourseId(courseId)
                .stream()
                .filter(sc -> "active".equals(sc.getStatus()))
                .map(StudentCourse::getStudentId)
                .toList();
    }

    /**
     * 获取课程下的学生数量。
     */
    @Transactional(readOnly = true)
    public long countCourseStudents(UUID courseId) {
        return studentCourseRepository.countByCourseId(courseId);
    }
}
