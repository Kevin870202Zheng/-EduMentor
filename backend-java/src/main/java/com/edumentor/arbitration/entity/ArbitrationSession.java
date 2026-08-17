package com.edumentor.arbitration.entity;

import com.edumentor.arbitration.entity.enums.ArbitrationPhase;
import com.edumentor.arbitration.entity.enums.ArbitrationStatus;
import com.edumentor.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 仲裁会话。
 * 同一知识点同一学生存在两个会话：PRE（课前）/ POST（课后），共用同一份案件（case_content）。
 * 学生扮演仲裁人，AI 扮演普通老百姓原/被告；双裁决齐全后生成分析报告。
 * 设计文档: .youcoder/plans/learning-directory-arbitration-design.html (v1.0) §4.7
 */
@Getter
@Setter
@Entity
@Table(name = "arbitration_sessions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_ars_kp_student_phase",
            columnNames = {"knowledge_point_id", "student_id", "phase"})
}, indexes = {
    @Index(name = "idx_ars_kp_student", columnList = "knowledge_point_id, student_id")
})
public class ArbitrationSession extends BaseEntity {

    /** 所属课程（courses.id） */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** 知识点（knowledge_points.id） */
    @Column(name = "knowledge_point_id", nullable = false)
    private UUID knowledgePointId;

    /** 学生（users.id） */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** 阶段：PRE 课前 / POST 课后 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ArbitrationPhase phase;

    /** 会话状态机（见 ArbitrationStatus） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArbitrationStatus status = ArbitrationStatus.CASE_GENERATING;

    /** AI 生成的案件（JSONB，结构见 dto.ArbitrationCase） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String caseContent;

    /** 学生裁决书（JSON：result + reason） */
    @Column(columnDefinition = "text")
    private String award;

    /** AI 分析报告（双裁决对比，Markdown） */
    @Column(columnDefinition = "text")
    private String report;

    /** 当前环节：0陈述/1答辩/2举证/3辩论/4裁决 */
    @Column(name = "stage_index", nullable = false)
    private Integer stageIndex = 0;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("courseId", courseId);
        dto.put("knowledgePointId", knowledgePointId);
        dto.put("studentId", studentId);
        dto.put("phase", phase != null ? phase.name() : null);
        dto.put("status", status != null ? status.name() : null);
        dto.put("caseContent", caseContent);
        dto.put("award", award);
        dto.put("report", report);
        dto.put("stageIndex", stageIndex);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
