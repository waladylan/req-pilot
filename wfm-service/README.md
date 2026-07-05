# Workflow Engine Service

FastAPI service responsible for generating WFM JSON from natural language requirements and mapping WFM to the backward-compatible flowchart DTO.

The service is internal to Req Pilot. Spring Boot calls it and keeps the public frontend API stable. The primary endpoint returns `{ wfm, flowchart, metadata }`; the older WFM-only endpoint is retained for WFM v1 compatibility.

## Run Locally

```bash
cd wfm-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

## Environment

OpenRouter, matching the existing backend default:

```bash
export LLM_PROVIDER=openrouter
export OPENROUTER_API_KEY=your_key
export OPENROUTER_MODEL=deepseek/deepseek-chat
export OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
```

OpenAI-compatible configuration:

```bash
export LLM_PROVIDER=openai
export OPENAI_API_KEY=your_key
export OPENAI_MODEL=gpt-4o-mini
export OPENAI_BASE_URL=https://api.openai.com/v1
```

Mock mode for local smoke tests and automated checks without calling a real LLM:

```bash
export LLM_PROVIDER=mock
```

Mock mode returns deterministic WFM JSON. Through `/internal/workflow/generate`, that WFM is also mapped to deterministic flowchart output without calling a real LLM.

Optional:

```bash
export LLM_TIMEOUT_SECONDS=60
export LLM_MAX_OUTPUT_TOKENS=4096
export LLM_TEMPERATURE=0.2
export WFM_PROMPT_VERSION=wfm-v1-python-001
export WFM_DEFAULT_VERSION=2.0
export WFM_SERVICE_PORT=8001
```

## API

`POST /internal/workflow/generate`

Request:

```json
{
  "requirement": "User can create a purchase request. Manager approves.",
  "context": {
    "projectId": "optional",
    "language": "en",
    "domain": "procurement"
  },
  "options": {
    "generationMode": "AI",
    "wfmVersion": "2.0",
    "model": "optional-model",
    "temperature": 0.2
  }
}
```

Response:

```json
{
  "wfm": {
    "wfmVersion": "2.0",
    "workflowId": "purchase_request_approval",
    "workflowName": "Purchase Request Approval",
    "direction": "LR",
    "nodes": [],
    "transitions": [],
    "metadata": {}
  },
  "flowchart": {
    "nodes": [],
    "edges": [],
    "mermaid": "flowchart LR\n"
  },
  "metadata": {
    "generationMode": "AI",
    "wfmVersion": "2.0",
    "wfmSource": "python-wfm-v2-ai-generator",
    "flowchartSource": "python-wfm-v2-flowchart-mapper",
    "model": "deepseek/deepseek-chat",
    "promptVersion": "wfm-v2-python-001",
    "canonicalizationStatus": "PASSED",
    "validationStatus": "PASSED",
    "mappingStatus": "PASSED",
    "warnings": []
  }
}
```

`POST /internal/wfm/generate` is retained as a compatibility endpoint and returns WFM v1-only `{ wfm, metadata }`.

To request WFM v1 through the workflow endpoint:

```json
{
  "requirement": "Feature: Login",
  "options": {
    "wfmVersion": "1.0",
    "generationMode": "RULE_BASED"
  }
}
```

To run deterministic WFM v2 without a real LLM:

```bash
curl -sS -X POST http://localhost:8001/internal/workflow/generate \
  -H 'Content-Type: application/json' \
  --data '{
    "requirement": "User can create a purchase request. Manager approves. If amount > 5000, finance approval is required.",
    "options": {
      "wfmVersion": "2.0",
      "generationMode": "MOCK"
    }
  }'
```

Error response:

```json
{
  "error": {
    "code": "WFM_GENERATION_FAILED",
    "message": "Unable to generate WFM from LLM provider",
    "details": {}
  }
}
```

## Test

```bash
cd wfm-service
pytest
```

End-to-end smoke test through Spring Boot without using a real LLM:

```bash
LLM_PROVIDER=mock docker compose up -d --build
curl -sS http://localhost:8081/api/requirements/generate-flow \
  -H 'Content-Type: application/json' \
  --data '{"requirement":"Feature: Leave request\nEmployee submits a leave request. Manager approves. If days > 5, HR approval is required. End."}'
```

The public response should include `wfm`, `flowchart`, and `metadata`.

## Limitations

- WFM v1 remains available, but `/internal/workflow/generate` defaults to WFM v2 unless `WFM_DEFAULT_VERSION=1.0` or `options.wfmVersion = "1.0"` is provided.
- WFM v2 validation is intentionally focused on core workflow semantics and does not attempt full BPMN validation.
- The service does not invent missing business logic during canonicalization.
- Python owns deterministic WFM-to-flowchart mapping for the main requirement-to-flow pipeline.
- Spring Boot remains responsible for preserving the public API and for downstream test case generation.
