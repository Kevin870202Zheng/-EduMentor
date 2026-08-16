#!/usr/bin/env python3
"""生成法学概论 4 个路径模板数据（EXAM/LITIGATION/INTEREST/TEACHING）。

规则（见 .youcoder/plans/learning-path-self-planning-design.html）：
- EXAM      课程考试：解析试卷文本 → 知识点名称/法律术语匹配 → 按出现频次降序选取至 ≈48 课时(2880 分钟)
- LITIGATION 纠纷解决：关键词(诉讼/程序/证据/司法/审判/仲裁/调解/民事/刑事/行政) + 主题归类，直至 ≈64 课时(3840 分钟)
- INTEREST   兴趣拓展：课程全部 LEAF 知识点，total_minutes=NULL
- TEACHING   师范生备课：动态规则(RULE_BY_STAGE)，不落节点

用法：
  python3 scripts/seed-path-templates.py [--execute]   # 默认生成 SQL 到 stdout；--execute 直接执行入库
"""
import subprocess
import sys
import collections

COURSE_ID = "7e79c597-e907-4680-a7f3-be69bcd7eed8"  # 法学概论
EXAM_BUDGET = 2880   # 48 课时
LIT_BUDGET = 3840    # 64 课时

# 试卷材料标题关键字
PAPER_TITLE_KEYWORDS = ("试卷", "考试")

# LITIGATION 关键词规则
LITIGATION_KEYWORDS = ["诉讼", "程序", "证据", "司法", "审判", "仲裁", "调解",
                       "民事", "刑事", "行政"]
# LITIGATION 主题 code 补充（V17 归类）
LITIGATION_THEME_CODES = ["ORDER_SAFETY", "CIVIL_LAW", "GOVERNANCE"]

# 高频法律术语（用于 EXAM 加权匹配）
TERMS = [
    "刑法", "民法", "宪法", "行政法", "刑事诉讼法", "民事诉讼法", "行政诉讼法",
    "民法典", "刑法典", "合同", "侵权", "继承", "婚姻", "物权", "债权", "担保",
    "犯罪", "正当防卫", "紧急避险", "诉讼时效", "行政处罚", "行政复议", "行政诉讼",
    "国家赔偿", "证据", "审判", "仲裁", "调解", "法人", "公司", "票据", "保险",
    "监护", "抚养", "赡养", "名誉权", "隐私权", "肖像权", "知识产权", "著作权",
    "专利权", "商标", "网络安全", "数据", "劳动合同", "劳动法", "消费者", "产品",
    "食品", "环境", "污染", "不动产", "机动车", "交通", "治安", "拘留", "逮捕",
    "侦查", "起诉", "上诉", "再审", "执行", "法律援助", "法律监督", "立法", "执法",
    "法治", "依法治国", "法律规范", "法律关系", "法律事实", "法律责任", "公民",
    "权利", "义务", "主权", "领土", "条约", "国际法", "缔约", "人权", "物权法",
    "法律责任", "民法总则", "刑诉", "民诉", "行诉", "法理学", "法制", "司法审查",
]


def query(sql):
    r = subprocess.run(
        ["psql", "-U", "roosevelt", "-d", "edumentor_dev", "-t", "-A", "-F", "|", "-c", sql],
        capture_output=True, text=True)
    if r.returncode != 0:
        print(f"[psql 错误] {r.stderr.strip()}", file=sys.stderr)
    return r.stdout.strip()


def fetch_papers():
    """获取试卷文本列表 {title, text}（raw_text 换行替换为空格，保证单行输出）"""
    rows = query(
        "SELECT title, REPLACE(COALESCE(raw_text,''), E'\\n', ' ') FROM course_materials "
        f"WHERE course_id='{COURSE_ID}' AND (title LIKE '%试卷%' OR title LIKE '%考试%') "
        "AND raw_text IS NOT NULL AND length(raw_text) > 500 "
        "ORDER BY created_at")
    papers = []
    for line in rows.split("\n"):
        if not line.strip():
            continue
        title, _, text = line.partition("|")
        if text:
            papers.append({"title": title.strip(), "text": text})
    return papers


