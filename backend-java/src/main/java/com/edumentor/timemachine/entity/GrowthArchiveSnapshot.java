package com.edumentor.timemachine.entity;

import com.edumentor.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 成长档案快照（成长时光机）— 按学段归档的学习数据。
 * <p>
 * 学段晋升或手动归档时生成，记录该学段下的主题掌握度、答题统计、薄弱点等，
 * 用于跨学段成长曲线与学习报告。
 * </p>
 */
@Getter
@Setter
@Entity
@Table(name = "growth_archive_snapshots", indexes = {
    @Index(name = "idx_gas_student", columnList = "student_id, created_at ASC")
})
public class GrowthArchiveSnapshot extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** 归档时所在学段 */
    @Column(length = 16)
    private String stage;

    @Column(name = "course_id")
    private UUID courseId;

    /** 归档摘要 JSON：{totalQuestions, accuracyRate, themeMastery[], weakKps[], ...} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String summary = "{}";

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("studentId", studentId);
        dto.put("stage", stage);
        dto.put("courseId", courseId);
        dto.put("summary", summary);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
