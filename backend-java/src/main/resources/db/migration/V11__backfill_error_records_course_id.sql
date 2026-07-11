-- =============================================================================
-- V11: 回填 error_records 表中 course_id 为 NULL 的记录
-- =============================================================================
-- 修复说明：
--   部分 error_records 的 course_id 为 NULL（通过 ReviewController API 直接创建，
--   或早期代码未设置 courseId）。这些记录无法通过按课程查询被检索到。
--   本迁移通过关联 questions 表，将 course_id 回填为题目所属课程的 ID。
-- =============================================================================

UPDATE error_records e
SET course_id = q.course_id
FROM questions q
WHERE e.question_id = q.id
  AND e.course_id IS NULL
  AND q.course_id IS NOT NULL;
