-- ============================================================================
-- V19: 学习路径自规划 + AI 共创 — 数据底座
--  ① path_templates         — 预设路径模板定义（EXAM/LITIGATION/INTEREST/TEACHING）
--  ② path_template_nodes    — 静态模板节点快照
--  ③ learning_paths 加字段   — source（路径来源）+ template_id（来源模板）
-- ============================================================================

-- ① 路径模板定义表
CREATE TABLE IF NOT EXISTS path_templates (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id      UUID NOT NULL,
    code           VARCHAR(32) NOT NULL,   -- EXAM / LITIGATION / INTEREST / TEACHING
    name           VARCHAR(128) NOT NULL,  -- 课程考试 / 纠纷解决 / 兴趣拓展 / 师范生备课
    description    TEXT,
    icon           VARCHAR(32),
    total_minutes  INT,                    -- 总课时（分钟）；INTEREST 为 NULL = 不设限
    node_count     INT NOT NULL DEFAULT 0,
    is_visible     BOOLEAN NOT NULL DEFAULT TRUE,
    template_type  VARCHAR(16) NOT NULL DEFAULT 'STATIC',  -- STATIC / RULE_BY_STAGE
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_pt_course FOREIGN KEY (course_id)
        REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT uk_pt_course_code UNIQUE (course_id, code)
);

CREATE INDEX IF NOT EXISTS idx_pt_course ON path_templates (course_id);
CREATE INDEX IF NOT EXISTS idx_pt_visible ON path_templates (is_visible) WHERE is_visible = TRUE;

-- ② 静态模板节点快照表（RULE_BY_STAGE 动态模板不落节点，按学段/主题实时计算）
CREATE TABLE IF NOT EXISTS path_template_nodes (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id           UUID NOT NULL,
    knowledge_point_id    UUID NOT NULL,
    knowledge_point_name  VARCHAR(255) NOT NULL,  -- 名称快照
    order_index           INT NOT NULL DEFAULT 0,
    estimated_minutes     INT NOT NULL DEFAULT 30,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_ptn_template FOREIGN KEY (template_id)
        REFERENCES path_templates(id) ON DELETE CASCADE,
    CONSTRAINT fk_ptn_kp FOREIGN KEY (knowledge_point_id)
        REFERENCES knowledge_points(id) ON DELETE CASCADE,
    CONSTRAINT uk_ptn_template_kp UNIQUE (template_id, knowledge_point_id)
);

CREATE INDEX IF NOT EXISTS idx_ptn_template ON path_template_nodes (template_id);

-- ③ learning_paths 加路径来源标记
ALTER TABLE learning_paths
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'AUTO',  -- AUTO/TEMPLATE/AI/CUSTOM
    ADD COLUMN IF NOT EXISTS template_id UUID;

CREATE INDEX IF NOT EXISTS idx_lp_source ON learning_paths (source);

-- ④ learning_path_nodes 加 AI 选择理由（AI 规划生成节点时记录，供学生查看/异议）
ALTER TABLE learning_path_nodes
    ADD COLUMN IF NOT EXISTS ai_reason TEXT;

-- 触发器：path_templates 也有 updated_at 自动更新
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_path_templates_updated_at ON path_templates;
CREATE TRIGGER trg_path_templates_updated_at
    BEFORE UPDATE ON path_templates
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
