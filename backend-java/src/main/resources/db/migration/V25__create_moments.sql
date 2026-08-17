-- ═══════════════════════════════════════════════════════════════
-- V25: 同学圈（学生朋友圈 + AI 法律风险提示）
-- 设计文档: .youcoder/plans/moments-legal-review-design.html (v1.0)
-- AI 对动态文本做法律风险检测（LegalReviewResult），不涉及法律则 ai_review=null
-- ═══════════════════════════════════════════════════════════════

-- ① 同学圈动态（支持本地图片上传，images 存 URL 数组）
CREATE TABLE IF NOT EXISTS moments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id      UUID NOT NULL REFERENCES users(id),
    content        TEXT NOT NULL,                      -- 动态正文（限 500 字）
    images         JSONB,                              -- 图片 URL 数组（["/uploads/moments/xxx.jpg"]）
    ai_review      JSONB,                              -- AI 法律检测结果（LegalReviewResult）；不涉及=null
    like_count     INTEGER NOT NULL DEFAULT 0,         -- 冗余点赞数
    comment_count  INTEGER NOT NULL DEFAULT 0,         -- 冗余评论数
    is_deleted     BOOLEAN NOT NULL DEFAULT FALSE,     -- 软删除
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_moments_created ON moments (created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_moments_author ON moments (author_id);

-- ② 点赞（每人每动态一次）
CREATE TABLE IF NOT EXISTS moment_likes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    moment_id   UUID NOT NULL REFERENCES moments(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (moment_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_ml_moment ON moment_likes (moment_id);

-- ③ 评论
CREATE TABLE IF NOT EXISTS moment_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    moment_id   UUID NOT NULL REFERENCES moments(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    content     TEXT NOT NULL,                         -- 限 200 字
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mc_moment ON moment_comments (moment_id, created_at);
