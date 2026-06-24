package com.edumentor.courseteacher.service;

import com.edumentor.common.exception.DuplicateResourceException;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.courseteacher.dto.AssignTeacherRequest;
import com.edumentor.courseteacher.entity.CourseTeacher;
import com.edumentor.courseteacher.repository.CourseTeacherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourseTeacherService {

    private static final Logger log = LoggerFactory.getLogger(CourseTeacherService.class);

    private final CourseTeacherRepository courseTeacherRepository;

    public CourseTeacherService(CourseTeacherRepository courseTeacherRepository) {
        this.courseTeacherRepository = courseTeacherRepository;
    }

    @Transactional
    public CourseTeacher assignTeacher(AssignTeacherRequest request) {
        if (courseTeacherRepository.existsByCourseIdAndTeacherId(request.getCourseId(), request.getTeacherId())) {
            throw new DuplicateResourceException("教师分配", "该教师已分配到此课程");
        }
        CourseTeacher ct = new CourseTeacher();
        ct.setCourseId(request.getCourseId());
        ct.setTeacherId(request.getTeacherId());
        ct.setRole(request.getRole());
        return courseTeacherRepository.save(ct);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCourseTeachers(UUID courseId) {
        return courseTeacherRepository.findByCourseId(courseId).stream()
                .map(CourseTeacher::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeTeacher(UUID id) {
        if (!courseTeacherRepository.existsById(id)) {
            throw new ResourceNotFoundException("教师分配记录", id);
        }
        courseTeacherRepository.deleteById(id);
    }
}
