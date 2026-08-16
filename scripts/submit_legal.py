import json
import urllib.request

PID = "b5b26ef3-caa1-41ff-b805-121120fc6d37"
token = open("/tmp/univ_token.txt").read().strip()
kp_ids = ["b2dcd8f5-8ffd-4adc-a02f-9c1f0a8eca74", "a39292df-5678-4843-85b0-bbcc935a1867"]
content = {"knowledgePointIds": kp_ids, "mapping": "谦让对应公民权利中的权利边界意识，孔融让梨体现权利让渡与相互尊重"}
body = json.dumps({"content": json.dumps(content, ensure_ascii=False)}).encode()

req = urllib.request.Request(
    f"http://localhost:8080/api/collab-classrooms/{PID}/tasks/b6cc4406-9e62-47e7-b18a-c28014ac4b75/submit",
    data=body, method="POST",
    headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
resp = json.loads(urllib.request.urlopen(req).read())
print("提交:", resp.get("message"), resp.get("code"))

req2 = urllib.request.Request(
    f"http://localhost:8080/api/collab-classrooms/{PID}",
    headers={"Authorization": f"Bearer {token}"})
d = json.loads(urllib.request.urlopen(req2).read())["data"]
print("项目状态:", d["status"])
for t in d["tasks"]:
    print(f"  {t['roleType']}: {t['status']}")
