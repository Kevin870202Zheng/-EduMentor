-- ============================================================================
-- V18__add_student_stage.sql
-- 学生学段身份绑定（PRD v4.0 §4 / §19）
--
-- student_profiles 新增 stage 字段：
--   PRIMARY / JUNIOR / SENIOR / UNIVERSITY
-- 该字段与学生账号绑定，决定学段主题学习等场景的默认学段内容。
-- ============================================================================

ALTER TABLE student_profiles
    ADD COLUMN IF NOT EXISTS stage VARCHAR(16);

-- 学生学段索引（按学段统计/筛选学生时使用）
CREATE INDEX IF NOT EXISTS idx_student_profiles_stage ON student_profiles (stage);

-- end of migration script
