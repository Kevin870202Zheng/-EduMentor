-- ============================================================
-- V10: 为 study_sessions 表添加 course_id 列
-- 用途：使学习会话支持按课程过滤和统计
-- ============================================================

-- 1. 添加 course_id 列（允许为空，兼容已有数据）
ALTER TABLE public.study_sessions
    ADD COLUMN IF NOT EXISTS course_id UUID;

-- 2. 添加外键约束
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_study_sessions_course'
    ) THEN
        ALTER TABLE public.study_sessions
            ADD CONSTRAINT fk_study_sessions_course
            FOREIGN KEY (course_id) REFERENCES public.courses(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- 3. 添加索引
CREATE INDEX IF NOT EXISTS idx_ss_course_id
    ON public.study_sessions(course_id);

COMMENT ON COLUMN public.study_sessions.course_id IS '关联的课程ID，用于按课程统计学习时长';
