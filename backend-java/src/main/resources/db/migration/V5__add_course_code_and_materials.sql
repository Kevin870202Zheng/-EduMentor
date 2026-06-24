-- ============================================================================
-- EduMentor (智学导师) — Flyway Migration: V5__add_course_code_and_materials.sql
--
-- 变更:
--   1. courses 表增加 course_code 字段（唯一、人类可读的课程编号）
--   2. 创建 course_materials 表（存储上传的原始资料）
--   3. 创建 kp_embeddings 表（知识点向量嵌入，用于 RAG 检索）
-- ============================================================================

-- 1. courses 表增加 course_code 字段
ALTER TABLE courses ADD COLUMN IF NOT EXISTS course_code VARCHAR(32);

-- 为已有课程生成默认编号（仅在首次迁移时生效）
UPDATE courses SET course_code = 'MATH101' WHERE name LIKE '%高等数学%' AND course_code IS NULL;
UPDATE courses SET course_code = 'MATH201' WHERE name LIKE '%线性代数%' AND course_code IS NULL;
UPDATE courses SET course_code = 'CS101' WHERE name LIKE '%Python%' AND course_code IS NULL;

-- 设置 NOT NULL + UNIQUE
ALTER TABLE courses ALTER COLUMN course_code SET NOT NULL;

-- 注意：PostgreSQL 不支持 ALTER TABLE ADD CONSTRAINT IF NOT EXISTS
-- 因此使用 DO 块来保证幂等性
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_courses_course_code'
    ) THEN
        ALTER TABLE courses ADD CONSTRAINT uk_courses_course_code UNIQUE (course_code);
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_courses_course_code ON courses (course_code);

COMMENT ON COLUMN courses.course_code IS '课程编号（业务唯一标识，如 MATH101），教师创建时自行填写';

-- ============================================================================
-- 2. course_materials — 课程原始资料表
--    存储教师上传的课程资料，支持 AI 提取流程
-- ============================================================================
CREATE TABLE IF NOT EXISTS course_materials (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    course_code     VARCHAR(32) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    file_type       VARCHAR(20),            -- pdf / docx / txt / md / html
    file_path       VARCHAR(500),
    raw_text        TEXT,                   -- 解析后的纯文本内容
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'extracting', 'extracted', 'published', 'failed')),
    extraction_result JSONB,                -- AI 提取结果的缓存（知识点/关系/习题的 JSON）
    error_message   TEXT,                   -- 提取失败时的错误信息
    created_by      UUID        NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cm_course_id ON course_materials (course_id);
CREATE INDEX IF NOT EXISTS idx_cm_course_code ON course_materials (course_code);
CREATE INDEX IF NOT EXISTS idx_cm_status ON course_materials (status);

COMMENT ON TABLE course_materials IS '课程原始资料 — 教师上传后经 AI 提取为结构化知识点和习题';
COMMENT ON COLUMN course_materials.course_code IS '冗余字段，方便直接按课程编号查询和日志识别';
COMMENT ON COLUMN course_materials.raw_text IS '从上传文件中解析出的纯文本，供 AI 提取使用';
COMMENT ON COLUMN course_materials.extraction_result IS 'AI 提取结果缓存：{knowledgePoints: [...], relations: [...], questions: [...]}';

-- ============================================================================
-- 3. kp_embeddings — 知识点向量嵌入表
--    存储知识点内容、习题等的向量嵌入，用于 RAG 相似度检索
-- ============================================================================
CREATE TABLE IF NOT EXISTS kp_embeddings (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    kp_id           UUID        REFERENCES knowledge_points(id) ON DELETE CASCADE,
    material_id     UUID        REFERENCES course_materials(id) ON DELETE CASCADE,
    content_type    VARCHAR(30) NOT NULL     -- 'kp_content' / 'question' / 'material_chunk'
                    CHECK (content_type IN ('kp_content', 'question', 'material_chunk')),
    chunk_text      TEXT        NOT NULL,
    embedding       vector(1536),           -- OpenAI text-embedding-3-small 维度
    course_id       UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    course_code     VARCHAR(32) NOT NULL,
    metadata        JSONB       DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kpe_course_id ON kp_embeddings (course_id);
CREATE INDEX IF NOT EXISTS idx_kpe_course_code ON kp_embeddings (course_code);
CREATE INDEX IF NOT EXISTS idx_kpe_content_type ON kp_embeddings (content_type);
CREATE INDEX IF NOT EXISTS idx_kpe_kp_id ON kp_embeddings (kp_id);

COMMENT ON TABLE kp_embeddings IS '知识点向量嵌入 — 供 LLM RAG 检索增强使用';
COMMENT ON COLUMN kp_embeddings.embedding IS '向量嵌入（1536 维），使用 IVFFlat 索引加速余弦相似度检索';
COMMENT ON COLUMN kp_embeddings.course_code IS '冗余字段，方便日志调试和快速过滤';

-- 向量索引（IVFFlat，100 个列表）
-- 注意：需要先插入数据后再创建 IVFFlat 索引效果更好，此处为 DDL 声明
-- CREATE INDEX IF NOT EXISTS idx_kpe_embedding ON kp_embeddings
--     USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ============================================================================
-- 触发器：course_materials 自动更新 updated_at
-- ============================================================================
DROP TRIGGER IF EXISTS trg_course_materials_updated_at ON course_materials;
CREATE TRIGGER trg_course_materials_updated_at
    BEFORE UPDATE ON course_materials
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