def fetch_kps():
    """获取 LEAF 知识点 [id, name, difficulty, theme_id, theme_code]"""
    rows = query(
        "SELECT kp.id::text, kp.name, kp.difficulty, COALESCE(kp.theme_id::text,''), COALESCE(st.code,'') "
        "FROM knowledge_points kp "
        "LEFT JOIN subject_themes st ON st.id = kp.theme_id "
        f"WHERE kp.course_id='{COURSE_ID}' AND kp.type='LEAF' "
        "ORDER BY kp.order_index ASC")
    kps = []
    for line in rows.split("\n"):
        parts = line.split("|")
        if len(parts) >= 4 and parts[0].strip():
            kps.append({
                "id": parts[0].strip(),
                "name": parts[1].strip(),
                "difficulty": int(parts[2].strip() or "3"),
                "theme_id": parts[3].strip(),
                "theme_code": parts[4].strip() if len(parts) > 4 else "",
            })
    return kps


def estimate_minutes(difficulty):
    return 30 + (difficulty - 1) * 15


def score_kps(kps, papers):
    """计算每个知识点的试卷相关度分数（EXAM 排序依据）"""
    # 术语 → 全部试卷出现次数
    all_text = "\n".join(p["text"] for p in papers)
    term_count = {}
    for term in TERMS:
        term_count[term] = all_text.count(term)

    scored = []
    for kp in kps:
        exact = 0
        for p in papers:
            exact += p["text"].count(kp["name"])
        term_score = sum(c for t, c in term_count.items() if t in kp["name"])
        # 直接名称匹配权重高；术语命中次之
        score = exact * 10 + term_score
        scored.append({**kp, "score": score, "exact": exact})
    return scored


def pick_by_budget(scored, budget):
    """按分数降序选取，直至累计分钟数 ≈ budget（分钟）"""
    ordered = sorted(scored, key=lambda k: (-k["score"], k["name"]))
    picked = []
    acc = 0
    for kp in ordered:
        m = estimate_minutes(kp["difficulty"])
        if acc + m > budget and picked:
            continue
        picked.append(kp)
        acc += m
    return picked


def esc(s):
    return s.replace("'", "''")


def build_sql(templates, nodes_by_code):
    lines = []
    lines.append("-- ============================================================")
    lines.append("-- 法学概论 路径模板种子数据（脚本生成，可重复执行）")
    lines.append(f"-- 课程: {COURSE_ID}")
    lines.append("-- ============================================================")
    lines.append("")
    for tpl in templates:
        lines.append(f"DELETE FROM path_template_nodes WHERE template_id IN "
                     f"(SELECT id FROM path_templates WHERE course_id='{COURSE_ID}' AND code='{tpl['code']}');")
        lines.append(f"DELETE FROM path_templates WHERE course_id='{COURSE_ID}' AND code='{tpl['code']}';")
        lines.append("")
        total = "NULL" if tpl.get("total_minutes") is None else tpl["total_minutes"]
        lines.append(
            f"INSERT INTO path_templates (course_id, code, name, description, icon, "
            f"total_minutes, node_count, is_visible, template_type, sort_order, created_at, updated_at) "
            f"VALUES ('{COURSE_ID}', '{tpl['code']}', '{esc(tpl['name'])}', '{esc(tpl['description'])}', "
            f"'{tpl['icon']}', {total}, {len(nodes_by_code.get(tpl['code'], []))}, TRUE, "
            f"'{tpl['template_type']}', {tpl['sort_order']}, NOW(), NOW());")
        nodes = nodes_by_code.get(tpl["code"], [])
        if nodes:
            lines.append("")
            lines.append(f"-- {tpl['name']} 节点（{len(nodes)} 个）")
            for i, n in enumerate(nodes):
                tpl_id_sub = f"(SELECT id FROM path_templates WHERE course_id='{COURSE_ID}' AND code='{tpl['code']}')"
                lines.append(
                    f"INSERT INTO path_template_nodes (template_id, knowledge_point_id, knowledge_point_name, "
                    f"order_index, estimated_minutes, created_at) VALUES "
                    f"({tpl_id_sub}, '{n['id']}', '{esc(n['name'])}', {i}, "
                    f"{estimate_minutes(n['difficulty'])}, NOW());")
        lines.append("")
    return "\n".join(lines)


