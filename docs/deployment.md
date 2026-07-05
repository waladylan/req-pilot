# Production Deployment

This deployment profile runs Req Pilot as four containers:

```text
Browser
  -> frontend Nginx on :80
      -> /api/* proxied to backend:8080
  -> Spring Boot backend
      -> PostgreSQL on postgres:5432
      -> Python workflow-engine on workflow-engine:8001
```

The Python workflow-engine is internal only. Do not publish its `/internal/*` endpoints through Nginx.

## Prerequisites

- Docker
- Docker Compose v2
- A server with enough memory for the Java backend, Python workflow-engine, PostgreSQL, and frontend Nginx
- An LLM provider key if `WFM_GENERATION_MODE=AI`

## Environment

Create a deployment env file:

```sh
cp .env.example .env
```

Fill in at least:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `LLM_PROVIDER`
- `OPENROUTER_API_KEY` or `OPENAI_API_KEY`
- `OPENROUTER_MODEL` or `OPENAI_MODEL`

Do not put secrets into frontend env vars. The frontend should call the same-origin `/api` path.

## Build And Start

```sh
scripts/deploy-prod.sh
```

Equivalent manual commands:

```sh
docker compose -f docker-compose.prod.yml config
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

Open:

```text
http://localhost
```

Backend health through Nginx:

```text
http://localhost/api/actuator/health
```

## Stop

```sh
scripts/stop-prod.sh
```

To remove persisted PostgreSQL data, remove the `postgres_data` volume explicitly after confirming the data is no longer needed.

## Smoke Test

```sh
scripts/smoke-test.sh
```

The smoke test checks:

- frontend responds
- backend health is `UP` through `/api/actuator/health`
- workflow-engine health is `UP` from inside the Docker network
- Compose services are running

It does not call a real LLM.

## Database Persistence And Migrations

The MVP currently uses Hibernate `ddl-auto=update`. This creates or updates:

- `projects`
- `requirements`
- WFM JSON fields
- flowchart JSON fields
- metadata JSON fields
- test case JSON fields

This is acceptable for MVP deployment but is not a substitute for versioned migrations. Before production hardening, replace this with Flyway or Liquibase and set:

```text
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

PostgreSQL data is persisted in the `postgres_data` Docker volume.

## Update Deployment

```sh
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
scripts/smoke-test.sh
```

If frontend assets look stale after deploy, clear browser cache or verify that the new frontend image was rebuilt.

## Logs

```sh
docker compose -f docker-compose.prod.yml logs -f --tail=100 frontend
docker compose -f docker-compose.prod.yml logs -f --tail=100 backend
docker compose -f docker-compose.prod.yml logs -f --tail=100 workflow-engine
docker compose -f docker-compose.prod.yml logs -f --tail=100 postgres
```

Logs should not include API keys. Do not enable full prompt/request-body logging in production.

## Troubleshooting

Backend cannot reach workflow-engine:

- Confirm `WORKFLOW_ENGINE_BASE_URL=http://workflow-engine:8001`.
- Confirm `workflow-engine` is healthy: `docker compose -f docker-compose.prod.yml ps`.
- Do not use `localhost` for service-to-service calls inside Docker.

Frontend calls localhost instead of `/api`:

- Confirm `VITE_API_BASE_URL` is unset or empty for the production frontend build.
- Confirm Nginx proxies `/api/` to `backend:8080`.

Database connection refused:

- Confirm `postgres` is healthy.
- Confirm `SPRING_DATASOURCE_URL` points to `jdbc:postgresql://postgres:5432/...`.
- Confirm database credentials in `.env` match the Postgres service environment.

LLM key missing:

- Configure the key on `workflow-engine`, not the frontend.
- For OpenRouter, set `OPENROUTER_API_KEY`.
- For OpenAI, set `OPENAI_API_KEY` and `LLM_PROVIDER=openai`.

Python internal endpoint exposed accidentally:

- Production Nginx returns `404` for `/internal/*`.
- `workflow-engine` has no published host port in `docker-compose.prod.yml`.

Node or edge labels missing after deployment:

- Rebuild the frontend image.
- Clear browser cache.
- Confirm the backend response still includes `{ wfm, flowchart, metadata }`.

## Manual Product Verification

1. Open `http://localhost`.
2. Create a project.
3. Create a requirement.
4. Generate flow.
5. Confirm the response metadata shows WFM v2 and includes `wfm`, `flowchart`, and `metadata`.
6. Generate test cases.
7. Refresh the browser.
8. Confirm the project, requirement, WFM, flowchart, and test cases are still present.
