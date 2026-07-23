-- V13__create_peer_quiz_tables.sql
-- 学生互出题考核功能 — 学生可以创建考核任务、指定参与学生、互相出题

-- 考核任务表：一次考核的元信息
CREATE TABLE IF NOT EXISTS peer_quizzes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    knowledge_point_id UUID REFERENCES knowledge_points(id) ON DELETE SET NULL,
    title VARCHAR(256) NOT NULL,
    deadline TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pq_creator ON peer_quizzes (creator_id);
CREATE INDEX IF NOT EXISTS idx_pq_course ON peer_quizzes (course_id);
CREATE INDEX IF NOT EXISTS idx_pq_status ON peer_quizzes (status);

-- 参与学生表
CREATE TABLE IF NOT EXISTS peer_quiz_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES peer_quizzes(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    score INTEGER,
    total_questions INTEGER,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(quiz_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_pqp_quiz ON peer_quiz_participants (quiz_id);
CREATE INDEX IF NOT EXISTS idx_pqp_student ON peer_quiz_participants (student_id);
CREATE INDEX IF NOT EXISTS idx_pqp_status ON peer_quiz_participants (status);

-- 考核题目关联表
CREATE TABLE IF NOT EXISTS peer_quiz_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES peer_quizzes(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(quiz_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_pqq_quiz ON peer_quiz_questions (quiz_id);
