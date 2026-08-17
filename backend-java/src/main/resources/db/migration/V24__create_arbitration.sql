-- ═══════════════════════════════════════════════════════════════
-- V24: 仲裁人案例分析（模拟仲裁）— 每知识点每学生 PRE/POST 双阶段
-- 设计文档: .youcoder/plans/learning-directory-arbitration-design.html (v1.0)
-- 学生扮演仲裁人，AI 扮演普通老百姓原/被告（降智）；双裁决齐全后生成分析报告
-- ═══════════════════════════════════════════════════════════════

-- ① 仲裁会话（每知识点每学生两个：PRE 课前 / POST 课后）
CREATE TABLE IF NOT EXISTS arbitration_sessions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id         UUID NOT NULL,
    knowledge_point_id UUID NOT NULL,
    student_id        UUID NOT NULL,
    phase             VARCHAR(8)  NOT NULL CHECK (phase IN ('PRE','POST')),
    status            VARCHAR(20) NOT NULL DEFAULT 'CASE_GENERATING'
                      CHECK (status IN ('CASE_GENERATING','OPENING','HEARING',
                                        'AWARD_READY','AWARDED','REPORTED')),
    case_content      JSONB,                -- AI 生成的案件（结构化 JSON，见 dto.ArbitrationCase）
    award             TEXT,                 -- 学生裁决书（结构化 JSON：result + reason）
    report            TEXT,                 -- AI 分析报告（双裁决对比，Markdown）
    stage_index       INTEGER NOT NULL DEFAULT 0,  -- 当前环节：0陈述/1答辩/2举证/3辩论/4裁决
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (knowledge_point_id, student_id, phase)
);
CREATE INDEX IF NOT EXISTS idx_ars_kp_student ON arbitration_sessions (knowledge_point_id, student_id);

-- ② 仲裁消息（独立表，便于审计/回放）
CREATE TABLE IF NOT EXISTS arbitration_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL,
    role        VARCHAR(16) NOT NULL,  -- CLERK/PLAINTIFF_AI/DEFENDANT_AI/ARBITER_STUDENT
    content     TEXT NOT NULL,
    round_seq   INTEGER NOT NULL DEFAULT 0,  -- 轮次（同轮内按 created_at 排序）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_arm_session ON arbitration_messages (session_id, round_seq);
