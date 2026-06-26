-- ============================================================
-- V7: 为 error_records 表添加 course_id 列
-- 用途：使错题复盘支持按课程过滤数据
-- ============================================================

-- 1. 添加 course_id 列（允许为空，兼容已有数据）
ALTER TABLE public.error_records
    ADD COLUMN IF NOT EXISTS course_id UUID;

-- 2. 添加外键约束（不严格要求，已有旧数据的 course_id 为 null）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_error_records_course'
    ) THEN
        ALTER TABLE public.error_records
            ADD CONSTRAINT fk_error_records_course
            FOREIGN KEY (course_id) REFERENCES public.courses(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- 3. 添加索引以优化按课程查询错题的性能
CREATE INDEX IF NOT EXISTS idx_er_course_id
    ON public.error_records(course_id);
