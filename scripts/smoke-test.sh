#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BASE_URL="${BASE_URL:-http://localhost:${FRONTEND_PORT:-80}}"

echo "Checking frontend at $BASE_URL ..."
curl -fsS "$BASE_URL/" >/dev/null

echo "Checking backend health through Nginx ..."
curl -fsS "$BASE_URL/api/actuator/health" | grep '"status":"UP"' >/dev/null

echo "Checking workflow-engine health inside Docker network ..."
docker compose -f "$COMPOSE_FILE" exec -T workflow-engine python -c "import json, urllib.request; data=json.load(urllib.request.urlopen('http://localhost:8001/health', timeout=5)); assert data['status'] == 'UP'"

echo "Checking Compose service status ..."
docker compose -f "$COMPOSE_FILE" ps

echo "Smoke test passed."
