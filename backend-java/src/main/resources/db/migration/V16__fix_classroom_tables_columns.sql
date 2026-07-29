-- ============================================================================
-- EduMentor (智学导师) — 补全课堂表缺失列
-- Flyway Migration: V16__fix_classroom_tables_columns.sql
--
-- BaseEntity 定义了 createdAt 和 updatedAt 字段，但 V14 迁移中
-- scene_actions 和 scene_quiz_records 表缺少 updated_at 列，
-- 导致 Hibernate INSERT 时抛出 SQLGrammarException。
-- ============================================================================

-- 为 scene_actions 补全 updated_at 列
ALTER TABLE scene_actions
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 为 scene_quiz_records 补全 updated_at 列
ALTER TABLE scene_quiz_records
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
