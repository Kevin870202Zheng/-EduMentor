package com.edumentor.course.service;

import com.edumentor.course.dto.KnowledgePointGroupDto;
import com.edumentor.course.dto.KnowledgePointDto;
import com.edumentor.course.dto.ThemeDto;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.entity.SubjectTheme;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.course.repository.SubjectThemeRepository;
import com.edumentor.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 跨学段主题服务（PRD v4.0 §11.3 / §11.4）。
 * <p>
 * 提供主题列表（含学段知识点计数）与主题下知识阶梯（按深度分层）查询能力。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeService {

    private final SubjectThemeRepository themeRepository;
    private final KnowledgePointRepository knowledgePointRepository;

    /**
     * 获取主题列表。
     *
     * @param stage 学段代码（可选）：指定时仅返回该学段下知识点数 &gt; 0 的主题，并附带计数
     * @return 主题 DTO 列表（按 sort_order 升序）
     */
    @Transactional(readOnly = true)
    public List<ThemeDto> listThemes(String stage) {
        List<SubjectTheme> themes = themeRepository.findAllByOrderBySortOrderAsc();
        return themes.stream()
                .map(theme -> new ThemeDto(
                        theme.getId(),
                        theme.getSubject(),
                        theme.getCode(),
                        theme.getName(),
                        theme.getDescription(),
                        theme.getIcon(),
                        theme.getSortOrder() != null ? theme.getSortOrder() : 0,
                        countKps(theme.getId(), stage)))
                .filter(dto -> stage == null || stage.isBlank() || dto.kpCount() > 0)
                .toList();
    }

    /**
     * 获取某主题下的知识阶梯（按深度分层）。
     *
     * @param themeId 主题 ID
     * @param stage   学段代码（可选）：指定时仅返回该学段知识点
     * @return 按 depth_level 升序分层的知识点分组
     */
    @Transactional(readOnly = true)
    public List<KnowledgePointGroupDto> listKpsByTheme(UUID themeId, String stage) {
        // 校验主题存在
        themeRepository.findById(themeId)
                .orElseThrow(() -> new ResourceNotFoundException("主题", themeId));

        List<KnowledgePoint> kps = (stage == null || stage.isBlank())
                ? knowledgePointRepository.findByThemeId(themeId)
                : knowledgePointRepository.findByThemeIdAndStageOrderByStageOrderAsc(themeId, stage);

        // 按 depth_level 分组（保持分组插入顺序），深度为空的按 1 处理
        Map<Integer, List<KnowledgePointDto>> grouped = kps.stream()
                .collect(Collectors.groupingBy(
                        kp -> kp.getDepthLevel() != null ? kp.getDepthLevel() : 1,
                        LinkedHashMap::new,
                        Collectors.mapping(KnowledgePointDto::fromEntity, Collectors.toList())));

        return grouped.entrySet().stream()
                .map(e -> new KnowledgePointGroupDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(KnowledgePointGroupDto::depthLevel))
                .toList();
    }

    private long countKps(UUID themeId, String stage) {
        return (stage == null || stage.isBlank())
                ? knowledgePointRepository.countByThemeId(themeId)
                : knowledgePointRepository.countByThemeIdAndStage(themeId, stage);
    }
}
