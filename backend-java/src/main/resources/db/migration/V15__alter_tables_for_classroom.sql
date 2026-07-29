-- ============================================================================
-- EduMentor (智学导师) — 沉浸式智慧课堂 现有表改造
-- Flyway Migration: V15__alter_tables_for_classroom.sql
--
-- 改造目的：
--   1. learning_path_nodes — 新增 node_type(classroom/review) 、classroom_id、scene_id
--   2. error_records       — 新增 source(quiz/exam/classroom)、classroom_id、scene_id、action_order
--   3. study_sessions       — 新增 session_type(quiz/classroom/mixed)、classroom_id
-- ============================================================================

-- ============================================================================
-- 1. learning_path_nodes — 支持课堂类型的路径节点
-- ============================================================================

-- 新增 node_type 字段，默认 'quiz' 兼容现有数据
ALTER TABLE learning_path_nodes
    ADD COLUMN IF NOT EXISTS node_type VARCHAR(16) NOT NULL DEFAULT 'quiz'
        CHECK (node_type IN ('quiz', 'classroom', 'review'));

-- 新增 classroom_id 和 scene_id 关联
ALTER TABLE learning_path_nodes
    ADD COLUMN IF NOT EXISTS classroom_id UUID REFERENCES classrooms(id) ON DELETE SET NULL;
ALTER TABLE learning_path_nodes
    ADD COLUMN IF NOT EXISTS scene_id UUID REFERENCES scenes(id) ON DELETE SET NULL;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_lpn_node_type ON learning_path_nodes (node_type);
CREATE INDEX IF NOT EXISTS idx_lpn_classroom ON learning_path_nodes (classroom_id) WHERE classroom_id IS NOT NULL;

-- ============================================================================
-- 2. error_records — 追踪错题来源（课堂Quiz vs 普通做题）
-- ============================================================================

-- 新增 source 字段标识错题来源
ALTER TABLE error_records
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'quiz'
        CHECK (source IN ('quiz', 'exam', 'classroom'));

-- 新增课堂关联字段
ALTER TABLE error_records
    ADD COLUMN IF NOT EXISTS classroom_id UUID REFERENCES classrooms(id) ON DELETE SET NULL;
ALTER TABLE error_records
    ADD COLUMN IF NOT EXISTS scene_id UUID REFERENCES scenes(id) ON DELETE SET NULL;
ALTER TABLE error_records
    ADD COLUMN IF NOT EXISTS action_order INTEGER;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_er_source ON error_records (source);
CREATE INDEX IF NOT EXISTS idx_er_source_student ON error_records (student_id, source) WHERE source = 'classroom';

-- ============================================================================
-- 3. study_sessions — 区分学习会话的类型
-- ============================================================================

-- 新增 session_type 字段，默认 'quiz' 兼容现有数据
ALTER TABLE study_sessions
    ADD COLUMN IF NOT EXISTS session_type VARCHAR(16) NOT NULL DEFAULT 'quiz'
        CHECK (session_type IN ('quiz', 'classroom', 'mixed'));

-- 新增 classroom_id 关联
ALTER TABLE study_sessions
    ADD COLUMN IF NOT EXISTS classroom_id UUID REFERENCES classrooms(id) ON DELETE SET NULL;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_ss_session_type ON study_sessions (session_type);
CREATE INDEX IF NOT EXISTS idx_ss_classroom ON study_sessions (classroom_id) WHERE classroom_id IS NOT NULL;
