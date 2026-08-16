-- ═══════════════════════════════════════════════════════════════
-- V22: 修复 moot_court_messages 缺少 updated_at 列
-- （BaseEntity 含 @LastModifiedDate updatedAt 字段，两个实体表均需该列）
-- 幂等：IF NOT EXISTS，新库/旧库均可安全执行
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE moot_court_messages
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
