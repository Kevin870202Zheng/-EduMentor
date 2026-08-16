-- ============================================================================
-- seed-time-machine.sql — 成长时光机演示种子数据（student01）
-- 用法：psql -h localhost -p 5432 -U edumentor_dev -d edumentor_dev -f scripts/seed-time-machine.sql
-- 幂等：先清空 student01 的时光机数据再插入
-- ============================================================================

-- student01
-- c631321c-a382-4846-9c24-8a173ddcd87a

-- 主题 ID（V17 预置）
-- 法律与规则       d1d5898f-8309-4a5a-a74b-1edb453e43b3
-- 宪法精神         c5c46374-a32a-4c5b-bf77-adacc1595037
-- 公民权利与义务   28396bea-db2c-4ed4-b87e-f7b5df9fc8f2

DELETE FROM time_machine_letters WHERE student_id = 'c631321c-a382-4846-9c24-8a173ddcd87a';
DELETE FROM growth_archive_snapshots WHERE student_id = 'c631321c-a382-4846-9c24-8a173ddcd87a';

-- ────────────────────────────────────────────────────────────────────────────
-- ① 成长档案快照：小学 → 初中 → 高中 → 大学（模拟跨学段成长轨迹）
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO growth_archive_snapshots (student_id, stage, course_id, summary, created_at) VALUES
('c631321c-a382-4846-9c24-8a173ddcd87a', 'PRIMARY', NULL, '{
  "totalQuestions": 120, "correctCount": 110, "accuracyRate": 0.92,
  "themeMastery": [
    {"themeId": "d1d5898f-8309-4a5a-a74b-1edb453e43b3", "themeName": "法律与规则", "mastery": 0.92, "total": 40},
    {"themeId": "c5c46374-a32a-4c5b-bf77-adacc1595037", "themeName": "宪法精神", "mastery": 0.78, "total": 45},
    {"themeId": "28396bea-db2c-4ed4-b87e-f7b5df9fc8f2", "themeName": "公民权利与义务", "mastery": 0.65, "total": 35}
  ],
  "weakKps": []
}'::jsonb, now() - interval '3 years'),

('c631321c-a382-4846-9c24-8a173ddcd87a', 'JUNIOR', NULL, '{
  "totalQuestions": 210, "correctCount": 178, "accuracyRate": 0.85,
  "themeMastery": [
    {"themeId": "d1d5898f-8309-4a5a-a74b-1edb453e43b3", "themeName": "法律与规则", "mastery": 0.88, "total": 75},
    {"themeId": "c5c46374-a32a-4c5b-bf77-adacc1595037", "themeName": "宪法精神", "mastery": 0.82, "total": 65},
    {"themeId": "28396bea-db2c-4ed4-b87e-f7b5df9fc8f2", "themeName": "公民权利与义务", "mastery": 0.72, "total": 70}
  ],
  "weakKps": []
}'::jsonb, now() - interval '2 years'),

('c631321c-a382-4846-9c24-8a173ddcd87a', 'SENIOR', NULL, '{
  "totalQuestions": 260, "correctCount": 203, "accuracyRate": 0.78,
  "themeMastery": [
    {"themeId": "d1d5898f-8309-4a5a-a74b-1edb453e43b3", "themeName": "法律与规则", "mastery": 0.85, "total": 90},
    {"themeId": "c5c46374-a32a-4c5b-bf77-adacc1595037", "themeName": "宪法精神", "mastery": 0.86, "total": 80},
    {"themeId": "28396bea-db2c-4ed4-b87e-f7b5df9fc8f2", "themeName": "公民权利与义务", "mastery": 0.75, "total": 90}
  ],
  "weakKps": []
}'::jsonb, now() - interval '10 months'),

('c631321c-a382-4846-9c24-8a173ddcd87a', 'UNIVERSITY', NULL, '{
  "totalQuestions": 320, "correctCount": 282, "accuracyRate": 0.88,
  "themeMastery": [
    {"themeId": "d1d5898f-8309-4a5a-a74b-1edb453e43b3", "themeName": "法律与规则", "mastery": 0.9, "total": 110},
    {"themeId": "c5c46374-a32a-4c5b-bf77-adacc1595037", "themeName": "宪法精神", "mastery": 0.87, "total": 100},
    {"themeId": "28396bea-db2c-4ed4-b87e-f7b5df9fc8f2", "themeName": "公民权利与义务", "mastery": 0.8, "total": 110}
  ],
  "weakKps": []
}'::jsonb, now() - interval '2 months');

-- ────────────────────────────────────────────────────────────────────────────
-- ② 来自过去的信（一封已答 + 一封待答）
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO time_machine_letters (student_id, stage, direction, question, answer, ai_generated, answered_at, created_at) VALUES
('c631321c-a382-4846-9c24-8a173ddcd87a', 'PRIMARY', 'PAST_TO_NOW',
 '法律为什么能管住所有人？它又不认识我。',
 '因为它背后是每一个人的共识和国家的力量。小时候觉得法律是"管人"的，现在才明白它是保护每个人的底线——不认识我也没关系，它守护的是所有人。',
 TRUE, now() - interval '1 year', now() - interval '1 year'),

('c631321c-a382-4846-9c24-8a173ddcd87a', 'JUNIOR', 'PAST_TO_NOW',
 '如果签了合同对方反悔了，是不是就只能自认倒霉？',
 NULL,
 TRUE, NULL, now() - interval '6 months');
