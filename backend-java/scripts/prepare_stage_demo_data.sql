-- ============================================================================
-- 多学段功能体验数据准备（一次性脚本，仅用于本地开发体验）
-- 1) 统一演示账号密码为 admin123
-- 2) 法学概论课程标注学段 = 大学（UNIVERSITY）
-- 3) 知识点批量回填 stage/depth（模拟教师端「一键标注学段」）
-- 4) 知识点按名称关键词打标 theme_id（8 个法律主题，默认归入 LAW_RULE）
-- ============================================================================

-- ① 统一演示账号密码（复制 admin 的 bcrypt 哈希）
UPDATE users
SET password_hash = (SELECT password_hash FROM users WHERE username = 'admin')
WHERE username IN ('teacher01', 'student01', 'student02', 'student03');

-- ② 课程级学段标注（PRD §10.3：人工锚点）
UPDATE courses SET stage = 'UNIVERSITY' WHERE course_code = 'LAW101';

-- ③ 知识点回填：stage 继承课程 + depth_level 以 difficulty 近似（PRD §10.4 规则1）
UPDATE knowledge_points kp
SET stage = c.stage,
    depth_level = COALESCE(kp.difficulty, 1)
FROM courses c
WHERE kp.course_id = c.id
  AND c.course_code = 'LAW101'
  AND (kp.stage IS NULL OR kp.stage = '');

-- ④ 主题打标：按知识点名称关键词归类到 8 个法律主题（PRD §10.5 关键词初标）
UPDATE knowledge_points kp
SET theme_id = sub.theme_id
FROM (
    SELECT kp2.id,
           CASE
               WHEN kp2.name LIKE '%宪法%' OR kp2.name LIKE '%根本法%'
                 OR kp2.name LIKE '%国家机构%' OR kp2.name LIKE '%国体%'
                 OR kp2.name LIKE '%政体%' OR kp2.name LIKE '%人大%'
                 OR kp2.name LIKE '%立法%' OR kp2.name LIKE '%修宪%'
                 THEN (SELECT id FROM subject_themes WHERE code = 'CONSTITUTION')

               WHEN kp2.name LIKE '%权利%' OR kp2.name LIKE '%义务%'
                 OR kp2.name LIKE '%公民%' OR kp2.name LIKE '%人身%'
                 OR kp2.name LIKE '%自由%' OR kp2.name LIKE '%隐私%'
                 OR kp2.name LIKE '%受教育%' OR kp2.name LIKE '%选举%'
                 THEN (SELECT id FROM subject_themes WHERE code = 'CIVIL_RIGHTS')

               WHEN kp2.name LIKE '%违法%' OR kp2.name LIKE '%犯罪%'
                 OR kp2.name LIKE '%刑罚%' OR kp2.name LIKE '%治安%'
                 OR kp2.name LIKE '%刑法%' OR kp2.name LIKE '%处罚%'
                 OR kp2.name LIKE '%责任年龄%'
                 THEN (SELECT id FROM subject_themes WHERE code = 'ORDER_SAFETY')

               WHEN kp2.name LIKE '%合同%' OR kp2.name LIKE '%侵权%'
                 OR kp2.name LIKE '%物权%' OR kp2.name LIKE '%婚姻%'
                 OR kp2.name LIKE '%继承%' OR kp2.name LIKE '%民法%'
                 OR kp2.name LIKE '%债权%' OR kp2.name LIKE '%财产%'
                 OR kp2.name LIKE '%人格%'
                 THEN (SELECT id FROM subject_themes WHERE code = 'CIVIL_LAW')

               WHEN kp2.name LIKE '%公平%' OR kp2.name LIKE '%正义%'
                 OR kp2.name LIKE '%司法%' OR kp2.name LIKE '%平等%'
                 OR kp2.name LIKE '%公正%' OR kp2.name LIKE '%法治精神%'
                 THEN (SELECT id FROM subject_themes WHERE code = 'JUSTICE')

               WHEN kp2.name LIKE '%政府%' OR kp2.name LIKE '%治理%'
                 OR kp2.name LIKE '%行政%' OR kp2.name LIKE '%依法治国%'
                 OR kp2.name LIKE '%法治国家%' OR kp2.name LIKE '%依法行政%'
                 THEN (SELECT id FROM subject_themes WHERE code = 'GOVERNANCE')

               WHEN kp2.name LIKE '%国际%' OR kp2.name LIKE '%条约%'
                 OR kp2.name LIKE '%外交%' OR kp2.name LIKE '%全球%'
                 OR kp2.name LIKE '%涉外%'
                 THEN (SELECT id FROM subject_themes WHERE code = 'INTERNATIONAL')

               ELSE (SELECT id FROM subject_themes WHERE code = 'LAW_RULE')
           END AS theme_id
    FROM knowledge_points kp2
    JOIN courses c ON kp2.course_id = c.id
    WHERE c.course_code = 'LAW101'
) sub
WHERE kp.id = sub.id AND kp.theme_id IS NULL;

-- ============================================================================
-- 校验
-- ============================================================================
SELECT '课程学段' AS check_name,
       name, stage
FROM courses WHERE course_code = 'LAW101';

SELECT '知识点 stage 覆盖' AS check_name,
       count(*) FILTER (WHERE stage IS NOT NULL) AS with_stage,
       count(*) AS total
FROM knowledge_points;

SELECT '主题分布' AS check_name,
       t.name, count(kp.id) AS kp_count
FROM subject_themes t
LEFT JOIN knowledge_points kp ON kp.theme_id = t.id
GROUP BY t.name, t.sort_order
ORDER BY t.sort_order;
