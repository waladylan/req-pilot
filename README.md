# Requirement -> Flowchart -> Test Cases

Project-based workflow app that converts natural-language requirements into WFM v2, a structured flowchart, and path-based test cases.

## Stack

- Frontend: React, TypeScript, Vite, TanStack Query, TanStack Router, Zustand, Axios, React Flow
- Backend: Java 21, Spring Boot, DTO-based REST APIs, validation, JPA
- WFM Service: Python, FastAPI, Pydantic
- Database: PostgreSQL via Docker Compose

## Project Structure

```txt
frontend/
  src/api
  src/components
  src/pages
  src/types
backend/
  src/main/java/com/reqpilot/controller
  src/main/java/com/reqpilot/service
  src/main/java/com/reqpilot/dto
  src/main/java/com/reqpilot/model
  src/main/java/com/reqpilot/repository
  src/main/java/com/reqpilot/wfm
wfm-service/
  app/main.py
  app/services
  app/schemas
  tests
```

## Project -> Requirements and WFM v2 Architecture

The product model is now `Project -> Requirements`. Each requirement stores its own input text and generated artifacts:

- `requirementText`
- `wfmVersion`, defaulting to `"2.0"`
- `wfmJson`
- `flowchartJson`
- `metadataJson`
- `testCasesJson`
- `testCaseMetadataJson`
- `testCasesGeneratedAt`
- `testCasesUpdatedAt`

WFM v2 is the generated workflow artifact source of truth. React Flow is only the editable canvas renderer for the stored flowchart JSON.

```txt
Requirement Text
  -> Saved Requirement
  -> Spring Boot public API
  -> Python workflow engine
  -> WFM v2 generation
  -> Python WFM v2 validator / canonicalizer
  -> Python WFM v2 to Flowchart mapper
  -> Spring Boot public API
  -> React Flow canvas
```

Derived outputs are compiled from WFM:

- WFM v2 -> Flowchart-compatible `nodes[]` and `edges[]`
- WFM v2 -> per-requirement test cases
- WFM -> Mermaid

WFM intentionally does not contain UI fields such as `x`, `y`, `position`, `color`, `shape`, handles, selection state, or React Flow metadata. Flowchart/canvas edits are artifact edits and do not automatically rewrite the requirement text.

## Run Locally

Start the full stack:

```sh
export OPENROUTER_API_KEY=your-openrouter-api-key
docker compose up -d --build
```

Docker Compose starts PostgreSQL, Spring Boot, and the Python WFM service. Spring Boot keeps the public frontend API stable and calls the Python service internally at `http://wfm-service:8001`.

## Production Deployment

Production Docker Compose serves the Vite build from Nginx, proxies public `/api` traffic to Spring Boot, keeps the Python workflow engine internal, and persists PostgreSQL data in a Docker volume.

See [docs/deployment.md](docs/deployment.md) for `.env` setup, build/start commands, smoke tests, and troubleshooting.

Run only the backend after code changes:

```sh
docker compose up -d --build backend
```

The supported backend and WFM-service environment variables are:

```txt
REQUIREMENT_ANALYZER_MODE=ai
WFM_SERVICE_BASE_URL=http://wfm-service:8001
WFM_SERVICE_TIMEOUT_MS=70000
LLM_PROVIDER=openrouter
OPENROUTER_API_KEY=your-openrouter-api-key
OPENROUTER_MODEL=deepseek/deepseek-chat
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENAI_API_KEY=your-openai-api-key
OPENAI_MODEL=gpt-4o-mini
OPENAI_BASE_URL=https://api.openai.com/v1
LLM_TIMEOUT_SECONDS=60
LLM_MAX_OUTPUT_TOKENS=4096
LLM_TEMPERATURE=0.2
WFM_PROMPT_VERSION=wfm-v2-python-001

# Legacy Java AI endpoint settings retained for /api/ai/* compatibility:
AI_PROVIDER=openrouter
OPENROUTER_DEFAULT_MODEL=deepseek/deepseek-chat
OPENROUTER_FALLBACK_MODEL_1=qwen/qwen3-32b:nitro
OPENROUTER_FALLBACK_MODEL_2=deepseek/deepseek-chat-v3-0324
OPENROUTER_TIMEOUT_SECONDS=60
OPENROUTER_MAX_COMPLETION_TOKENS=4096
OPENROUTER_TEMPERATURE=0.2
AI_FALLBACK_TO_RULE_BASED=true
AI_CACHE_ENABLED=true
AI_CACHE_TTL_DAYS=30
AI_PROMPT_VERSION=requirement-to-wfm-v1
```

