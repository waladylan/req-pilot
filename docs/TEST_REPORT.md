# Full App Test Report

## Date

2026-07-04

## Scope

This audit covered the current full-stack WFM pipeline:

- Requirement analysis API
- WFM generation API
- React Flow generation API
- Test case generation API
- Frontend React Flow rendering path
- Backend unit/controller/service tests
- Frontend unit tests, lint, and production build
- Docker backend smoke tests for deterministic non-AI endpoints

Real paid AI smoke tests were not executed. In this shell:

- `OPENROUTER_API_KEY_PRESENT=false`
- `RUN_REAL_AI_SMOKE_TEST=false`

That matches the requested guardrail: do not call paid AI unless a key is configured and `RUN_REAL_AI_SMOKE_TEST=true`.

## Current Pipeline Status

| Step | Status | Notes |
| --- | --- | --- |
| Requirement Analysis | PASS/PARTIAL | Controller tests cover valid and blank input. Real AI smoke skipped by env guard. |
| WFM Generation | PASS/PARTIAL | Controller/service tests cover valid and invalid input. Real AI smoke skipped by env guard. |
| React Flow Generation | PASS | Deterministic service tests and Docker HTTP smoke passed. Output format is `REACT_FLOW`; Mermaid is not the main output. |
| UI React Flow Rendering | PASS/PARTIAL | Source/build/tests confirm React Flow is the main renderer. Manual browser click-through remains listed below. |
| Test Case Generation | PASS | Deterministic service/controller tests and Docker HTTP smoke passed. Generation is WFM-based. |

## Verification Summary

| Area | Status | Notes |
| --- | --- | --- |
| Backend unit tests | PASS | `mvn test` passed with 102 tests. |
| Backend package | PASS | `mvn package -DskipTests` passed. |
| Frontend unit tests | PASS | Vitest passed with 25 tests. |
| Frontend lint | PASS | ESLint completed with no output/errors. |
| Frontend typecheck | PASS | TypeScript project build completed with `tsc -b`. |
| Frontend build | PASS | Vite build passed. Existing large chunk warning remains. |
| `/api/ai/analyze-requirement` | PASS in controller tests | Real AI smoke skipped by env guard. |
| `/api/ai/generate-wfm` | PASS in controller/service tests | Real AI smoke skipped by env guard. |
| `/api/ai/generate-flowchart` | PASS | Unit/controller tests and Docker HTTP smoke passed. |
| `/api/ai/generate-test-cases` | PASS | Unit/controller tests and Docker HTTP smoke passed. |
| Main UI renderer | PASS by source/build/tests | Main canvas uses React Flow. Mermaid remains only for export/derived output. |

## Commands Run

Backend:

```bash
cd /Users/duongminhduy/Desktop/lab/req-pilot/backend
mvn test
mvn package -DskipTests
```

Results:

- `mvn test`: PASS, 102 tests, 0 failures, 0 errors
- `mvn package -DskipTests`: PASS

Frontend, using the bundled Node runtime:

```bash
cd /Users/duongminhduy/Desktop/lab/req-pilot/frontend
/Users/duongminhduy/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node ./node_modules/vitest/vitest.mjs run
/Users/duongminhduy/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node ./node_modules/eslint/bin/eslint.js .
/Users/duongminhduy/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node ./node_modules/typescript/bin/tsc -b
/Users/duongminhduy/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node ./node_modules/vite/bin/vite.js build
```

Results:

- Vitest: PASS, 2 files, 25 tests
- ESLint: PASS
- TypeScript project build: PASS
- Vite build: PASS
- Vite warning: generated JS chunk is larger than 500 kB after minification

Docker:

```bash
cd /Users/duongminhduy/Desktop/lab/req-pilot
docker compose ps
```

Result:

- `req-pilot-backend`: up on `localhost:8081`
- `req-pilot-postgres`: healthy on `localhost:5432`

## Docker HTTP Smoke

