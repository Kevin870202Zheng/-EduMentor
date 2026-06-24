-- =============================================================================
-- EduMentor — V4: 修复 chat_history.session_id 类型（UUID → VARCHAR(64)）
-- =============================================================================
-- 背景：V1 建表时 chat_history.session_id 被定义为 UUID 类型，
-- 但实体 ChatHistory 中 sessionId 为 String（@Column length = 64）。
-- Hibernate ddl-auto:validate 会因类型不匹配而报错。
-- =============================================================================

ALTER TABLE chat_history
    ALTER COLUMN session_id TYPE VARCHAR(64);