For the main requirement-to-flow pipeline, Spring Boot calls the Python workflow engine at `/internal/workflow/generate` with `options.wfmVersion = "2.0"`. Python owns WFM v2 generation and WFM-to-flowchart mapping, then returns `{ wfm, flowchart, metadata }`. Spring Boot persists those artifacts on the selected requirement and returns them to the frontend.

The recommended OpenRouter model is `deepseek/deepseek-chat`. Avoid `:free` models for production or demos because they are heavily rate-limited. Add credits to your OpenRouter account before using paid models.

Run the Python service directly during development:

```sh
cd wfm-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
LLM_PROVIDER=openrouter OPENROUTER_API_KEY=your-openrouter-api-key uvicorn app.main:app --host 0.0.0.0 --port 8001
```

## Public Project and Requirement API

The frontend workspace uses project and saved requirement endpoints:

```txt
GET    /api/projects
POST   /api/projects
GET    /api/projects/{projectId}
PUT    /api/projects/{projectId}
DELETE /api/projects/{projectId}

GET    /api/projects/{projectId}/requirements
POST   /api/projects/{projectId}/requirements
GET    /api/requirements/{requirementId}
PUT    /api/requirements/{requirementId}
DELETE /api/requirements/{requirementId}
POST   /api/requirements/{requirementId}/generate-flow
POST   /api/requirements/{requirementId}/generate-test-cases
GET    /api/requirements/{requirementId}/test-cases
```

Saved requirement generation loads `requirement.requirementText`, calls the Python workflow engine with WFM v2 options, stores `wfmJson`, `flowchartJson`, and `metadataJson`, then returns:

```txt
POST /api/requirements/{requirementId}/generate-flow
```

```json
{
  "requirement": {
    "id": "...",
    "projectId": "...",
    "title": "Purchase Approval",
    "requirementText": "User can create a purchase request...",
    "status": "GENERATED",
    "wfmVersion": "2.0"
  },
  "wfm": {
    "wfmVersion": "2.0"
  },
  "flowchart": {
    "nodes": [],
    "edges": [],
    "mermaid": "flowchart LR\n"
  },
  "metadata": {
    "generationMode": "AI",
    "wfmSource": "python-wfm-v2-ai-generator",
    "flowchartSource": "python-wfm-v2-flowchart-mapper"
  }
}
```

The legacy standalone endpoint remains available temporarily:

```txt
POST /api/requirements/generate-flow
```

It accepts direct requirement text and returns `{ wfm, flowchart, metadata }` without saving to a project. The older React Flow endpoint is also retained for compatibility:

```txt
POST /api/flowcharts/generate
```

Optional debug data can still be requested on `/api/flowcharts/generate` for backend diagnostics:

```json
{
  "requirement": "User can create a purchase request.",
  "options": {
    "includeDebug": true,
    "includeRequirementAnalysis": true,
    "includeWfm": true
  }
}
```

The `/api/ai/*` endpoints are internal/debug endpoints. The frontend must not call them. AI provider details and API keys stay backend-only.

Saved requirement test-case generation uses:

```txt
POST /api/requirements/{requirementId}/generate-test-cases
GET  /api/requirements/{requirementId}/test-cases
```

The generate endpoint loads the selected requirement's persisted WFM v2 artifact, calls the Python workflow engine at `/internal/test-cases/generate`, stores the returned `testCaseSet` and metadata on that same requirement, and returns:

```json
{
  "requirement": {
    "id": "...",
    "wfmVersion": "2.0",
    "testCaseSet": {
      "testCaseVersion": "1.0",
      "sourceWfmVersion": "2.0",
      "testCases": []
    }
  },
  "testCaseSet": {
    "testCaseVersion": "1.0",
    "sourceWfmVersion": "2.0",
    "testCases": []
  },
  "metadata": {
    "sourceWfmVersion": "2.0",
    "generator": "python-wfm-v2-test-case-generator",
    "generationStatus": "PASSED"
  }
}
```