The deterministic smoke test used a purchase request WFM fixture and did not call any external AI provider.

Actual result:

```json
{
  "flowchart": {
    "status": 200,
    "format": "REACT_FLOW",
    "nodes": 7,
    "edges": 7,
    "firstNode": "start",
    "hasMermaid": false
  },
  "testCases": {
    "status": 200,
    "suiteName": "Purchase Request Approval Test Suite",
    "count": 7,
    "warnings": 0,
    "coverage": 7
  }
}
```

## Endpoint Coverage

### Requirement Analysis

Endpoint:

```text
POST /api/ai/analyze-requirement
```

Covered by `AiControllerTest` with:

- Valid purchase request requirement
- Blank requirement validation

Real provider smoke is intentionally skipped unless both of these are true:

- `OPENROUTER_API_KEY` is configured
- `RUN_REAL_AI_SMOKE_TEST=true`

Manual AI smoke command:

```bash
curl -sS -X POST http://localhost:8081/api/ai/analyze-requirement \
  -H 'Content-Type: application/json' \
  -d '{"requirement":"User creates a purchase request. Manager approval is required before the request is approved."}'
```

### WFM Generation

Endpoint:

```text
POST /api/ai/generate-wfm
```

Covered by controller/service tests with:

- Valid requirement analysis input
- Missing input validation
- AI JSON validation

Manual AI smoke command:

```bash
curl -sS -X POST http://localhost:8081/api/ai/generate-wfm \
  -H 'Content-Type: application/json' \
  -d '{
    "requirementAnalysis": {
      "summary": "User creates a purchase request. Manager approval is required.",
      "actors": ["Requester", "Manager"],
      "modules": [],
      "assumptions": [],
      "openQuestions": [],
      "riskLevel": "MEDIUM"
    }
  }'
```

### React Flow Generation

Endpoint:

```text
POST /api/ai/generate-flowchart
```

Covered by:

- `ReactFlowGenerationServiceTest`
- `AiControllerTest`
- Docker HTTP smoke

Manual deterministic smoke command:

```bash
curl -sS -X POST http://localhost:8081/api/ai/generate-flowchart \
  -H 'Content-Type: application/json' \
  -d '{
    "wfm": {
      "workflowName": "Purchase Request Approval",
      "version": "1.0",
      "summary": "User creates a purchase request and manager approves or rejects it.",
      "actors": ["Requester", "Manager"],
      "nodes": [
        {"id":"start","kind":"start","label":"Start","metadata":{}},
        {"id":"create_request","kind":"input","label":"Requester enters purchase request","actor":"Requester","metadata":{}},
        {"id":"validate_request","kind":"decision","label":"Is request valid?","metadata":{}},
        {"id":"submit_request","kind":"action","label":"Submit request","actor":"Requester","metadata":{}},
        {"id":"manager_approval","kind":"approval","label":"Manager approval","actor":"Manager","metadata":{}},
        {"id":"approved","kind":"end","label":"Request approved","metadata":{}},
        {"id":"rejected","kind":"end","label":"Request rejected","metadata":{}}
      ],
      "edges": [
        {"id":"edge_start_create","from":"start","to":"create_request","metadata":{}},
        {"id":"edge_create_validate","from":"create_request","to":"validate_request","metadata":{}},
        {"id":"edge_validate_yes","from":"validate_request","to":"submit_request","condition":"Request is valid","metadata":{}},
        {"id":"edge_validate_no","from":"validate_request","to":"rejected","condition":"Request is invalid","metadata":{}},
        {"id":"edge_submit_approval","from":"submit_request","to":"manager_approval","metadata":{}},
        {"id":"edge_approval_yes","from":"manager_approval","to":"approved","condition":"Manager approves","metadata":{}},
        {"id":"edge_approval_no","from":"manager_approval","to":"rejected","condition":"Manager rejects","metadata":{}}
      ],
      "businessRules": ["Manager approval is required."],
      "validations": ["Purchase amount must be positive."],
      "assumptions": [],
      "edgeCases": ["Invalid request data."],
      "openQuestions": [],
      "riskLevel": "MEDIUM"
    }
  }'
```

