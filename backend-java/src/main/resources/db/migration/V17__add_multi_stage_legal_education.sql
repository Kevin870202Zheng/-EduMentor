-- ============================================================================
-- V17__add_multi_stage_legal_education.sql
-- 多学段法律课程体系 — 数据模型落库（PRD v4.0 §10.2 / §15）
--
-- 包含：
--   ① education_stages   学段定义表（PRIMARY/JUNIOR/SENIOR/UNIVERSITY）
--   ② subject_themes     跨学段主题表（8 个法律主题）
--   ③ knowledge_points   新增 stage / depth_level / theme_id / stage_order
--   ④ courses            新增 stage
--   ⑤ knowledge_relations 关系类型增加 PROGRESSION
--
-- 约定：全部使用 IF NOT EXISTS / IF EXISTS 保证幂等，可重复执行
-- ============================================================================

-- ============================================================================
-- ① education_stages — 学段定义表
-- ============================================================================
CREATE TABLE IF NOT EXISTS education_stages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(16) NOT NULL UNIQUE,   -- PRIMARY / JUNIOR / SENIOR / UNIVERSITY
    name            VARCHAR(32) NOT NULL,           -- 小学 / 初中 / 高中 / 大学
    description     TEXT,
    min_depth       INT         NOT NULL DEFAULT 1, -- 最小认知深度
    max_depth       INT         NOT NULL DEFAULT 5, -- 最大认知深度
    sort_order      INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 初始化四学段数据（幂等：存在则跳过）
INSERT INTO education_stages (code, name, description, min_depth, max_depth, sort_order)
SELECT 'PRIMARY',    '小学', '启蒙认知 — 建立规则意识、法律初步感知', 1, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM education_stages WHERE code = 'PRIMARY');

INSERT INTO education_stages (code, name, description, min_depth, max_depth, sort_order)
SELECT 'JUNIOR',     '初中', '系统理解 — 掌握法律基础知识、宪法核心内容', 2, 3, 2
WHERE NOT EXISTS (SELECT 1 FROM education_stages WHERE code = 'JUNIOR');

INSERT INTO education_stages (code, name, description, min_depth, max_depth, sort_order)
SELECT 'SENIOR',     '高中', '深入应用 — 法律分析、综合思辨、案例应用', 3, 5, 3
WHERE NOT EXISTS (SELECT 1 FROM education_stages WHERE code = 'SENIOR');

INSERT INTO education_stages (code, name, description, min_depth, max_depth, sort_order)
SELECT 'UNIVERSITY', '大学', '理论升华 — 法治理论系统掌握、法治思维养成', 4, 5, 4
WHERE NOT EXISTS (SELECT 1 FROM education_stages WHERE code = 'UNIVERSITY');

