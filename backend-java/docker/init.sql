-- =============================================================================
-- EduMentor — PostgreSQL 初始化脚本
-- =============================================================================
-- 此脚本在 PostgreSQL 容器首次启动时自动执行。
-- 用途：启用 pgcrypto 扩展（用于 gen_random_uuid()）
-- =============================================================================

-- 启用 pgcrypto 扩展（UUID 生成）
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 启用 pgvector 扩展（向量嵌入存储和相似度检索）
CREATE EXTENSION IF NOT EXISTS "vector";

-- 可选: 创建额外的数据库角色（如果不需要默认用户以外的角色，可留空）
-- CREATE ROLE readonly WITH LOGIN PASSWORD 'readonly_password' NOBYPASSRLS;
-- GRANT CONNECT ON DATABASE edumentor TO readonly;
