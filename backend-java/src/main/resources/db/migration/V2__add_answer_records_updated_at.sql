-- =============================================================================
-- EduMentor — V2: 为 answer_records 表添加 updated_at 列
-- =============================================================================
-- 背景：V1 创建 answer_records 表时遗漏了 updated_at 列，
-- 但实体类中已通过 BaseEntity 定义了 @LastModifiedDate 映射到 updated_at。
-- 由于 ddl-auto 设置为 validate，Hibernate 校验会因列缺失而失败。
-- =============================================================================

-- 1. 添加 updated_at 列（历史数据初始化为创建时间）
ALTER TABLE answer_records
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 2. 创建触发器，在更新时自动更新 updated_at
CREATE TRIGGER trg_answer_records_updated_at
    BEFORE UPDATE ON answer_records
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
