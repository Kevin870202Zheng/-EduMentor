#!/bin/bash
cd /home/ubuntu/EduMentor/backend-java
pkill -9 -f java 2>/dev/null
sleep 2

export DEEPSEEK_API_KEY=sk-REPLACED_DEEPSEEK_KEY
export CORS_ALLOWED_ORIGINS=http://82.156.203.220:3000
export ARK_API_KEY=ark-REPLACED_ARK_KEY
export ARK_EMBEDDING_MODEL=ep-REPLACED_MODEL

nohup java -Xmx4g -jar edumentor-backend-1.0.0.jar \
  --spring.flyway.enabled=false \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:postgresql://172.18.0.2:5432/edumentor \
  --spring.datasource.username=edumentor \
  --spring.datasource.password=REPLACED_DB_PASSWORD \
  --jwt.secret-key=edumentor-dev-secret-key-minimum-256-bits-please-replace-in-production \
  --llm.provider=DEEPSEEK \
  --llm.max-tokens=16384 \
  --llm.timeout=600 \
  --rag.vector-engine=true \
  > /tmp/edumentor-backend.log 2>&1 &
echo "PID: $!"
