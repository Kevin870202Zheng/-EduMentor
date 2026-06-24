-- =============================================================================
-- EduMentor — V3: 为其他缺少 updated_at 的表补齐该列
-- =============================================================================
-- 背景：V1 建表时，chat_history 和 knowledge_relations 表遗漏了 updated_at 列，
-- 但对应的实体类通过 BaseEntity 定义了 @LastModifiedDate 映射到 updated_at。
-- ddl-auto 为 validate 时，Hibernate 校验会因列缺失而失败。
-- =============================================================================

-- 1. chat_history 表
ALTER TABLE chat_history
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_chat_history_updated_at
    BEFORE UPDATE ON chat_history
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 2. knowledge_relations 表
ALTER TABLE knowledge_relations
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_knowledge_relations_updated_at
    BEFORE UPDATE ON knowledge_relations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
