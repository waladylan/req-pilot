#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"

if [ ! -f ".env" ]; then
  echo "Missing .env. Create one from .env.example before deploying."
  exit 1
fi

docker compose -f "$COMPOSE_FILE" config >/dev/null
docker compose -f "$COMPOSE_FILE" build
docker compose -f "$COMPOSE_FILE" up -d
docker compose -f "$COMPOSE_FILE" ps

echo ""
echo "Frontend: http://localhost:${FRONTEND_PORT:-80}"
echo "Backend health through Nginx: http://localhost:${FRONTEND_PORT:-80}/api/actuator/health"
echo "Logs: docker compose -f $COMPOSE_FILE logs -f --tail=100"
