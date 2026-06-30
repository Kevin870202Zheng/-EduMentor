#!/usr/bin/env python3
"""为 LAW101 课程中缺少习题的知识点批量生成习题（每知识点3道）"""
import json, subprocess, time

CID = "7e79c597-e907-4680-a7f3-be69bcd7eed8"
DS_KEY = open("/Users/roosevelt/.youcoder/config.toml").read().split('api_key = "')[1].split('"')[0]
API = "https://api.deepseek.com/chat/completions"

def query(sql):
    r = subprocess.run(["psql", "-U", "roosevelt", "-d", "edumentor_dev", "-t", "-A", "-F", "|", "-c", sql],
                       capture_output=True, text=True)
    return r.stdout.strip()

def call_llm(kps_batch):
    prompt = f"""你是一个课程习题生成助手。请为以下每个知识点生成3道练习题。
要求：
- 题型多样：单选题(SINGLE_CHOICE)、多选题(MULTIPLE_CHOICE)、判断题(TRUE_FALSE)、填空题(FILL_BLANK)、简答题(SHORT_ANSWER)
- 选择题必须包含4个选项
- 每道题必须包含正确答案和解析
- 输出严格JSON格式

知识点列表：
{chr(10).join(f'{i+1}. {kp}' for i,kp in enumerate(kps_batch))}

输出格式示例：
{{"questions": [
  {{"kpName":"知识点名称","content":"题目","type":"SINGLE_CHOICE","options":["A. 选项","B. 选项","C. 选项","D. 选项"],"correctAnswer":"A","explanation":"解析"}}
]}}"""

    data = {
        "model": "deepseek-v4-flash",
        "messages": [
            {"role": "system", "content": "你是一个课程习题生成助手。严格输出JSON。"},
            {"role": "user", "content": prompt}
        ],
        "max_tokens": 8000,
        "temperature": 0.3
    }
    
    for attempt in range(3):
        try:
            r = subprocess.run(["curl", "-s", "-X", "POST", API,
                "-H", "Content-Type: application/json",
                "-H", f"Authorization: Bearer {DS_KEY}",
                "-d", json.dumps(data, ensure_ascii=False)],
                capture_output=True, text=True, timeout=120)
            resp = json.loads(r.stdout)
            content = resp['choices'][0]['message'].get('content', '')
            start = content.find('{')
            end = content.rfind('}') + 1
            if start >= 0 and end > start:
                result = json.loads(content[start:end])
                return result.get('questions', [])
        except Exception as e:
            print(f"  重试 {attempt+1}: {e}")
            time.sleep(5)
    return []

print("获取无习题的知识点...")
rows = query(f"SELECT name FROM knowledge_points WHERE course_id='{CID}' AND id NOT IN (SELECT DISTINCT knowledge_point_id FROM questions WHERE course_id='{CID}') ORDER BY order_index")
kps = [r for r in rows.split('\n') if r.strip()]
print(f"需要补习题的知识点: {len(kps)} 个")

total_new = 0
batch_size = 15
for i in range(0, len(kps), batch_size):
    batch = kps[i:i+batch_size]
    print(f"\n批次 {i//batch_size + 1}/{(len(kps)-1)//batch_size + 1}: {batch[0][:20]}... ({len(batch)}个)")
    
    questions = call_llm(batch)
    if not questions:
        print("  跳过")
        continue
    
    saved = 0
    for q in questions:
        kp_name = q.get('kpName', '').strip()
        content = q.get('content', '').strip()
        if not content:
            continue
        
        # 查找知识点
        kp_id = ""
        if kp_name:
            kp_id = query(f"SELECT id::text FROM knowledge_points WHERE course_id='{CID}' AND REPLACE(LOWER(TRIM(name)),' ','')=REPLACE(LOWER(TRIM('{kp_name.replace(chr(39),chr(39)*2)}')),' ','')")
        if not kp_id and batch:
            kp_id = query(f"SELECT id::text FROM knowledge_points WHERE course_id='{CID}' AND name='{batch[len(saved)//3 if saved<len(batch) else 0].replace(chr(39),chr(39)*2)}' LIMIT 1")
        if not kp_id:
            kp_id = query(f"SELECT id::text FROM knowledge_points WHERE course_id='{CID}' ORDER BY order_index LIMIT 1 OFFSET {i}")
        if not kp_id:
            continue
        
        exist = query(f"SELECT COUNT(*) FROM questions WHERE course_id='{CID}' AND knowledge_point_id='{kp_id}' AND content='{content.replace(chr(39),chr(39)*2)}'")
        if int(exist) > 0:
            continue
        
        opts = q.get('options', [])
        opts_json = 'null'
        if opts and len(opts) > 0:
            opt_map = {}
            for idx, o in enumerate(opts):
                label = chr(65+idx)
                text = o.split('. ', 1)[-1] if isinstance(o, str) and '. ' in o else str(o)
                opt_map[label] = text
            opts_json = json.dumps(opt_map, ensure_ascii=False).replace("'", "''")
        
        q_type = q.get('type', 'SINGLE_CHOICE')
        type_map = {'单选题':'SINGLE_CHOICE','多选题':'MULTIPLE_CHOICE','判断题':'TRUE_FALSE','填空题':'FILL_BLANK','简答题':'SHORT_ANSWER','论述题':'ESSAY'}
        q_type = type_map.get(q_type, q_type)
        if q_type not in ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','FILL_BLANK','SHORT_ANSWER','ESSAY','CODING'):
            q_type = 'SINGLE_CHOICE'
        
        correct = q.get('correctAnswer', '').replace("'", "''")
        explanation = q.get('explanation', '').replace("'", "''")
        content_esc = content.replace("'", "''")
        
        subprocess.run(["psql", "-U", "roosevelt", "-d", "edumentor_dev", "-c",
            f"INSERT INTO questions (knowledge_point_id, course_id, question_type, content, options, correct_answer, explanation, difficulty, is_published, created_at) "
            f"VALUES ('{kp_id}','{CID}','{q_type}','{content_esc}','{opts_json}','{correct}','{explanation}',3,true,NOW());"],
            capture_output=True)
        saved += 1
    
    total_new += saved
    print(f"  本批新增 {saved} 道习题")
    time.sleep(3)

print(f"\n=== 完成！共新增 {total_new} 道习题 ===")
total = query(f"SELECT COUNT(*) FROM questions WHERE course_id='{CID}'")
print(f"课程总习题数: {total}")
