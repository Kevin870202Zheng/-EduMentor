import json
import urllib.request

PID = "b5b26ef3-caa1-41ff-b805-121120fc6d37"
token = open("/tmp/teacher_token.txt").read().strip()

req = urllib.request.Request(
    f"http://localhost:8080/api/collab-classrooms/{PID}/generate",
    data=b"{}", method="POST",
    headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
try:
    resp = json.loads(urllib.request.urlopen(req, timeout=300).read())
    d = resp.get("data", {})
    print("结果:", resp.get("message"), resp.get("code"))
    print("课堂ID:", d.get("id"))
    print("课堂标题:", d.get("title"))
    print("来源:", d.get("source"))
    print("场景数:", d.get("sceneCount"))
    print("总时长:", d.get("totalDurationSeconds"), "秒")
    open("/tmp/collab_classroom_id.txt", "w").write(d.get("id", ""))
except Exception as e:
    print("生成失败:", e)
    if hasattr(e, "read"):
        try:
            print(e.read().decode())
        except Exception:
            pass
