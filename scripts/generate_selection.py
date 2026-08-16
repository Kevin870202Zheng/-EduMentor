import json
import urllib.request

token = open("/tmp/teacher_token.txt").read().strip()
course_id = "7e79c597-e907-4680-a7f3-be69bcd7eed8"

# 取3个LEAF知识点
import subprocess
kp_ids = subprocess.run(
    ["psql", "-h", "localhost", "-U", "edumentor_dev", "-d", "edumentor_dev", "-t", "-A",
     "-c", "SELECT id FROM knowledge_points WHERE type='LEAF' AND course_id='" + course_id + "' ORDER BY order_index LIMIT 3"],
    capture_output=True, text=True, env={"PGPASSWORD": "dev@123", "PATH": "/usr/bin:/bin:/usr/local/bin"}
).stdout.strip().split("\n")
kp_ids = [k for k in kp_ids if k]
print("勾选知识点:", len(kp_ids))

body = json.dumps({
    "courseId": course_id,
    "knowledgePointIds": kp_ids,
    "mode": "aggregated",
    "title": "聚合测试课堂：前三课知识点",
    "difficulty": 3,
    "courseName": "法学概论",
}, ensure_ascii=False).encode()

req = urllib.request.Request(
    "http://localhost:8080/api/v2/classrooms/generate-from-selection",
    data=body, method="POST",
    headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
try:
    resp = json.loads(urllib.request.urlopen(req, timeout=300).read())
    d = resp.get("data", {})
    print("结果:", resp.get("message"), resp.get("code"))
    print("课堂ID:", d.get("id"))
    print("标题:", d.get("title"))
    print("来源:", d.get("source"))
    print("场景数:", d.get("sceneCount"))
    print("总时长:", d.get("totalDurationSeconds"), "秒")
except Exception as e:
    print("生成失败:", e)
    if hasattr(e, "read"):
        try:
            print(e.read().decode())
        except Exception:
            pass