### Test Case Generation

Endpoint:

```text
POST /api/ai/generate-test-cases
```

Covered by:

- `TestCaseGenerationServiceTest`
- `AiControllerTest`
- Docker HTTP smoke

Manual deterministic smoke command:

```bash
curl -sS -X POST http://localhost:8081/api/ai/generate-test-cases \
  -H 'Content-Type: application/json' \
  -d '{
    "wfm": {
      "workflowName": "Purchase Request Approval",
      "version": "1.0",
      "summary": "User creates a purchase request and manager approves or rejects it.",
      "actors": ["Requester", "Manager"],
      "nodes": [
        {"id":"start","kind":"start","label":"Start","metadata":{}},
        {"id":"create_request","kind":"input","label":"Requester enters purchase request","actor":"Requester","metadata":{}},
        {"id":"validate_request","kind":"decision","label":"Is request valid?","metadata":{}},
        {"id":"submit_request","kind":"action","label":"Submit request","actor":"Requester","metadata":{}},
        {"id":"manager_approval","kind":"approval","label":"Manager approval","actor":"Manager","metadata":{}},
        {"id":"approved","kind":"end","label":"Request approved","metadata":{}},
        {"id":"rejected","kind":"end","label":"Request rejected","metadata":{}}
      ],
      "edges": [
        {"id":"edge_start_create","from":"start","to":"create_request","metadata":{}},
        {"id":"edge_create_validate","from":"create_request","to":"validate_request","metadata":{}},
        {"id":"edge_validate_yes","from":"validate_request","to":"submit_request","condition":"Request is valid","metadata":{}},
        {"id":"edge_validate_no","from":"validate_request","to":"rejected","condition":"Request is invalid","metadata":{}},
        {"id":"edge_submit_approval","from":"submit_request","to":"manager_approval","metadata":{}},
        {"id":"edge_approval_yes","from":"manager_approval","to":"approved","condition":"Manager approves","metadata":{}},
        {"id":"edge_approval_no","from":"manager_approval","to":"rejected","condition":"Manager rejects","metadata":{}}
      ],
      "businessRules": ["Manager approval is required."],
      "validations": ["Purchase amount must be positive."],
      "assumptions": [],
      "edgeCases": ["Invalid request data."],
      "openQuestions": [],
      "riskLevel": "MEDIUM"
    }
  }'
```

## Frontend Rendering Check

Source inspection confirms the main canvas uses React Flow:

- `frontend/src/components/canvas/FlowCanvas.tsx`
- `frontend/src/components/flowchart/FlowchartPreview.tsx`
- `frontend/src/helpers/flowchart.ts`
- `frontend/src/helpers/wfm-to-react-flow.ts`

Mermaid references remain for:

- Export Mermaid action
- Derived Mermaid builders
- Optional/deprecated `mermaid` DTO field compatibility
- Tests for Mermaid generation

No active main UI path was found that uses Mermaid as the primary visual renderer.

## Findings

### F1 - Missing negative API coverage

Severity: LOW

Files:

- `backend/src/test/java/com/reqpilot/controller/AiControllerTest.java`

Description:

Several controller paths had happy-path tests but lacked invalid-input coverage. This made it easier for validation regressions to pass silently.

Root cause:

Controller tests were focused on successful DTO conversion and did not exercise invalid WFM or missing input requests.

Fix applied:

Added tests for blank requirement input, missing WFM generation input, invalid React Flow WFM input, and invalid test case WFM input.

### F2 - Missing invalid AI JSON coverage

Severity: LOW

Files:

- `backend/src/test/java/com/reqpilot/ai/AiJsonValidatorTest.java`

Description:

The AI JSON validator needed explicit coverage for non-JSON provider responses.

Root cause:

Existing tests covered valid JSON and fenced JSON but not malformed provider output.

