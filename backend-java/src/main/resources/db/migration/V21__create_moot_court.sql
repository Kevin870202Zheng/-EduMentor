-- ═══════════════════════════════════════════════════════════════
-- V21: 模拟法庭（moot court）— 课后/PRE+POST 双阶段庭审
-- 设计文档: .youcoder/plans/moot-court-design.html (v1.1)
-- ═══════════════════════════════════════════════════════════════

-- ① 模拟法庭会话（每个学生每个课堂两个：PRE 课前 / POST 课后）
CREATE TABLE IF NOT EXISTS moot_court_sessions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id UUID NOT NULL,
    student_id   UUID NOT NULL,
    phase        VARCHAR(8)  NOT NULL CHECK (phase IN ('PRE','POST')),
    status       VARCHAR(20) NOT NULL DEFAULT 'CASE_GENERATING'
                 CHECK (status IN ('CASE_GENERATING','OPENING','HEARING',
                                   'JUDGMENT_READY','JUDGED','REPORTED')),
    case_content JSONB,                -- AI 生成的案件（结构化 JSON，见 MootCourtCase）
    judgment     TEXT,                 -- 学生判决书（结构化 JSON：result + reason）
    report       TEXT,                 -- AI 分析报告（两份判决对比，Markdown 文本）
    stage_index  INTEGER NOT NULL DEFAULT 0,  -- 当前庭审环节（0陈述/1答辩/2举证/3辩论/4判决）
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (classroom_id, student_id, phase)
);
CREATE INDEX IF NOT EXISTS idx_mcs_classroom ON moot_court_sessions (classroom_id);
CREATE INDEX IF NOT EXISTS idx_mcs_student   ON moot_court_sessions (student_id);

-- ② 庭审消息（独立表，便于审计/分页/回放）
CREATE TABLE IF NOT EXISTS moot_court_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL,
    role        VARCHAR(16) NOT NULL,  -- SYSTEM/CLERK/PLAINTIFF_AI/DEFENDANT_AI/JUDGE_STUDENT
    content     TEXT NOT NULL,
    round_seq   INTEGER NOT NULL DEFAULT 0,  -- 庭审轮次（同轮内按 created_at 排序）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mcm_session ON moot_court_messages (session_id, round_seq);

