-- ============================================================================
-- V23__create_time_machine.sql
-- 成长时光机 — 数据模型落库（v2 版本规划 §5.3）
--
-- 包含：
--   ① time_machine_letters         来自过去的信（跨学段自我对话）
--   ② growth_archive_snapshots     成长档案快照（晋升/手动归档）
--
-- 约定：全部使用 IF NOT EXISTS 保证幂等，可重复执行
-- ============================================================================

-- ============================================================================
-- ① time_machine_letters — 来自过去的信
--    direction: PAST_TO_NOW（过去→现在）| NOW_TO_FUTURE（现在→未来）
-- ============================================================================
CREATE TABLE IF NOT EXISTS time_machine_letters (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id    UUID        NOT NULL REFERENCES users(id),
    stage         VARCHAR(16),                          -- 写信人所在学段（过去的自己）
    course_id     UUID,                                 -- 可选：关联课程
    direction     VARCHAR(16) NOT NULL DEFAULT 'PAST_TO_NOW',
    question      TEXT        NOT NULL,                 -- 来自过去的提问
    answer        TEXT,                                 -- 现在的回答
    ai_generated  BOOLEAN     NOT NULL DEFAULT TRUE,    -- 提问是否 AI 生成
    answered_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tml_student ON time_machine_letters (student_id, created_at DESC);

-- ============================================================================
-- ② growth_archive_snapshots — 成长档案快照
--    summary JSON: { totalQuestions, accuracyRate, courseId, themeMastery:[{themeId,themeName,mastery,total}], weakKps:[{kpId,kpName,mastery}], stage }
-- ============================================================================
CREATE TABLE IF NOT EXISTS growth_archive_snapshots (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id    UUID        NOT NULL REFERENCES users(id),
    stage         VARCHAR(16),                          -- 归档时所在学段
    course_id     UUID,
    summary       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gas_student ON growth_archive_snapshots (student_id, created_at ASC);