Test-case generation does not regenerate or overwrite the requirement text, WFM, or flowchart. If the selected requirement has no WFM v2 artifact yet, generate flow first.

Frontend flowchart generation can take longer than ordinary API calls because the backend may run analysis and WFM generation through the AI provider. The browser client uses a dedicated 180 second timeout for saved requirement generation and the legacy `POST /api/flowcharts/generate` endpoint. Override it with:

```txt
VITE_FLOWCHART_GENERATION_TIMEOUT_MS=180000
```

Requirement analysis endpoint:

```sh
curl -X POST http://localhost:8080/api/ai/analyze-requirement \
  -H "Content-Type: application/json" \
  -d '{
    "requirement": "User can create a purchase request. Manager approves. If amount > 5000, finance approval is required."
  }'
```

WFM Definition generation endpoint:

```sh
curl -X POST http://localhost:8080/api/ai/generate-wfm \
  -H "Content-Type: application/json" \
  -d '{
    "requirementAnalysis": {
      "summary": "User can create a purchase request which requires manager approval. If the amount exceeds 5000, finance approval is also required.",
      "actors": ["User", "Manager", "Finance"],
      "modules": [
        {
          "name": "Purchase Request",
          "description": "Module for creating and approving purchase requests.",
          "screens": ["Purchase Request Creation Screen", "Approval Screen"],
          "businessRules": [
            "Manager approval is required for all purchase requests.",
            "Finance approval is required if the purchase request amount exceeds 5000."
          ],
          "validations": ["Amount must be a positive number."],
          "workflowSteps": [
            "User creates a purchase request.",
            "Manager reviews and approves the request.",
            "If amount > 5000, Finance reviews and approves the request."
          ],
          "edgeCases": ["What happens if the manager rejects the request?"]
        }
      ],
      "assumptions": ["The system supports role-based access control."],
      "openQuestions": ["Is there a time limit for approvals?"],
      "riskLevel": "MEDIUM"
    }
  }'
```

`POST /api/ai/generate-wfm` converts Requirement Analysis JSON into a WFM Definition that can later feed flowcharts, test cases, documentation, and automation rules. This Phase 3 endpoint preserves actors, business rules, validations, assumptions, edge cases, open questions, and risk level. `nodes[].kind` is intentionally string-based, so custom workflow node kinds are accepted without changing Java enum code.

React Flow graph generation endpoint:

```sh
curl -X POST http://localhost:8080/api/ai/generate-flowchart \
  -H "Content-Type: application/json" \
  -d '{
    "wfm": {
      "workflowName": "Purchase Request Approval",
      "version": "1.0",
      "summary": "User creates a purchase request. Manager approval is required.",
      "actors": ["User", "Manager"],
      "nodes": [
        {"id": "start", "kind": "start", "label": "Start", "metadata": {}},
        {"id": "create_purchase_request", "kind": "action", "label": "Create purchase request", "actor": "User", "metadata": {}},
        {"id": "manager_approval", "kind": "approval", "label": "Manager approval", "actor": "Manager", "metadata": {}},
        {"id": "approved", "kind": "end", "label": "Request approved", "metadata": {}},
        {"id": "rejected", "kind": "end", "label": "Request rejected", "metadata": {}}
      ],
      "edges": [
        {"id": "edge_start_create", "from": "start", "to": "create_purchase_request", "metadata": {}},
        {"id": "edge_create_approval", "from": "create_purchase_request", "to": "manager_approval", "metadata": {}},
        {"id": "edge_approved", "from": "manager_approval", "to": "approved", "condition": "Manager approves", "metadata": {}},
        {"id": "edge_rejected", "from": "manager_approval", "to": "rejected", "condition": "Manager rejects", "metadata": {}}
      ],
      "businessRules": [],
      "validations": [],
      "assumptions": [],
      "edgeCases": [],
      "openQuestions": [],
      "riskLevel": "MEDIUM"
    }
  }'
```

