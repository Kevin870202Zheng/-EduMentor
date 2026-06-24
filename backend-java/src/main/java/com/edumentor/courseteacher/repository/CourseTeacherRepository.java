package com.edumentor.courseteacher.repository;

import com.edumentor.courseteacher.entity.CourseTeacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseTeacherRepository extends JpaRepository<CourseTeacher, UUID> {
    List<CourseTeacher> findByCourseId(UUID courseId);
    List<CourseTeacher> findByTeacherId(UUID teacherId);
    Optional<CourseTeacher> findByCourseIdAndTeacherId(UUID courseId, UUID teacherId);
    boolean existsByCourseIdAndTeacherId(UUID courseId, UUID teacherId);
}
