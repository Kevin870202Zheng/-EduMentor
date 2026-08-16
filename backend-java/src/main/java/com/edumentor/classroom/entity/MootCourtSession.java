package com.edumentor.classroom.entity;

import com.edumentor.classroom.entity.enums.MootCourtPhase;
import com.edumentor.classroom.entity.enums.MootCourtStatus;
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
 * 模拟法庭会话。
 * 同一课堂同一学生存在两个会话：PRE（课前）/ POST（课后），共用同一份案件（case_content）。
 * 学生扮演法官，AI 扮演原被告；两份判决齐全后生成分析报告。
 */
@Getter
@Setter
@Entity
@Table(name = "moot_court_sessions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_mcs_classroom_student_phase",
            columnNames = {"classroom_id", "student_id", "phase"})
}, indexes = {
    @Index(name = "idx_mcs_classroom", columnList = "classroom_id"),
    @Index(name = "idx_mcs_student", columnList = "student_id")
})
public class MootCourtSession extends BaseEntity {

    /** 关联课堂（classrooms.id） */
    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    /** 学生（users.id） */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** 阶段：PRE 课前 / POST 课后 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private MootCourtPhase phase;

    /** 会话状态机（见 MootCourtStatus） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MootCourtStatus status = MootCourtStatus.CASE_GENERATING;

    /** AI 生成的案件（JSONB，结构见 dto.MootCourtCase） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String caseContent;

    /** 学生判决书（JSON：result + reason） */
    @Column(columnDefinition = "text")
    private String judgment;

    /** AI 分析报告（两份判决对比，Markdown） */
    @Column(columnDefinition = "text")
    private String report;

    /** 当前庭审环节：0陈述/1答辩/2举证/3辩论/4判决 */
    @Column(name = "stage_index", nullable = false)
    private Integer stageIndex = 0;

    @Override
    public Map<String, Object> toDto() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", getId());
        dto.put("classroomId", classroomId);
        dto.put("studentId", studentId);
        dto.put("phase", phase != null ? phase.name() : null);
        dto.put("status", status != null ? status.name() : null);
        dto.put("caseContent", caseContent);
        dto.put("judgment", judgment);
        dto.put("report", report);
        dto.put("stageIndex", stageIndex);
        dto.put("createdAt", getCreatedAt());
        dto.put("updatedAt", getUpdatedAt());
        return dto;
    }
}