Fix applied:

Added a regression test proving invalid provider text is rejected with an `AiProviderException`.

### F3 - React Flow generation should remain deterministic and non-AI

Severity: LOW

Files:

- `backend/src/test/java/com/reqpilot/service/ReactFlowGenerationServiceTest.java`

Description:

React Flow generation should consume WFM only and must not depend on the AI provider.

Root cause:

The architecture rule was not directly protected by a test.

Fix applied:

Added a reflection-based regression test that fails if `ReactFlowGenerationService` declares an `AiProvider` dependency.

### F4 - Frontend bundle size warning

Severity: LOW

Files:

- `frontend/package.json`
- `frontend/src/**`

Description:

The frontend production build passes, but Vite reports a chunk larger than 500 kB.

Root cause:

The React Flow/editor bundle is currently shipped as part of the main application bundle.

Recommended fix:

Consider dynamic imports or route-level code splitting for the canvas/editor path. This does not block correctness.

## Fixes Applied

No production behavior changes were required during this audit pass. The main low-risk gap was missing test coverage around API validation and accidental AI coupling.

Changed files:

- `backend/src/test/java/com/reqpilot/ai/AiJsonValidatorTest.java`
  - Verifies invalid AI JSON is rejected.
- `backend/src/test/java/com/reqpilot/controller/AiControllerTest.java`
  - Adds requirement analysis endpoint coverage.
  - Adds invalid input coverage for WFM, React Flow, and test case endpoints.
- `backend/src/test/java/com/reqpilot/service/ReactFlowGenerationServiceTest.java`
  - Verifies React Flow generation service does not depend on `AiProvider`.
- `docs/TEST_REPORT.md`
  - Documents audit scope, commands, endpoint coverage, findings, and manual checks.

## Tests Added

- `AiJsonValidatorTest.rejectsInvalidJson`
  - Covers invalid/non-JSON AI provider output.
- `AiControllerTest.analyzeRequirementReturnsRequirementAnalysis`
  - Covers valid requirement analysis response shape.
- `AiControllerTest.analyzeRequirementRejectsBlankRequirement`
  - Covers blank input validation.
- `AiControllerTest.generateWfmRejectsMissingInput`
  - Covers missing WFM generation input.
- `AiControllerTest.generateFlowchartRejectsInvalidWfm`
  - Covers invalid WFM handling for React Flow generation.
- `AiControllerTest.generateTestCasesRejectsInvalidWfm`
  - Covers invalid WFM handling for test case generation.
- `ReactFlowGenerationServiceTest.doesNotDependOnAiProvider`
  - Covers the deterministic Phase 4 architecture rule.

## Remaining Risks

- Real AI smoke tests were skipped because `OPENROUTER_API_KEY` and `RUN_REAL_AI_SMOKE_TEST` were not set in this shell.
- Manual browser click-through at `http://localhost:5173/` is still recommended after the dev server is running.
- Frontend coverage is primarily helper/unit-level. Full React component interaction coverage can be expanded later if needed.
- Vite reports a large JS chunk after minification. This is a performance warning, not a correctness failure.

## Manual Test Steps

Run these after starting the app:

```bash
cd /Users/duongminhduy/Desktop/lab/req-pilot
docker compose up -d
```

Then open:

```text
http://localhost:5173/
```

Manual UI acceptance:

- Select the default sample requirement.
- Click `Generate Flow`.
- Confirm nodes render on the React Flow canvas.
- Drag at least one node and confirm the app remains responsive.
- Edit a node label and confirm the requirement text syncs.
- Click `Generate Test Cases`.
- Confirm test cases render in the table.
- Export Mermaid and CSV.

## Notes

- The frontend currently calls the legacy compatibility endpoints under `/api/requirements/*`.
- The phase-specific `/api/ai/*` endpoints are available and covered separately.
- The legacy endpoints should remain compatible because the UI depends on them.
- Real AI smoke tests should only be run intentionally with credits available on the provider account.
