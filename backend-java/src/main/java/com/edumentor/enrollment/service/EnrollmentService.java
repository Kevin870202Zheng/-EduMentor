package com.edumentor.enrollment.service;

import com.edumentor.common.exception.DuplicateResourceException;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.course.entity.Course;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.enrollment.dto.EnrollmentDto;
import com.edumentor.enrollment.entity.StudentCourse;
import com.edumentor.enrollment.repository.StudentCourseRepository;
import com.edumentor.learningpath.entity.LearningPath;
import com.edumentor.learningpath.entity.PathStatus;
import com.edumentor.learningpath.repository.LearningPathRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 选课服务 — 学生选课、退课、课程列表查询。
 */
@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final StudentCourseRepository studentCourseRepository;
    private final CourseRepository courseRepository;
    private final LearningPathRepository learningPathRepository;

    public EnrollmentService(StudentCourseRepository studentCourseRepository,
                             CourseRepository courseRepository,
                             LearningPathRepository learningPathRepository) {
        this.studentCourseRepository = studentCourseRepository;
        this.courseRepository = courseRepository;
        this.learningPathRepository = learningPathRepository;
    }

    /**
     * 获取学生的已退课程列表。
     */
    @Transactional(readOnly = true)
    public List<EnrollmentDto> listDroppedCourses(UUID studentId) {
        return studentCourseRepository.findByStudentIdAndStatus(studentId, "dropped")
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
     * 学生选课。
     */
    @Transactional
    public EnrollmentDto enroll(UUID studentId, UUID courseId) {
        log.info("学生选课: studentId={}, courseId={}", studentId, courseId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("课程", courseId));

        // 1. 检查是否已有活跃的选课
        Optional<StudentCourse> existingActive = studentCourseRepository
                .findByStudentIdAndCourseId(studentId, courseId)
                .filter(sc -> "active".equals(sc.getStatus()));
        if (existingActive.isPresent()) {
            throw new DuplicateResourceException("选课", "该学生已选此课程");
        }

        StudentCourse saved;

        // 2. 🔗 联动：如果有退课记录，重用并恢复（保留原始创建时间）
        Optional<StudentCourse> existingDropped = studentCourseRepository
                .findByStudentIdAndCourseId(studentId, courseId)
                .filter(sc -> "dropped".equals(sc.getStatus()));
        if (existingDropped.isPresent()) {
            saved = existingDropped.get();
            saved.setStatus("active");
            saved.setEnrolledAt(LocalDateTime.now());
            saved.setCompletedAt(null);
            saved = studentCourseRepository.save(saved);
            log.info("重新选课成功: enrollmentId={}, 恢复历史选课记录", saved.getId());
        } else {
            // 3. 全新选课
            StudentCourse sc = new StudentCourse();
            sc.setStudentId(studentId);
            sc.setCourseId(courseId);
            sc.setCourseCode(course.getCourseCode());
            sc.setStatus("active");
            sc.setEnrolledAt(LocalDateTime.now());
            saved = studentCourseRepository.save(sc);
            log.info("选课成功: enrollmentId={}", saved.getId());
        }

        // 4. 🔗 联动：恢复之前暂停的学习路径为 DRAFT 状态
        List<LearningPath> paths = learningPathRepository.findByStudentIdAndCourseId(studentId, courseId);
        int restoredCount = 0;
        for (LearningPath path : paths) {
            if (path.getStatus() == PathStatus.PAUSED) {
                path.setStatus(PathStatus.DRAFT);
                restoredCount++;
            }
        }
        if (!paths.isEmpty()) {
            learningPathRepository.saveAll(paths);
        }

        if (restoredCount > 0) {
            log.info("已恢复 {} 条学习路径为草稿状态", restoredCount);
        }

        return EnrollmentDto.fromEntity(saved, course.getName());
    }

    /**
     * 退课。
     */
    @Transactional
    public EnrollmentDto dropCourse(UUID enrollmentId) {
        StudentCourse sc = studentCourseRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("选课记录", enrollmentId));

        UUID studentId = sc.getStudentId();
        UUID courseId = sc.getCourseId();

        // 1. 标记退课
        sc.setStatus("dropped");
        studentCourseRepository.save(sc);

        // 2. 🔗 联动：暂停该课程下的所有活跃/草稿学习路径
        List<LearningPath> paths = learningPathRepository.findByStudentIdAndCourseId(studentId, courseId);
        int pausedCount = 0;
        for (LearningPath path : paths) {
            if (path.getStatus() == PathStatus.ACTIVE || path.getStatus() == PathStatus.DRAFT) {
                path.setStatus(PathStatus.PAUSED);
                pausedCount++;
            }
        }
        if (!paths.isEmpty()) {
            learningPathRepository.saveAll(paths);
        }

        String courseName = courseRepository.findById(courseId)
                .map(Course::getName)
                .orElse("未知课程");

        log.info("退课成功: enrollmentId={}, 已暂停 {} 条学习路径", enrollmentId, pausedCount);
        return EnrollmentDto.fromEntity(sc, courseName);
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
