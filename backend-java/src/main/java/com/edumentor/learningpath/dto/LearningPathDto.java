package com.edumentor.learningpath.dto;

import com.edumentor.learningpath.entity.LearningPath;
import com.edumentor.learningpath.entity.LearningPathNode;
import com.edumentor.learningpath.entity.PathStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学习路径 DTO — 用于路径列表和详情展示。
 * <p>
 * 包含路径基本信息及关联的节点列表。
 * </p>
 *
 * @author EduMentor Team
 */
@Data
public class LearningPathDto {

    private UUID id;
    private UUID studentId;
    private UUID courseId;
    private UUID createdBy;
    private String name;
    private String description;
    private PathStatus status;
    private Integer progress;
    private Integer totalNodes;
    private Integer completedNodes;
    private Integer dailyMinutes;
    private List<LearningPathNodeDto> nodes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将 LearningPath 实体转换为 DTO。
     *
     * @param entity 学习路径实体
     * @return DTO 对象
     */
    public static LearningPathDto fromEntity(LearningPath entity) {
        if (entity == null) {
            return null;
        }
        LearningPathDto dto = new LearningPathDto();
        dto.setId(entity.getId());
        dto.setStudentId(entity.getStudentId());
        dto.setCourseId(entity.getCourseId());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus());
        dto.setProgress(entity.getProgress());
        dto.setTotalNodes(entity.getTotalNodes());
        dto.setCompletedNodes(entity.getCompletedNodes());
        dto.setDailyMinutes(entity.getDailyMinutes());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 转换节点列表
        if (entity.getNodes() != null && !entity.getNodes().isEmpty()) {
            dto.setNodes(entity.getNodes().stream()
                    .map(LearningPathNodeDto::fromEntity)
                    .sorted(Comparator.comparingInt(LearningPathNodeDto::getOrderIndex))
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}