`POST /api/ai/generate-flowchart` deterministically converts WFM Definition JSON into React Flow graph JSON for the UI. Phase 4 does not call an LLM. WFM remains the source of truth; React Flow nodes and edges are a rendering target with deterministic initial positions. Mermaid is optional export/compatibility output elsewhere, not the primary Phase 4 response.

Response excerpt:

```json
{
  "workflowName": "Purchase Request Approval",
  "version": "1.0",
  "format": "REACT_FLOW",
  "direction": "LR",
  "nodes": [
    {
      "id": "manager_approval",
      "type": "approval",
      "position": { "x": 0.0, "y": 280.0 },
      "data": {
        "label": "Manager approval",
        "kind": "approval",
        "actor": "Manager",
        "description": null,
        "sourceWfmNodeId": "manager_approval",
        "metadata": {}
      }
    }
  ],
  "edges": [
    {
      "id": "edge_approved",
      "source": "manager_approval",
      "target": "approved",
      "type": "smoothstep",
      "label": "Manager approves",
      "data": {
        "condition": "Manager approves",
        "sourceWfmEdgeId": "edge_approved",
        "metadata": {}
      }
    }
  ],
  "warnings": []
}
```

Test case suite generation endpoint:

```sh
curl -X POST http://localhost:8080/api/ai/generate-test-cases \
  -H "Content-Type: application/json" \
  -d '{
    "wfm": {
      "workflowName": "Purchase Request Approval",
      "version": "1.0",
      "summary": "User creates a purchase request. Manager approval is required.",
      "actors": ["User", "Manager"],
      "nodes": [
        {"id": "start", "kind": "start", "label": "Start", "metadata": {}},
        {"id": "create_purchase_request", "kind": "action", "label": "Create purchase request", "actor": "User", "metadata": {}},
        {"id": "manager_approval", "kind": "approval", "label": "Manager approval", "actor": "Manager", "metadata": {}},
        {"id": "approved", "kind": "end", "label": "Request approved", "metadata": {}},
        {"id": "rejected", "kind": "end", "label": "Request rejected", "metadata": {}}
      ],
      "edges": [
        {"id": "edge_start_create", "from": "start", "to": "create_purchase_request", "metadata": {}},
        {"id": "edge_create_approval", "from": "create_purchase_request", "to": "manager_approval", "metadata": {}},
        {"id": "edge_approved", "from": "manager_approval", "to": "approved", "condition": "Manager approves", "metadata": {}},
        {"id": "edge_rejected", "from": "manager_approval", "to": "rejected", "condition": "Manager rejects", "metadata": {}}
      ],
      "businessRules": [],
      "validations": ["Amount must be a positive number."],
      "assumptions": [],
      "edgeCases": ["What happens if the manager rejects the request?"],
      "openQuestions": ["Can a request be edited after approval?"],
      "riskLevel": "MEDIUM"
    }
  }'
```

`POST /api/ai/generate-test-cases` deterministically converts WFM Definition JSON into a structured manual QA test suite. Phase 5 does not call an LLM, OpenRouter, or `AiProvider`, and it does not read React Flow graph/UI flowchart data. The WFM payload is validated first and remains the source of truth. Test cases include `sourceNodeIds` and `sourceEdgeIds` for traceability back to WFM, and the `coverage` object reports covered and uncovered workflow nodes and edges. Open questions are returned as warnings instead of executable test cases.

Response excerpt:

```json
{
  "suiteName": "Purchase Request Approval Test Suite",
  "version": "1.0",
  "sourceWorkflowName": "Purchase Request Approval",
  "testCases": [
    {
      "id": "TC-001",
      "title": "Happy path - Purchase Request Approval",
      "type": "HAPPY_PATH",
      "priority": "P0",
      "sourceNodeIds": ["start", "create_purchase_request", "manager_approval", "approved"],
      "sourceEdgeIds": ["edge_start_create", "edge_create_approval", "edge_approved"]
    }
  ],
  "coverage": {
    "nodeCount": 5,
    "edgeCount": 4,
    "coveredNodeIds": [],
    "coveredEdgeIds": [],
    "uncoveredNodeIds": [],
    "uncoveredEdgeIds": []
  },
  "warnings": []
}
```

Run the backend:

```sh
cd backend
mvn spring-boot:run
```

Run the frontend:

