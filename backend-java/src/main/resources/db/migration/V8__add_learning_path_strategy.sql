-- ============================================================
-- V8: 为 learning_paths 表添加 adapt_strategy 列
-- 用途：持久化路径策略，支持均衡/最短/拓展 3 种模式
-- ============================================================

ALTER TABLE public.learning_paths
    ADD COLUMN IF NOT EXISTS adapt_strategy VARCHAR(16) NOT NULL DEFAULT 'REORDER';

COMMENT ON COLUMN public.learning_paths.adapt_strategy IS '适配策略: REORDER(均衡) / SHORTEN(最短) / FOCUS_WEAK(拓展)';