-- ============================================================================
-- ② subject_themes — 跨学段主题表
-- ============================================================================
CREATE TABLE IF NOT EXISTS subject_themes (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    subject         VARCHAR(64) NOT NULL,          -- 学科（如：法律）
    code            VARCHAR(32) NOT NULL UNIQUE,   -- LAW_RULE / CONSTITUTION / ...
    name            VARCHAR(128) NOT NULL,          -- 法律与规则 / 宪法精神 / ...
    description     TEXT,
    icon            VARCHAR(32),                    -- 主题图标（emoji）
    sort_order      INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_themes_subject ON subject_themes (subject);

-- 初始化 8 个法律主题（幂等）
INSERT INTO subject_themes (subject, code, name, description, icon, sort_order)
SELECT '法律', 'LAW_RULE', '法律与规则',
       '认识规则到理解法律的特征与作用，再到分析法律规范体系',
       '⚖️', 1
WHERE NOT EXISTS (SELECT 1 FROM subject_themes WHERE code = 'LAW_RULE');

INSERT INTO subject_themes (subject, code, name, description, icon, sort_order)
SELECT '法律', 'CONSTITUTION', '宪法精神',
       '从认识宪法是根本法，到理解宪法基本原则，再到评价宪法在国家治理中的地位',
       '🏛️', 2
WHERE NOT EXISTS (SELECT 1 FROM subject_themes WHERE code = 'CONSTITUTION');

INSERT INTO subject_themes (subject, code, name, description, icon, sort_order)
SELECT '法律', 'CIVIL_RIGHTS', '公民权利与义务',
       '从知道儿童基本权利，到理解权利义务关系，再到综合运用宪法及部门法保障公民权利',
       '🧑‍⚖️', 3
WHERE NOT EXISTS (SELECT 1 FROM subject_themes WHERE code = 'CIVIL_RIGHTS');

INSERT INTO subject_themes (subject, code, name, description, icon, sort_order)
SELECT '法律', 'ORDER_SAFETY', '社会秩序与安全',
       '从遵守公共秩序，到理解违法与犯罪，再到系统理解刑事法治与行政法治',
       '🚦', 4
WHERE NOT EXISTS (SELECT 1 FROM subject_themes WHERE code = 'ORDER_SAFETY');

INSERT INTO subject_themes (subject, code, name, description, icon, sort_order)
SELECT '法律', 'JUSTICE', '公平正义',
       '从感知公平公正，到理解法律面前人人平等，再到评价司法公正与法治价值',
       '🕊️', 5
WHERE NOT EXISTS (SELECT 1 FROM subject_themes WHERE code = 'JUSTICE');

INSERT INTO subject_themes (subject, code, name, description, icon, sort_order)
SELECT '法律', 'CIVIL_LAW', '民事法律',
       '从知道民事权利，到理解民事主体与责任，再到系统理解民法体系',
       '📜', 6
WHERE NOT EXISTS (SELECT 1 FROM subject_themes WHERE code = 'CIVIL_LAW');

INSERT INTO subject_themes (subject, code, name, description, icon, sort_order)
SELECT '法律', 'GOVERNANCE', '国家机构与治理',
       '从知道国家有政府和法律管理，到了解国家机构职能，再到评价全面依法治国',
       '🏢', 7
WHERE NOT EXISTS (SELECT 1 FROM subject_themes WHERE code = 'GOVERNANCE');

INSERT INTO subject_themes (subject, code, name, description, icon, sort_order)
SELECT '法律', 'INTERNATIONAL', '国际视野',
       '从了解国际法和国际组织，到分析国际法与人权公约，再到评价全球治理中的中国法治贡献',
       '🌍', 8
WHERE NOT EXISTS (SELECT 1 FROM subject_themes WHERE code = 'INTERNATIONAL');

-- ============================================================================
-- ③ knowledge_points — 新增学段相关字段
--    stage 与 depth_level 为迁移回填字段（PRD §10.4），初始为 NULL/默认值
-- ============================================================================
ALTER TABLE knowledge_points
    ADD COLUMN IF NOT EXISTS stage            VARCHAR(16),  -- PRIMARY/JUNIOR/SENIOR/UNIVERSITY
    ADD COLUMN IF NOT EXISTS depth_level      SMALLINT DEFAULT 1
                        CHECK (depth_level BETWEEN 1 AND 5),
    ADD COLUMN IF NOT EXISTS theme_id         UUID,
    ADD COLUMN IF NOT EXISTS stage_order      INTEGER DEFAULT 0;

-- 主题外键（先加列后建约束，保证幂等）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_kp_theme'
    ) THEN
        ALTER TABLE knowledge_points
            ADD CONSTRAINT fk_kp_theme FOREIGN KEY (theme_id)
            REFERENCES subject_themes(id) ON DELETE SET NULL;
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_kp_stage ON knowledge_points (stage);
CREATE INDEX IF NOT EXISTS idx_kp_theme ON knowledge_points (theme_id);
CREATE INDEX IF NOT EXISTS idx_kp_stage_depth ON knowledge_points (stage, depth_level);

-- ============================================================================
-- ④ courses — 新增学段字段
-- ============================================================================
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS stage VARCHAR(16);  -- PRIMARY/JUNIOR/SENIOR/UNIVERSITY

CREATE INDEX IF NOT EXISTS idx_courses_stage ON courses (stage);

-- ============================================================================
-- ⑤ knowledge_relations — 关系类型增加 PROGRESSION
-- ============================================================================
ALTER TABLE knowledge_relations
    DROP CONSTRAINT IF EXISTS knowledge_relations_relation_type_check;

ALTER TABLE knowledge_relations
    ADD CONSTRAINT knowledge_relations_relation_type_check
    CHECK (relation_type IN ('PREREQUISITE', 'PARENT_OF', 'RELATED', 'PROGRESSION'));

-- end of migration script