def main():
    execute = "--execute" in sys.argv
    papers = fetch_papers()
    kps = fetch_kps()
    if not papers:
        print("未找到试卷材料，中止", file=sys.stderr)
        sys.exit(1)
    if not kps:
        print("未找到 LEAF 知识点，中止", file=sys.stderr)
        sys.exit(1)

    print(f"试卷 {len(papers)} 份 / LEAF 知识点 {len(kps)} 个", file=sys.stderr)
    scored = score_kps(kps, papers)
    nonzero = [k for k in scored if k["score"] > 0]
    print(f"命中试卷匹配的知识点: {len(nonzero)} 个", file=sys.stderr)

    # EXAM：按分数 top 选取至 2880 分钟
    exam_nodes = pick_by_budget(scored, EXAM_BUDGET)
    # LITIGATION：关键词/主题过滤 + 分数排序至 3840 分钟
    lit_candidates = [
        k for k in scored
        if any(kw in k["name"] for kw in LITIGATION_KEYWORDS)
        or k["theme_code"] in LITIGATION_THEME_CODES
    ]
    lit_nodes = pick_by_budget(lit_candidates, LIT_BUDGET)
    # INTEREST：全量
    interest_nodes = list(scored)

    templates = [
        {
            "code": "EXAM", "name": "课程考试", "icon": "📘",
            "description": "试卷高频考点优先，48 课时应试冲刺",
            "total_minutes": EXAM_BUDGET, "template_type": "STATIC", "sort_order": 1,
        },
        {
            "code": "LITIGATION", "name": "纠纷解决", "icon": "⚖️",
            "description": "司法程序与证据实务，64 课时实战进阶",
            "total_minutes": LIT_BUDGET, "template_type": "STATIC", "sort_order": 2,
        },
        {
            "code": "INTEREST", "name": "兴趣拓展", "icon": "🔭",
            "description": "完整课程知识库，全量覆盖",
            "total_minutes": None, "template_type": "STATIC", "sort_order": 3,
        },
        {
            "code": "TEACHING", "name": "师范生备课", "icon": "📚",
            "description": "按目标学段动态分课，一课 4 课时深度备课",
            "total_minutes": None, "template_type": "RULE_BY_STAGE", "sort_order": 4,
        },
    ]
    nodes_by_code = {
        "EXAM": exam_nodes,
        "LITIGATION": lit_nodes,
        "INTEREST": interest_nodes,
        "TEACHING": [],
    }
    print(f"EXAM 节点 {len(exam_nodes)}（{sum(estimate_minutes(k['difficulty']) for k in exam_nodes)} 分钟）", file=sys.stderr)
    print(f"LITIGATION 节点 {len(lit_nodes)}（{sum(estimate_minutes(k['difficulty']) for k in lit_nodes)} 分钟）", file=sys.stderr)
    print(f"INTEREST 节点 {len(interest_nodes)}", file=sys.stderr)

    sql = build_sql(templates, nodes_by_code)
    if execute:
        r = subprocess.run(
            ["psql", "-U", "roosevelt", "-d", "edumentor_dev", "-v", "ON_ERROR_STOP=1", "-c", sql],
            capture_output=True, text=True)
        if r.returncode != 0:
            print(f"[执行失败] {r.stderr.strip()}", file=sys.stderr)
            sys.exit(1)
        print("模板数据已入库", file=sys.stderr)
        # 校验
        chk = query("SELECT code, node_count FROM path_templates WHERE course_id=%s ORDER BY sort_order" % f"'{COURSE_ID}'")
        print("当前模板：", file=sys.stderr)
        print(chk, file=sys.stderr)
    else:
        print(sql)


if __name__ == "__main__":
    main()
