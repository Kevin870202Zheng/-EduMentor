#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""智慧课堂形式升级 C1 验证：生成含 slide 布局 + interactive 组件的课堂并检查结构"""
import json
import subprocess
import urllib.request
import sys
import time

BASE = "http://localhost:8080/api"
COURSE_ID = "7e79c597-e907-4680-a7f3-be69bcd7eed8"  # 法学概论

def http(method, path, body=None, token=None, timeout=600):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"error": e.code, "body": e.read().decode()[:500]}

def psql(sql):
    r = subprocess.run(
        ["psql", "-h", "localhost", "-U", "edumentor_dev", "-d", "edumentor_dev", "-t", "-A", "-c", sql],
        capture_output=True, text=True,
        env={"PGPASSWORD": "dev@123", "PATH": "/usr/bin:/bin:/usr/local/bin"})
    return [x for x in r.stdout.strip().split("\n") if x]

def main():
    # 1. 登录
    login = http("POST", "/auth/login", {"username": "student01", "password": "student123"})
    token = (login.get("data") or login).get("accessToken")
    if not token:
        print("❌ 登录失败:", login)
        sys.exit(1)
    print("✅ 登录成功")

    # 2. 查诉讼/程序/配对类 LEAF 知识点（更容易触发 interactive）
    rows = psql(f"""SELECT id, name FROM knowledge_points
        WHERE course_id='{COURSE_ID}' AND type='LEAF'
          AND (name LIKE '%程序%' OR name LIKE '%诉讼%' OR name LIKE '%流程%'
               OR name LIKE '%责任%' OR name LIKE '%原则%' OR name LIKE '%行为%')
        ORDER BY order_index LIMIT 5""")
    if not rows:
        rows = psql(f"""SELECT id, name FROM knowledge_points
            WHERE course_id='{COURSE_ID}' AND type='LEAF'
            ORDER BY order_index LIMIT 5""")
    kps = [r.split("|") for r in rows if "|" in r]
    print(f"✅ 勾选 {len(kps)} 个知识点:")
    for _, name in kps:
        print(f"   - {name}")

    # 3. 生成聚合课堂（标题加时间戳避免唯一约束冲突）
    stamp = time.strftime("%m%d%H%M")
    body = {
        "courseId": COURSE_ID,
        "knowledgePointIds": [k for k, _ in kps],
        "mode": "aggregated",
        "title": f"升级验证课堂：PPT+交互组件 {stamp}",
        "difficulty": 3,
        "courseName": "法学概论",
    }
    print("⏳ 生成中（聚合课堂，预计 3-8 分钟）...")
    t0 = time.time()
    resp = http("POST", "/v2/classrooms/generate-from-selection", body, token, timeout=900)
    data = resp.get("data", resp)
    cid = data.get("id")
    if not cid:
        print("❌ 生成失败:", resp)
        sys.exit(1)
    print(f"✅ 课堂已生成 id={cid} 标题={data.get('title')} 场景数={data.get('sceneCount')} 耗时={int(time.time()-t0)}s")

    # 4. 获取详情并分析结构
    detail = http("GET", f"/v2/classrooms/{cid}", token=token)
    d = detail.get("data", detail)
    scenes = d.get("scenes", [])
    print(f"\n📊 共 {len(scenes)} 个场景，结构分析:")
    stats = {"slides": 0, "widget": 0, "summaryMap": 0, "show_slide": 0,
             "launch_widget": 0, "widget_actions": 0, "interactive": 0}
    for s in scenes:
        content = s.get("content") or {}
        # 兼容嵌套结构：后端把 contentJson 序列化在 content.contentJson
        cj = content.get("contentJson") or {}
        if isinstance(cj, dict):
            content = {**content, **cj}
        slides = content.get("slides")
        widget = content.get("widget")
        smap = content.get("summaryMap")
        if isinstance(slides, str):
            try: slides = json.loads(slides)
            except Exception: slides = None
        if isinstance(widget, str):
            try: widget = json.loads(widget)
            except Exception: widget = None
        acts = s.get("actions") or []
        a_types = [a.get("type") for a in acts]
        flag = []
        if slides: stats["slides"] += 1; flag.append(f"slides({len(slides)}页)")
        if widget: stats["widget"] += 1; flag.append(f"widget({widget.get('subtype')})")
        if smap: stats["summaryMap"] += 1; flag.append("summaryMap")
        if "show_slide" in a_types: stats["show_slide"] += 1
        if "launch_widget" in a_types: stats["launch_widget"] += 1
        stats["widget_actions"] += len([t for t in a_types if t.startswith("widget_")])
        if s.get("sceneType") == "interactive": stats["interactive"] += 1
        print(f"  [{s.get('sceneType')}] {s.get('title')} 动作={a_types[:4]}{'…' if len(a_types)>4 else ''} {'🏷️'+','.join(flag) if flag else ''}")

    print("\n📈 汇总:", json.dumps(stats, ensure_ascii=False))

    # 5. 校验：至少有一个 slides 或 widget 场景
    ok = stats["slides"] > 0 or stats["widget"] > 0
    print("\n" + ("✅ 通过：课堂包含 PPT 布局 / 交互组件" if ok else "⚠️ 未检测到新结构，需检查 LLM 输出或提示词"))
    return 0 if ok else 2

if __name__ == "__main__":
    sys.exit(main())
