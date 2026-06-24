package com.edumentor.enrollment.repository;

import com.edumentor.enrollment.entity.StudentCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学生选课数据访问层。
 */
@Repository
public interface StudentCourseRepository extends JpaRepository<StudentCourse, UUID> {

    List<StudentCourse> findByStudentIdAndStatus(UUID studentId, String status);

    List<StudentCourse> findByCourseId(UUID courseId);

    Optional<StudentCourse> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    long countByCourseId(UUID courseId);
}
