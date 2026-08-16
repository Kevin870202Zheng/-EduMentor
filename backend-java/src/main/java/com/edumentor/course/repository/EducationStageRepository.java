package com.edumentor.course.repository;

import com.edumentor.course.entity.EducationStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学段 Repository — 提供学段定义的数据访问。
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Repository
public interface EducationStageRepository extends JpaRepository<EducationStage, UUID> {

    Optional<EducationStage> findByCode(String code);

    List<EducationStage> findAllByOrderBySortOrderAsc();
}
