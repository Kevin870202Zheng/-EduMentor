package com.edumentor.course.repository;

import com.edumentor.course.entity.CourseMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 课程资料 Repository。
 */
@Repository
public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, UUID> {

    List<CourseMaterial> findByCourseIdOrderByCreatedAtDesc(UUID courseId);

    List<CourseMaterial> findByCourseCodeOrderByCreatedAtDesc(String courseCode);

    List<CourseMaterial> findByCourseCodeAndStatus(String courseCode, String status);
}
