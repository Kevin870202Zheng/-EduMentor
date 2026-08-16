package com.edumentor.course.repository;

import com.edumentor.course.entity.SubjectTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 跨学段主题 Repository — 提供主题定义的数据访问。
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Repository
public interface SubjectThemeRepository extends JpaRepository<SubjectTheme, UUID> {

    Optional<SubjectTheme> findByCode(String code);

    List<SubjectTheme> findAllByOrderBySortOrderAsc();

    List<SubjectTheme> findBySubjectOrderBySortOrderAsc(String subject);
}
