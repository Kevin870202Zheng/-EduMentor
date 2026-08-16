-- ============================================================================
-- V20__classroom_generator_and_collab.sql
-- 智慧课堂生成器 — 数据模型落库（设计文档 classroom-generator-design v1.1）
--
-- 包含：
--   ① classrooms        放开 knowledge_point_id 强绑定 + 新增 source 生成来源
--   ② story_library     中华传统故事库（本期预置 6 个故事）
--   ③ collab_classroom_projects  学段协作课堂项目（教师发起）
--   ④ collab_project_tasks       协作任务（四学段角色分工）
--
-- 约定：全部使用 IF NOT EXISTS / IF EXISTS 保证幂等，可重复执行
-- ============================================================================

-- ============================================================================
-- ① classrooms — 放开知识点强绑定 + 生成来源标识
--    source: knowledge（单知识点）| multi_knowledge（多知识点聚合）| collaborative（学段协作）
-- ============================================================================
ALTER TABLE classrooms
    ALTER COLUMN knowledge_point_id DROP NOT NULL;

ALTER TABLE classrooms
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'knowledge';

CREATE INDEX IF NOT EXISTS idx_classrooms_source ON classrooms (source);

-- ============================================================================
-- ② story_library — 中华传统故事库
--    本期预置数据，后续演进为公共知识库由教师维护（created_by + status 预留）
-- ============================================================================
CREATE TABLE IF NOT EXISTS story_library (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,           -- 故事名
    content     TEXT        NOT NULL,             -- 故事原文
    dynasty     VARCHAR(64),                      -- 朝代/出处年代
    source_ref  VARCHAR(255),                     -- 出处（如《世说新语》）
    keywords    TEXT,                             -- 关键词（逗号分隔）
    theme_id    UUID,                             -- 建议关联法律主题（subject_themes.code）
    status      VARCHAR(16) NOT NULL DEFAULT 'published',
    created_by  UUID,                             -- 创建人（后续公共库教师维护）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_story_theme ON story_library (theme_id);
CREATE INDEX IF NOT EXISTS idx_story_status ON story_library (status);

-- ── 预置 6 个中华传统故事（幂等：按 title 去重） ──

INSERT INTO story_library (title, content, dynasty, source_ref, keywords, theme_id, status, created_by)
SELECT '孔融让梨', '东汉时期，孔融有五个哥哥和一个弟弟。一天，家里买来一筐梨，父亲让他们兄弟自己挑。哥哥们抢先挑大的、好的梨，而四岁的孔融却挑了一个最小的梨。父亲问他："你为什么挑最小的梨呢？"孔融回答："我年纪最小，应该吃小梨，把大梨让给哥哥们。"父亲又问："那弟弟不是比你更小吗？"孔融说："弟弟比我小，我更应该让着他。"父亲听了十分高兴，夸赞孔融小小年纪就懂得谦让。孔融让梨的故事流传千古，成为中华传统美德"谦让"的典范。',
       '东汉', '《世说新语》', '谦让,礼貌,家庭,公平', (SELECT id FROM subject_themes WHERE code = 'CIVIL_RIGHTS'), 'published', NULL
WHERE NOT EXISTS (SELECT 1 FROM story_library WHERE title = '孔融让梨');

INSERT INTO story_library (title, content, dynasty, source_ref, keywords, theme_id, status, created_by)
SELECT '司马光砸缸', '北宋时期，司马光小时候和一群孩子在院子里玩耍。院子里有一口大水缸，缸里装满了水。一个孩子爬到缸沿上玩耍，一不小心掉进了水缸里。水缸又高又大，孩子在里面拼命挣扎，眼看就要被水淹没。其他孩子都吓坏了，有的哭，有的喊，有的跑去找大人。只有司马光没有慌张，他急中生智，搬起一块大石头，使劲砸向水缸。"砰"的一声，水缸被砸出一个大洞，水哗哗地流了出来，掉进缸里的孩子得救了。司马光砸缸救人的故事告诉我们：遇到危险时要沉着冷静，善于想办法解决问题。',
       '北宋', '《宋史》', '机智,救人,危险,冷静', (SELECT id FROM subject_themes WHERE code = 'ORDER_SAFETY'), 'published', NULL
WHERE NOT EXISTS (SELECT 1 FROM story_library WHERE title = '司马光砸缸');

INSERT INTO story_library (title, content, dynasty, source_ref, keywords, theme_id, status, created_by)
SELECT '铁杵磨成针', '唐代大诗人李白小时候在四川眉山读书。他生性贪玩，常常逃学。一天，李白又溜出学堂，来到一条小河边玩耍。河边有一位老婆婆，正蹲在一块大石头上，专心致志地磨一根粗粗的铁杵。李白好奇地问："老婆婆，您磨这根铁杵做什么呀？"老婆婆头也不抬地回答："我要把它磨成一根绣花针。"李白惊讶地说："这么粗的铁杵，什么时候才能磨成针呀？"老婆婆笑着说："只要功夫深，铁杵磨成针。一天磨一点，天天坚持，总能磨成。"李白听了深受触动，从此发奋读书，终于成为一代诗仙。',
       '唐代', '《方舆胜览》', '坚持,勤奋,毅力,诚信', (SELECT id FROM subject_themes WHERE code = 'LAW_RULE'), 'published', NULL
WHERE NOT EXISTS (SELECT 1 FROM story_library WHERE title = '铁杵磨成针');

INSERT INTO story_library (title, content, dynasty, source_ref, keywords, theme_id, status, created_by)
SELECT '曹冲称象', '三国时期，有人送给曹操一头大象。曹操十分高兴，带着儿子和大臣们一起去看。大象又高又大，身子像一堵墙，腿像四根粗柱子。曹操问："这头大象到底有多重？谁能称出它的重量？"大臣们议论纷纷，有人说造一杆大秤，有人说把大象杀了切成块称，曹操听了都摇头。这时，曹操七岁的儿子曹冲站出来说："我有个办法！把大象牵到船上，在船身刻下水位线；再把大象牵下船，往船上装石头，装到水位线一样的位置。石头的重量就是大象的重量。"曹操照做，果然称出了大象的重量。曹冲用"等量代换"的方法解决了难题，大臣们无不佩服他的智慧。',
       '三国', '《三国志》', '智慧,科学,证据,思考', (SELECT id FROM subject_themes WHERE code = 'JUSTICE'), 'published', NULL
WHERE NOT EXISTS (SELECT 1 FROM story_library WHERE title = '曹冲称象');

INSERT INTO story_library (title, content, dynasty, source_ref, keywords, theme_id, status, created_by)
SELECT '东郭先生与狼', '从前，有一位善良的读书人叫东郭先生。一天，他赶着一头毛驴走在路上，迎面跑来一只受伤的狼。狼哀求道："先生，猎人在追我，求您救救我吧！"东郭先生心一软，就让狼躲进自己的书袋里。猎人追上来问："你看见一只狼了吗？"东郭先生摇摇头说："没看见。"猎人走后，狼从书袋里钻出来，却张牙舞爪地说："我饿了，你救了我，就让我吃了你吧！"东郭先生吓得连连后退。这时来了一位老农，听完经过后，老农说："我不信狼能钻进这么小的书袋，你们再演示一遍。"狼得意地又钻了进去，老农立刻把袋口扎紧，把恶狼打死了。老农对东郭先生说："对狼讲仁慈，就是对自己残忍啊！"',
       '明代', '《中山狼传》', '善良,辨别,自我保护,防骗', (SELECT id FROM subject_themes WHERE code = 'ORDER_SAFETY'), 'published', NULL
WHERE NOT EXISTS (SELECT 1 FROM story_library WHERE title = '东郭先生与狼');

INSERT INTO story_library (title, content, dynasty, source_ref, keywords, theme_id, status, created_by)
SELECT '南辕北辙', '战国时期，有个人想从中原到南方的楚国去。他驾着马车，却一个劲儿地向北边赶路。路上遇到一位朋友，朋友问他："你要去哪里呀？"他大声说："我要去楚国！"朋友惊讶地说："楚国在南边，你怎么往北走呢？"他不以为然地回答："没关系，我的马跑得快！"朋友说："马跑得越快，离楚国就越远呀！"他又说："我的盘缠多！"朋友说："盘缠再多，方向不对也是白费。"他还不服气："我的车夫赶车的本领高！"朋友叹气道："你的马越快、钱越多、车夫本领越高，可方向错了，只会离目的地越来越远。"这个故事告诉我们：做事之前要先确定正确的方向，方向错了，条件越好、力气越大，反而错得越远。',
       '战国', '《战国策》', '方向,规则,目标,方法', (SELECT id FROM subject_themes WHERE code = 'GOVERNANCE'), 'published', NULL
WHERE NOT EXISTS (SELECT 1 FROM story_library WHERE title = '南辕北辙');

-- ============================================================================
-- ③ collab_classroom_projects — 学段协作课堂项目（教师发起）
--    状态机: DRAFT → INVITING → COLLECTING → REVIEW → GENERATING → PUBLISHED
-- ============================================================================
CREATE TABLE IF NOT EXISTS collab_classroom_projects (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255) NOT NULL,        -- 项目标题
    description     TEXT,                          -- 项目描述
    course_id       UUID,                          -- 法律知识来源课程（courses.id）
    creator_id      UUID        NOT NULL,          -- 发起者（教师）
    story_id        UUID,                          -- 选定的故事（story_library.id，由小学学生提交）
    difficulty      INTEGER     NOT NULL DEFAULT 3 CHECK (difficulty BETWEEN 1 AND 5),
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','INVITING','COLLECTING','REVIEW','GENERATING','PUBLISHED','ARCHIVED')),
    classroom_id    UUID,                          -- 生成后的课堂（classrooms.id）
    metadata        JSONB,                         -- 聚合知识点等扩展信息
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_collab_creator ON collab_classroom_projects (creator_id);
CREATE INDEX IF NOT EXISTS idx_collab_status ON collab_classroom_projects (status);
CREATE INDEX IF NOT EXISTS idx_collab_course ON collab_classroom_projects (course_id);

-- ============================================================================
-- ④ collab_project_tasks — 协作任务（四学段角色分工）
--    role_type: STORY_PICKER（小学选故事）/ CHARACTER_DESIGNER（初中角色形象）
--               / SCRIPT_WRITER（高中台词）/ LEGAL_MAPPER（大学法律映射）
--    content 为 JSON 文本，不同角色结构不同（见设计文档 5.3）
-- ============================================================================
CREATE TABLE IF NOT EXISTS collab_project_tasks (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID        NOT NULL,        -- 所属项目
    role_type        VARCHAR(24) NOT NULL
                     CHECK (role_type IN ('STORY_PICKER','CHARACTER_DESIGNER','SCRIPT_WRITER','LEGAL_MAPPER')),
    required_stage   VARCHAR(16) NOT NULL,        -- PRIMARY/JUNIOR/SENIOR/UNIVERSITY
    assigned_user_id UUID,                         -- 被邀学生
    content          TEXT,                         -- 回复内容（JSON 文本）
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','COMPLETED','REVIEWED')),
    submitted_at     TIMESTAMPTZ,
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, role_type)
);

CREATE INDEX IF NOT EXISTS idx_collab_task_project ON collab_project_tasks (project_id);
CREATE INDEX IF NOT EXISTS idx_collab_task_assigned ON collab_project_tasks (assigned_user_id);
CREATE INDEX IF NOT EXISTS idx_collab_task_status ON collab_project_tasks (status);

-- end of migration script
