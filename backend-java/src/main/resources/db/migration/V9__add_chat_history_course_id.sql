-- ============================================================
-- V9: 为 chat_history 表添加 course_id 列
-- 用途：使智能答疑会话支持按课程隔离，退课后可区分会话归属
-- ============================================================

-- 1. 添加 course_id 列（允许为空，兼容已有数据）
ALTER TABLE public.chat_history
    ADD COLUMN IF NOT EXISTS course_id UUID;

-- 2. 添加外键约束（不严格要求，已有旧数据的 course_id 为 null）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_history_course'
    ) THEN
        ALTER TABLE public.chat_history
            ADD CONSTRAINT fk_chat_history_course
            FOREIGN KEY (course_id) REFERENCES public.courses(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- 3. 添加索引以优化按课程查询聊天记录的性能
CREATE INDEX IF NOT EXISTS idx_ch_course_id
    ON public.chat_history(course_id);

COMMENT ON COLUMN public.chat_history.course_id IS '关联的课程ID，用于按课程隔离对话历史';
