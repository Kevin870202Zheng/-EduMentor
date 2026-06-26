-- ============================================================================
-- EduMentor — Python 程序设计课程测试数据
-- 插入知识点、先修关系、习题、答题记录，便于功能测试
-- ============================================================================

DO $$
DECLARE
    v_course_id UUID;
    v_student01 UUID;
    v_student02 UUID;
    v_student03 UUID;

    -- 知识点 IDs
    v_kp1  UUID; v_kp2  UUID; v_kp3  UUID; v_kp4  UUID;
    v_kp5  UUID; v_kp6  UUID; v_kp7  UUID; v_kp8  UUID;
    v_kp9  UUID; v_kp10 UUID; v_kp11 UUID; v_kp12 UUID;

    -- 习题 IDs
    v_q1 UUID; v_q2 UUID; v_q3 UUID; v_q4 UUID;
    v_q5 UUID; v_q6 UUID; v_q7 UUID; v_q8 UUID;
    v_q9 UUID; v_q10 UUID; v_q11 UUID; v_q12 UUID;

    -- 用于随机答题结果
    v_now TIMESTAMPTZ := now();
BEGIN

    -- ========================================================================
    -- 1. 获取 CS101 课程 ID
    -- ========================================================================
    SELECT id INTO v_course_id FROM courses WHERE course_code = 'CS101';
    IF v_course_id IS NULL THEN
        RAISE EXCEPTION 'Course CS101 not found!';
    END IF;

    RAISE NOTICE 'CS101 course_id: %', v_course_id;

    -- 获取学生 IDs（根据用户名动态查找）
    SELECT id INTO v_student01 FROM users WHERE username = 'student01';
    SELECT id INTO v_student02 FROM users WHERE username = 'student02';
    SELECT id INTO v_student03 FROM users WHERE username = 'student03';

    IF v_student01 IS NULL THEN
        RAISE EXCEPTION 'student01 not found in users table!';
    END IF;
    RAISE NOTICE 'Students: %, %, %', v_student01, v_student02, v_student03;

    -- ========================================================================
    -- 2. 插入知识点
    -- ========================================================================
    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, 'Python简介与环境搭建', '了解Python语言特性，安装Python解释器与IDE，运行第一个程序', 1, 2, 1, 30)
    RETURNING id INTO v_kp1;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '变量与数据类型', '变量命名规则，整数、浮点数、布尔型、None类型，类型转换', 1, 5, 2, 45)
    RETURNING id INTO v_kp2;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '字符串操作', '字符串创建、索引、切片、格式化、常用方法', 2, 5, 3, 60)
    RETURNING id INTO v_kp3;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '列表与元组', '列表的创建、增删改查、列表推导式，元组的不可变性', 2, 5, 4, 60)
    RETURNING id INTO v_kp4;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '条件判断', 'if/elif/else 语句，逻辑运算符，条件表达式', 2, 4, 5, 45)
    RETURNING id INTO v_kp5;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '循环结构', 'for循环、while循环、break/continue、range()函数', 3, 5, 6, 60)
    RETURNING id INTO v_kp6;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '函数定义与调用', '函数定义、参数传递、返回值、可变参数、lambda表达式', 3, 5, 7, 75)
    RETURNING id INTO v_kp7;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '字典与集合', '字典的键值对操作、集合运算、哈希表原理', 3, 4, 8, 60)
    RETURNING id INTO v_kp8;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '文件操作', '文件读写、with语句、CSV/JSON文件处理', 4, 4, 9, 60)
    RETURNING id INTO v_kp9;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '异常处理', 'try/except/finally、自定义异常、断言', 3, 3, 10, 45)
    RETURNING id INTO v_kp10;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '面向对象基础', '类与对象、继承、封装、多态、特殊方法', 4, 4, 11, 90)
    RETURNING id INTO v_kp11;

    INSERT INTO knowledge_points (course_id, name, description, difficulty, importance, order_index, estimated_minutes)
    VALUES (v_course_id, '模块与包', '模块导入、包结构、标准库常用模块', 4, 3, 12, 45)
    RETURNING id INTO v_kp12;

    RAISE NOTICE 'Inserted 12 knowledge points';

    -- ========================================================================
    -- 3. 插入先修关系
    -- ========================================================================
    -- 变量与数据类型 → Python简介
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp2, v_kp1, 'PREREQUISITE', 1.0);
    -- 字符串操作 → 变量与数据类型
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp3, v_kp2, 'PREREQUISITE', 1.0);
    -- 列表与元组 → 变量与数据类型
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp4, v_kp2, 'PREREQUISITE', 1.0);
    -- 条件判断 → 变量与数据类型
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp5, v_kp2, 'PREREQUISITE', 1.0);
    -- 循环结构 → 条件判断
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp6, v_kp5, 'PREREQUISITE', 1.0);
    -- 循环结构 → 列表与元组
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp6, v_kp4, 'PREREQUISITE', 1.0);
    -- 函数 → 循环结构
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp7, v_kp6, 'PREREQUISITE', 1.0);
    -- 字典与集合 → 列表与元组
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp8, v_kp4, 'PREREQUISITE', 1.0);
    -- 文件操作 → 循环结构
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp9, v_kp6, 'PREREQUISITE', 1.0);
    -- 异常处理 → 循环结构
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp10, v_kp6, 'PREREQUISITE', 1.0);
    -- 面向对象 → 函数
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp11, v_kp7, 'PREREQUISITE', 1.0);
    -- 模块与包 → 面向对象
    INSERT INTO knowledge_relations (source_kp_id, target_kp_id, relation_type, weight)
    VALUES (v_kp12, v_kp11, 'PREREQUISITE', 1.0);

    RAISE NOTICE 'Inserted 12 prerequisite relations';

    -- ========================================================================
    -- 4. 插入习题（含选项和答案）
    -- ========================================================================

    -- Q1: Python简介 - 难度1
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp1, v_course_id, 'SINGLE_CHOICE', 1,
        '以下哪个是正确的Python程序入口写法？',
        '[
            {"label":"A","text":"void main()"},
            {"label":"B","text":"if __name__ == \"__main__\":"},
            {"label":"C","text":"function main()"},
            {"label":"D","text":"program main"}
        ]'::jsonb,
        'B',
        'Python使用 if __name__ == "__main__": 作为程序入口，这是Python的约定写法。',
        true)
    RETURNING id INTO v_q1;

    -- Q2: 变量与数据类型 - 难度1
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp2, v_course_id, 'SINGLE_CHOICE', 1,
        '在Python中，type(3.14) 的结果是？',
        '[
            {"label":"A","text":"<class \"int\">"},
            {"label":"B","text":"<class \"float\">"},
            {"label":"C","text":"<class \"str\">"},
            {"label":"D","text":"<class \"number\">"}
        ]'::jsonb,
        'B',
        '3.14 是浮点数，Python中浮点数的类型是 float。',
        true)
    RETURNING id INTO v_q2;

    -- Q3: 字符串操作 - 难度2
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp3, v_course_id, 'SINGLE_CHOICE', 2,
        '"Hello, World!"[7:12] 的切片结果是？',
        '[
            {"label":"A","text":"World"},
            {"label":"B","text":"World!"},
            {"label":"C","text":"ello,"},
            {"label":"D","text":"Hello"}
        ]'::jsonb,
        'A',
        '字符串索引从0开始，[7:12] 取第7到11位字符："W","o","r","l","d"。注意索引7是"W"，12是"！"的前一位。',
        true)
    RETURNING id INTO v_q3;

    -- Q4: 列表与元组 - 难度2
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp4, v_course_id, 'SINGLE_CHOICE', 2,
        '以下哪个操作会改变原列表 lst = [1, 2, 3]？',
        '[
            {"label":"A","text":"lst + [4]"},
            {"label":"B","text":"lst.append(4)"},
            {"label":"C","text":"lst[1:3]"},
            {"label":"D","text":"lst * 2"}
        ]'::jsonb,
        'B',
        'append() 是列表的原地操作方法，会在原列表末尾添加元素。其他操作都会返回新列表而不改变原列表。',
        true)
    RETURNING id INTO v_q4;

    -- Q5: 条件判断 - 难度2
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp5, v_course_id, 'SINGLE_CHOICE', 2,
        'x = 15; 执行 if x > 10: print("A") elif x > 5: print("B") else: print("C") 的输出是？',
        '[
            {"label":"A","text":"A"},
            {"label":"B","text":"B"},
            {"label":"C","text":"C"},
            {"label":"D","text":"AB"}
        ]'::jsonb,
        'A',
        'x=15 满足第一个条件 x>10，因此执行 print("A") 后跳过剩余的 elif 和 else。',
        true)
    RETURNING id INTO v_q5;

    -- Q6: 循环结构 - 难度3
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp6, v_course_id, 'SINGLE_CHOICE', 3,
        '执行 list(range(2, 10, 3)) 的结果是？',
        '[
            {"label":"A","text":"[2, 5, 8]"},
            {"label":"B","text":"[2, 5, 8, 11]"},
            {"label":"C","text":"[2, 4, 6, 8]"},
            {"label":"D","text":"[2, 3, 4, 5, 6, 7, 8, 9]"}
        ]'::jsonb,
        'A',
        'range(2, 10, 3) 从2开始，步长为3，不超过10：2, 5, 8。',
        true)
    RETURNING id INTO v_q6;

    -- Q7: 函数 - 难度3
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp7, v_course_id, 'SINGLE_CHOICE', 3,
        '以下 lambda 表达式等价于哪个函数？ lambda x, y: x + y',
        '[
            {"label":"A","text":"def add(x, y): return x - y"},
            {"label":"B","text":"def add(x, y): return x + y"},
            {"label":"C","text":"def add(x, y): print(x + y)"},
            {"label":"D","text":"def add(x, y): x + y"}
        ]'::jsonb,
        'B',
        'lambda x, y: x + y 是一个匿名函数，接收两个参数并返回它们的和。等价于 def add(x, y): return x + y。',
        true)
    RETURNING id INTO v_q7;

    -- Q8: 字典与集合 - 难度3
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp8, v_course_id, 'SINGLE_CHOICE', 3,
        'd = {"a": 1, "b": 2}; d.get("c", 0) 的结果是？',
        '[
            {"label":"A","text":"None"},
            {"label":"B","text":"0"},
            {"label":"C","text":"报错 KeyError"},
            {"label":"D","text":"False"}
        ]'::jsonb,
        'B',
        'dict.get(key, default) 方法在键不存在时返回默认值，不会抛出异常。这里 "c" 不存在，返回默认值 0。',
        true)
    RETURNING id INTO v_q8;

    -- Q9: 文件操作 - 难度4
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp9, v_course_id, 'SINGLE_CHOICE', 4,
        '使用 with open("file.txt", "r") as f: 的优势是什么？',
        '[
            {"label":"A","text":"文件只能读取不能写入"},
            {"label":"B","text":"自动关闭文件，无需手动调用 f.close()"},
            {"label":"C","text":"文件打开速度更快"},
            {"label":"D","text":"支持所有编码格式"}
        ]'::jsonb,
        'B',
        'with 语句是上下文管理器，在代码块执行完毕后会自动调用 f.close() 关闭文件资源，防止资源泄漏。',
        true)
    RETURNING id INTO v_q9;

    -- Q10: 异常处理 - 难度3
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp10, v_course_id, 'SINGLE_CHOICE', 3,
        '执行 int("abc") 会触发什么异常？',
        '[
            {"label":"A","text":"ValueError"},
            {"label":"B","text":"TypeError"},
            {"label":"C","text":"SyntaxError"},
            {"label":"D","text":"NameError"}
        ]'::jsonb,
        'A',
        'int() 函数无法将非数字字符串 "abc" 转换为整数，会抛出 ValueError。',
        true)
    RETURNING id INTO v_q10;

    -- Q11: 面向对象 - 难度4
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp11, v_course_id, 'SINGLE_CHOICE', 4,
        '在Python中，__init__ 方法的作用是？',
        '[
            {"label":"A","text":"删除对象"},
            {"label":"B","text":"初始化新创建的对象"},
            {"label":"C","text":"打印对象信息"},
            {"label":"D","text":"比较两个对象"}
        ]'::jsonb,
        'B',
        '__init__ 是Python类的构造函数（准确说是初始化方法），在创建类的实例时自动调用，用于初始化对象的属性。',
        true)
    RETURNING id INTO v_q11;

    -- Q12: 模块与包 - 难度4
    INSERT INTO questions (knowledge_point_id, course_id, question_type, difficulty, content, options, correct_answer, explanation, is_published)
    VALUES (v_kp12, v_course_id, 'SINGLE_CHOICE', 4,
        '导入 math 模块后，使用数学常数 π 的正确写法是？',
        '[
            {"label":"A","text":"pi"},
            {"label":"B","text":"math.pi"},
            {"label":"C","text":"math.PI"},
            {"label":"D","text":"MATH.pi"}
        ]'::jsonb,
        'B',
        '导入 math 模块后，需要通过 math.pi 访问圆周率。Python中变量名区分大小写，math模块中定义为小写 pi。',
        true)
    RETURNING id INTO v_q12;

    RAISE NOTICE 'Inserted 12 questions';

    -- ========================================================================
    -- 5. 插入答题记录
    --    为3个学生各生成约10条答题记录，模拟不同的学习情况
    -- ========================================================================

    -- student01（李明，计科2101）— 学得不错，正确率约70%
    INSERT INTO answer_records (student_id, question_id, knowledge_point_id, course_id, is_correct, student_answer, time_spent_seconds, attempted_at) VALUES
    (v_student01, v_q1, v_kp1, v_course_id, true, 'B', 15, v_now - interval '10 days'),
    (v_student01, v_q2, v_kp2, v_course_id, true, 'B', 20, v_now - interval '10 days'),
    (v_student01, v_q3, v_kp3, v_course_id, false, 'C', 45, v_now - interval '9 days'),
    (v_student01, v_q4, v_kp4, v_course_id, true, 'B', 25, v_now - interval '9 days'),
    (v_student01, v_q5, v_kp5, v_course_id, true, 'A', 18, v_now - interval '8 days'),
    (v_student01, v_q6, v_kp6, v_course_id, true, 'A', 30, v_now - interval '8 days'),
    (v_student01, v_q7, v_kp7, v_course_id, false, 'A', 60, v_now - interval '7 days'),
    (v_student01, v_q8, v_kp8, v_course_id, true, 'B', 22, v_now - interval '6 days'),
    (v_student01, v_q9, v_kp9, v_course_id, true, 'B', 35, v_now - interval '5 days'),
    (v_student01, v_q10, v_kp10, v_course_id, true, 'A', 15, v_now - interval '4 days'),
    (v_student01, v_q11, v_kp11, v_course_id, false, 'A', 55, v_now - interval '3 days'),
    (v_student01, v_q12, v_kp12, v_course_id, true, 'B', 28, v_now - interval '2 days');

    -- student02（王芳，计科2101）— 学得一般，正确率约50%
    INSERT INTO answer_records (student_id, question_id, knowledge_point_id, course_id, is_correct, student_answer, time_spent_seconds, attempted_at) VALUES
    (v_student02, v_q1, v_kp1, v_course_id, true, 'B', 20, v_now - interval '9 days'),
    (v_student02, v_q2, v_kp2, v_course_id, true, 'B', 25, v_now - interval '9 days'),
    (v_student02, v_q3, v_kp3, v_course_id, false, 'D', 50, v_now - interval '8 days'),
    (v_student02, v_q4, v_kp4, v_course_id, false, 'A', 30, v_now - interval '8 days'),
    (v_student02, v_q5, v_kp5, v_course_id, true, 'A', 20, v_now - interval '7 days'),
    (v_student02, v_q6, v_kp6, v_course_id, false, 'C', 65, v_now - interval '7 days'),
    (v_student02, v_q7, v_kp7, v_course_id, true, 'B', 40, v_now - interval '6 days'),
    (v_student02, v_q8, v_kp8, v_course_id, false, 'D', 35, v_now - interval '5 days'),
    (v_student02, v_q9, v_kp9, v_course_id, true, 'B', 30, v_now - interval '4 days'),
    (v_student02, v_q10, v_kp10, v_course_id, true, 'A', 18, v_now - interval '3 days'),
    (v_student02, v_q11, v_kp11, v_course_id, false, 'C', 70, v_now - interval '2 days'),
    (v_student02, v_q12, v_kp12, v_course_id, false, 'C', 45, v_now - interval '1 day');

    -- student03（赵强，数科2101）— 学得较差，正确率约33%
    INSERT INTO answer_records (student_id, question_id, knowledge_point_id, course_id, is_correct, student_answer, time_spent_seconds, attempted_at) VALUES
    (v_student03, v_q1, v_kp1, v_course_id, true, 'B', 25, v_now - interval '8 days'),
    (v_student03, v_q2, v_kp2, v_course_id, false, 'A', 35, v_now - interval '8 days'),
    (v_student03, v_q3, v_kp3, v_course_id, false, 'B', 60, v_now - interval '7 days'),
    (v_student03, v_q4, v_kp4, v_course_id, false, 'C', 40, v_now - interval '7 days'),
    (v_student03, v_q5, v_kp5, v_course_id, true, 'A', 22, v_now - interval '6 days'),
    (v_student03, v_q6, v_kp6, v_course_id, false, 'D', 75, v_now - interval '6 days'),
    (v_student03, v_q7, v_kp7, v_course_id, false, 'C', 55, v_now - interval '5 days'),
    (v_student03, v_q8, v_kp8, v_course_id, true, 'B', 30, v_now - interval '4 days'),
    (v_student03, v_q9, v_kp9, v_course_id, false, 'A', 50, v_now - interval '3 days'),
    (v_student03, v_q10, v_kp10, v_course_id, true, 'A', 20, v_now - interval '2 days'),
    (v_student03, v_q11, v_kp11, v_course_id, false, 'D', 65, v_now - interval '1 day');

    RAISE NOTICE 'Inserted answer records for 3 students';

    -- ========================================================================
    -- 6. 更新 student_profiles 的 bkt_state（模拟 BKT 参数）
    --    让仪表盘和诊断页面有数据显示
    -- ========================================================================

    -- student01 — 掌握度较高
    UPDATE student_profiles SET
        bkt_state = jsonb_build_object(
            'states', jsonb_build_object(
                v_kp1::text, 0.92, v_kp2::text, 0.88, v_kp3::text, 0.65,
                v_kp4::text, 0.85, v_kp5::text, 0.90, v_kp6::text, 0.78,
                v_kp7::text, 0.55, v_kp8::text, 0.82, v_kp9::text, 0.72,
                v_kp10::text, 0.88, v_kp11::text, 0.48, v_kp12::text, 0.76
            ),
            'overall_mastery', 0.75
        ),
        weak_areas = '["函数定义与调用", "面向对象基础"]'::jsonb,
        strengths = '["变量与数据类型", "条件判断", "异常处理"]'::jsonb,
        learning_efficiency = 0.82
    WHERE user_id = v_student01;

    -- student02 — 掌握度中等
    UPDATE student_profiles SET
        bkt_state = jsonb_build_object(
            'states', jsonb_build_object(
                v_kp1::text, 0.85, v_kp2::text, 0.78, v_kp3::text, 0.52,
                v_kp4::text, 0.45, v_kp5::text, 0.82, v_kp6::text, 0.42,
                v_kp7::text, 0.70, v_kp8::text, 0.50, v_kp9::text, 0.68,
                v_kp10::text, 0.85, v_kp11::text, 0.38, v_kp12::text, 0.45
            ),
            'overall_mastery', 0.60
        ),
        weak_areas = '["列表与元组", "循环结构", "字典与集合", "面向对象基础"]'::jsonb,
        strengths = '["Python简介", "条件判断", "异常处理"]'::jsonb,
        learning_efficiency = 0.65
    WHERE user_id = v_student02;

    -- student03 — 掌握度较低
    UPDATE student_profiles SET
        bkt_state = jsonb_build_object(
            'states', jsonb_build_object(
                v_kp1::text, 0.72, v_kp2::text, 0.45, v_kp3::text, 0.35,
                v_kp4::text, 0.30, v_kp5::text, 0.75, v_kp6::text, 0.28,
                v_kp7::text, 0.38, v_kp8::text, 0.68, v_kp9::text, 0.32,
                v_kp10::text, 0.78, v_kp11::text, 0.25, v_kp12::text, 0.30
            ),
            'overall_mastery', 0.42
        ),
        weak_areas = '["变量与数据类型","字符串操作","列表与元组","循环结构","函数定义与调用","文件操作","面向对象基础","模块与包"]'::jsonb,
        strengths = '["Python简介", "条件判断", "异常处理"]'::jsonb,
        learning_efficiency = 0.45
    WHERE user_id = v_student03;

    RAISE NOTICE 'Updated BKT states for 3 students';

    -- ========================================================================
    -- 7. 确保学生已选 CS101 课程
    -- ========================================================================
    INSERT INTO student_courses (student_id, course_id, course_code, status)
    VALUES
        (v_student01, v_course_id, 'CS101', 'active'),
        (v_student02, v_course_id, 'CS101', 'active'),
        (v_student03, v_course_id, 'CS101', 'active')
    ON CONFLICT (student_id, course_id) DO NOTHING;

    RAISE NOTICE 'Enrolled students in CS101';

    RAISE NOTICE '========================================================';
    RAISE NOTICE 'Python 程序设计课程测试数据插入完成！';
    RAISE NOTICE '知识点: 12 个';
    RAISE NOTICE '先修关系: 12 条';
    RAISE NOTICE '习题: 12 道';
    RAISE NOTICE '答题记录: student01(12), student02(12), student03(11)';
    RAISE NOTICE '========================================================';

END $$;
