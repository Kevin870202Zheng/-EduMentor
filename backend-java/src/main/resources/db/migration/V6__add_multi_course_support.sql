-- ============================================================================
-- EduMentor (智学导师) — Flyway Migration: V6__add_multi_course_support.sql
--
-- 变更:
--   1. student_profiles 增加组织维度字段（班级/专业/系/学院）
--   2. 新建 student_courses 表（学生选课关联）
--   3. 新建 course_teachers 表（课程教师分配）
-- ============================================================================

-- 1. student_profiles 增加组织维度
ALTER TABLE student_profiles
    ADD COLUMN IF NOT EXISTS class_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS major VARCHAR(64),
    ADD COLUMN IF NOT EXISTS department VARCHAR(64),
    ADD COLUMN IF NOT EXISTS college VARCHAR(64);

COMMENT ON COLUMN student_profiles.class_name IS '班级名称，如"计科2101班"';
COMMENT ON COLUMN student_profiles.major IS '专业，如"计算机科学与技术"';
COMMENT ON COLUMN student_profiles.department IS '系，如"计算机系"';
COMMENT ON COLUMN student_profiles.college IS '学院，如"信息与计算机学院"';

-- 2. student_courses — 学生选课关联表
CREATE TABLE IF NOT EXISTS student_courses (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id       UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    course_code     VARCHAR(32) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'completed', 'dropped')),
    enrolled_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_student_course UNIQUE (student_id, course_id)
);

CREATE INDEX IF NOT EXISTS idx_sc_student ON student_courses (student_id);
CREATE INDEX IF NOT EXISTS idx_sc_course ON student_courses (course_id);
CREATE INDEX IF NOT EXISTS idx_sc_student_status ON student_courses (student_id, status);

COMMENT ON TABLE student_courses IS '学生选课 — 学生与课程的多对多关联';
COMMENT ON COLUMN student_courses.course_code IS '冗余字段，方便调试和快速识别';

-- 3. course_teachers — 课程教师分配表
CREATE TABLE IF NOT EXISTS course_teachers (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    teacher_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL DEFAULT 'lecturer'
                    CHECK (role IN ('lecturer', 'tutor', 'assistant')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_course_teacher UNIQUE (course_id, teacher_id)
);

CREATE INDEX IF NOT EXISTS idx_ct_course ON course_teachers (course_id);
CREATE INDEX IF NOT EXISTS idx_ct_teacher ON course_teachers (teacher_id);

COMMENT ON TABLE course_teachers IS '课程教师分配 — 授课教师、辅导教师等多角色支持';
COMMENT ON COLUMN course_teachers.role IS '讲师(lecturer)/辅导(tutor)/助教(assistant)';

-- ============================================================================
-- 触发器
-- ============================================================================
DROP TRIGGER IF EXISTS trg_student_courses_updated_at ON student_courses;
CREATE TRIGGER trg_student_courses_updated_at
    BEFORE UPDATE ON student_courses
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
