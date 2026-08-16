import json
import urllib.request

PID = "b5b26ef3-caa1-41ff-b805-121120fc6d37"
token = open("/tmp/teacher_token.txt").read().strip()

# 任务ID → 复核内容
reviews = {
    "2599f882-bbce-4f59-a0b0-9658413575f6": {"storyId": "", "reason": "孔融让梨体现谦让美德，与公民权利义务意识契合（教师复核）"},
    "ae6dc2d6-7cd3-40a0-be73-122b7e696fd5": {"characters": "孔融：四岁孩童，圆脸大眼，素色汉服，谦让懂事；父亲：温厚长者，教子有方；哥哥们：活泼好动（教师修订版）"},
    "ec167740-eb2d-446f-a19e-00a72b850c22": {"script": "【开场·旁白】东汉年间，孔府庭院，梨香满园。\n孔融：我要这个最小的。\n父亲：为何不要大梨？\n孔融：我年纪最小，理当让着哥哥们。（教师修订版）"},
    "b6cc4406-9e62-47e7-b18a-c28014ac4b75": {"knowledgePointIds": ["b2dcd8f5-8ffd-4adc-a02f-9c1f0a8eca74", "a39292df-5678-4843-85b0-bbcc935a1867"], "mapping": "谦让对应公民权利中的权利边界意识（教师确认）"},
}

for task_id, content in reviews.items():
    if task_id == "2599f882-bbce-4f59-a0b0-9658413575f6":
        # 小学故事选择需要真实 storyId，这里读取数据库中孔融让梨的ID
        continue
    body = json.dumps({"content": json.dumps(content, ensure_ascii=False)}).encode()
    req = urllib.request.Request(
        f"http://localhost:8080/api/collab-classrooms/{PID}/tasks/{task_id}/review",
        data=body, method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    resp = json.loads(urllib.request.urlopen(req).read())
    print(f"复核 {task_id[:8]}: {resp.get('message')}")

# 小学故事选择复核（需要 storyId）
import subprocess
story_id = subprocess.run(
    ["psql", "-h", "localhost", "-U", "edumentor_dev", "-d", "edumentor_dev", "-t", "-A",
     "-c", "SELECT id FROM story_library WHERE title='孔融让梨'"],
    capture_output=True, text=True, env={"PGPASSWORD": "dev@123", "PATH": "/usr/bin:/bin:/usr/local/bin"}
).stdout.strip()
reviews["2599f882-bbce-4f59-a0b0-9658413575f6"]["storyId"] = story_id
body = json.dumps({"content": json.dumps(reviews["2599f882-bbce-4f59-a0b0-9658413575f6"], ensure_ascii=False)}).encode()
req = urllib.request.Request(
    f"http://localhost:8080/api/collab-classrooms/{PID}/tasks/2599f882-bbce-4f59-a0b0-9658413575f6/review",
    data=body, method="POST",
    headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
resp = json.loads(urllib.request.urlopen(req).read())
print(f"复核 STORY_PICKER: {resp.get('message')}")

# 检查最终状态
req2 = urllib.request.Request(f"http://localhost:8080/api/collab-classrooms/{PID}",
    headers={"Authorization": f"Bearer {token}"})
d = json.loads(urllib.request.urlopen(req2).read())["data"]
print("项目状态:", d["status"])
print("故事:", d.get("storyTitle"))
for t in d["tasks"]:
    print(f"  {t['roleType']}: {t['status']}")
