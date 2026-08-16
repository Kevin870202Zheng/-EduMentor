package com.edumentor.course.service;

import com.edumentor.course.entity.EducationStage;
import com.edumentor.course.repository.EducationStageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学段服务（PRD v4.0 §11.2）。
 * <p>
 * 提供学段定义的查询能力，供 StageSelector 组件使用。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StageService {

    private final EducationStageRepository stageRepository;

    /**
     * 获取所有学段定义（按 sort_order 升序）。
     *
     * @return 学段列表：小学 → 初中 → 高中 → 大学
     */
    @Transactional(readOnly = true)
    public List<EducationStage> listStages() {
        return stageRepository.findAllByOrderBySortOrderAsc();
    }
}