```sh
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

The frontend opens as a full-screen canvas-first workspace. React Flow is the base layer, while the toolbar, sidebar, inspector, legend, and status bar float above the canvas without resizing it.

## Tests

Backend unit tests cover WFM validation/normalization, the rule-based parser, flowchart mapping, and test case generation:

```sh
cd backend
mvn test
```

Frontend unit tests cover WFM registries, WFM-to-React Flow conversion, canvas-only node positions, graph edits, edge colors, and requirement text sync:

```sh
cd frontend
npm run test
```

Frontend build check:

```sh
cd frontend
npm run build
```

Yarn also works with the same scripts if your registry setup supports it.

## API

### Legacy Generate Flowchart

`POST /api/flowcharts/generate`

```json
{
  "requirement": "User can create a purchase request. Manager approves. If amount > 5000, finance approval is required."
}
```

Response excerpt:

```json
{
  "workflowName": "Purchase Request Approval",
  "format": "REACT_FLOW",
  "flowchart": {
    "workflowName": "Purchase Request Approval",
    "version": "1.0",
    "format": "REACT_FLOW",
    "direction": "LR",
    "nodes": [
      {
        "id": "start",
        "type": "start",
        "position": { "x": 0.0, "y": 0.0 },
        "data": {
          "label": "Start",
          "kind": "start",
          "sourceWfmNodeId": "start"
        }
      }
    ],
    "edges": [
      {
        "id": "edge_start_create_purchase_request",
        "source": "start",
        "target": "create_purchase_request",
        "type": "smoothstep",
        "label": null,
        "data": {
          "condition": null,
          "sourceWfmEdgeId": "edge_start_create_purchase_request"
        }
      }
    ],
    "warnings": []
  },
  "warnings": []
}
```

The backend internally runs:

```txt
Requirement text
  -> RequirementAnalysisService
  -> WfmGenerationService
  -> ReactFlowGenerationService
  -> public React Flow response
