import json
import urllib.request

PID = "b5b26ef3-caa1-41ff-b805-121120fc6d37"
token = open("/tmp/teacher_token.txt").read().strip()

req = urllib.request.Request(
    f"http://localhost:8080/api/collab-classrooms/{PID}",
    headers={"Authorization": f"Bearer {token}"})
d = json.loads(urllib.request.urlopen(req).read())["data"]
print("项目状态:", d["status"])
print("课堂ID:", d.get("classroomId"))
print("故事:", d.get("storyTitle"))
for t in d["tasks"]:
    print(f"  {t['roleType']}: {t['status']} (学生: {t.get('assignedName')})")

# 验证课堂内容
cid = d.get("classroomId")
if cid:
    req2 = urllib.request.Request(
        f"http://localhost:8080/api/v2/classrooms/{cid}",
        headers={"Authorization": f"Bearer {token}"})
    c = json.loads(urllib.request.urlopen(req2).read()).get("data", {})
    print("\n课堂验证:")
    print("  标题:", c.get("title"))
    print("  来源:", c.get("source"))
    print("  场景数:", c.get("sceneCount"))
    print("  知识点ID:", c.get("knowledgePointId"))