```

The public flowchart response does not include Mermaid as the main output. WFM and requirement analysis are backend implementation details unless explicit debug options are requested.

Legacy compatibility endpoint:

`POST /api/requirements/generate-flow`

This endpoint is retained for backward compatibility, but new frontend code should use `POST /api/requirements/{requirementId}/generate-flow`.

Legacy response excerpt:

```json
{
  "wfm": {
    "schemaVersion": "1.0",
    "modelType": "WORKFLOW_AST",
    "workflow": {
      "id": "user-login",
      "title": "User Login",
      "language": "en"
    },
    "ast": {
      "nodes": [
        { "id": "N1", "role": "START", "kind": "START", "title": "Start" },
        { "id": "N2", "role": "INPUT", "kind": "USER_ENTERS_USERNAME_AND_PASSWORD", "title": "User Enters Username And Password" },
        { "id": "N3", "role": "DECISION", "kind": "DECISION", "title": "Login Result" },
        { "id": "N4", "role": "OUTPUT", "kind": "REDIRECT_TO_DASHBOARD", "title": "Redirect To Dashboard" },
        { "id": "N5", "role": "ERROR", "kind": "SHOW_INVALID_USERNAME_OR_PASSWORD", "title": "Show \"Invalid Username Or Password\"" },
        { "id": "N6", "role": "END", "kind": "END", "title": "End" }
      ],
      "transitions": [
        { "id": "T1", "from": "N1", "to": "N2", "semantic": "DEFAULT" },
        { "id": "T2", "from": "N2", "to": "N3", "semantic": "DEFAULT" },
        { "id": "T3", "from": "N3", "to": "N4", "semantic": "YES", "condition": "Credentials Are Valid" },
        { "id": "T4", "from": "N3", "to": "N5", "semantic": "NO", "condition": "Invalid" }
      ]
    }
  },
  "flowchart": {
    "nodes": [
      { "id": "N1", "label": "Start", "type": "START" },
      { "id": "N2", "label": "User Enters Username And Password", "type": "ACTION" },
      { "id": "N3", "label": "Login Result", "type": "DECISION" },
      { "id": "N6", "label": "End", "type": "END" }
    ],
    "edges": [
      { "id": "T1", "source": "N1", "target": "N2", "type": "DEFAULT" },
      { "id": "T2", "source": "N2", "target": "N3", "type": "DEFAULT" },
      { "id": "T3", "source": "N3", "target": "N4", "label": "Credentials Are Valid", "type": "YES" },
      { "id": "T4", "source": "N3", "target": "N5", "label": "Invalid", "type": "NO" }
    ],
    "mermaid": "flowchart LR\n  N1([\"Start\"])\n  ..."
  },
  "metadata": {
    "source": "RULE_BASED",
    "warnings": []
  }
}
```

The frontend stores `wfm` as the workflow source of truth after generation. It renders React Flow from WFM plus canvas view state. `flowchart.nodes[]`, `flowchart.edges[]`, and `mermaid` remain in the response for compatibility and export.

### Generate Saved Requirement Test Cases

`POST /api/requirements/{requirementId}/generate-test-cases`

The backend loads the selected requirement's saved WFM v2 artifact, sends only that WFM to the Python workflow engine, persists the returned test-case set on the same requirement, and returns the updated requirement plus the generated artifact.

Response excerpt:

```json
{
  "testCaseSet": {
    "testCaseVersion": "1.0",
    "sourceWfmVersion": "2.0",
    "workflowId": "purchase-request",
    "workflowName": "Purchase Request Approval",
    "testCases": [
      {
        "id": "TC-001",
        "title": "Purchase Request Approval - Manager rejects",
        "priority": "HIGH",
        "sourcePath": {
          "nodeIds": ["start", "create_purchase_request", "manager_approved", "request_rejected"],
          "transitionIds": ["t_start_create", "t_create_manager", "t_manager_rejected"]
        },
        "preconditions": ["Workflow data is available."],
        "steps": [],
        "expectedResult": "The workflow reaches Request Rejected."
      }
    ],
    "coverage": {
      "nodeCount": 8,
      "transitionCount": 8,
      "coveredNodeIds": [],
      "coveredTransitionIds": []
    }
  },
  "metadata": {
    "generator": "python-wfm-v2-test-case-generator",
    "generationStatus": "PASSED"
  }
}
```

`GET /api/requirements/{requirementId}/test-cases` returns the saved test-case artifact without regenerating it. The legacy standalone endpoint `POST /api/requirements/generate-testcases` is retained only for older clients.

## Sample Requirements

The frontend includes a Sample Requirements dropdown. The default sample is Login Flow because it exercises all core node types and every edge semantic color.

```txt
Feature: User Login

Start
1. User opens the Login screen.
2. User enters username and password.
3. System validates the input.

If required information is missing:
- Show validation error.
- End.

If credentials are valid:
- Authenticate user.
- Generate access token.
- Redirect to Dashboard.
- End.

If credentials are invalid:
- Show "Invalid username or password".
- Allow user to retry.

If retry:
- Return to Login screen.

If user cancels login:
- Return to Home page.
- End.

If authentication succeeds:
- Show login success message.
- End.

If system cannot connect to authentication server:
- Show system error.
- End.
```

Additional samples:

- Registration Flow
- Checkout Flow
- Delete Product
- Password Reset
- Approval Process

## AI / Workflow Engine Integration

The main requirement-to-flow path is designed around the Python workflow engine.

- `RuleBasedRequirementAnalyzer` remains available for local deterministic generation.
- Spring Boot calls the Python workflow engine and expects `{ wfm, flowchart, metadata }` back.
- The Python workflow engine owns prompt construction, provider calls, JSON extraction, lightweight WFM validation, and deterministic WFM-to-flowchart mapping.

AI integrations must generate WFM JSON only. They should not generate React Flow nodes, coordinates, colors, handles, Mermaid, or other UI data. WFM v2 is the default workflow AST for the Python workflow engine; WFM v1 remains available by setting `WFM_DEFAULT_VERSION=1.0` or sending `options.wfmVersion = "1.0"` to `/internal/workflow/generate`. The Python workflow engine maps validated WFM into the compatibility flowchart DTO; Spring Boot preserves the public API contract for the frontend.

WFM v2 can be requested explicitly:

```json
{
  "requirement": "User can create a purchase request. Manager approves. If amount > 5000, finance approval is required.",
  "options": {
    "wfmVersion": "2.0",
    "generationMode": "MOCK"
  }
}
```
