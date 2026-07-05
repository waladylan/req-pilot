# AI MVP Implementation Plan

## 1. Purpose

This document defines the practical implementation plan for the AI MVP of the project:

```text
Requirement Text → WFM v1 → Flowchart / Test Cases / Mermaid / Future Exporters
```

The goal of this MVP is to add AI-based requirement analysis in a safe, incremental, and testable way.

The MVP target is:

```text
Rule-based Analyzer + Gemini Flash + Requirement WFM Cache
```

AI must only generate **WFM v1 JSON**.

AI must not generate:

- React Flow
- Mermaid
- Layout
- Position
- Color
- Shape
- Test cases
- BPMN
- PlantUML
- UI state

---

## 2. Current Assumptions

- Backend already supports WFM v1.
- `POST /api/requirements/generate-flow` returns or should return:

```json
{
  "wfm": {},
  "flowchart": {},
  "metadata": {}
}
```

- `flowchart` must remain backward-compatible.
- WFM is the source of truth.
- React Flow is only a renderer/canvas view.
- Test cases must be generated from WFM, not from React Flow.
- AI has not been implemented yet.
- Default analyzer mode must remain rule-based.

---

## 3. Non-Goals

Do not implement these in this MVP:

- DeepSeek provider
- OpenRouter provider
- Ollama/local provider
- AI repair loop
- Prompt A/B testing
- Confidence scoring
- Human approval workflow
- AI-generated test cases
- AI-generated React Flow
- AI-generated Mermaid
- BPMN export
- PlantUML export
- Automation test generation
- Full frontend rewrite

---

## 4. MVP Scope

The MVP should include:

- `RequirementAnalyzer` abstraction if not already cleanly defined
- `RuleBasedRequirementAnalyzer` as default
- `AiRequirementAnalyzer`
- `AiProvider` abstraction
- `GeminiProvider`
- `FakeAiProvider` or test stub provider
- `RequirementToWfmPromptBuilder`
- Prompt versioning
- `AiWfmResponseParser`
- WFM validation after AI output
- WFM normalization after validation
- Fallback to rule-based analyzer
- Requirement WFM cache
- API metadata
- Unit tests
- Integration tests

---

## 5. Recommended Implementation Order

Implement in this order to keep the change safe and testable.

### Step 1: Interfaces and configuration

Add or clean up:

```text
RequirementAnalyzer
AiProvider
AiGenerationRequest
AiGenerationResponse
AnalyzerMode
AiProviderType
```

Add configuration:

```properties
requirement.analyzer.mode=rule_based
ai.provider=gemini
ai.model=gemini-2.5-flash-lite
ai.api-key=${GEMINI_API_KEY}
ai.timeout-ms=15000
ai.max-output-tokens=8192
ai.temperature=0.1
ai.fallback-to-rule-based=true
ai.cache.enabled=true
ai.cache.ttl-days=30
ai.prompt-version=requirement-to-wfm-v1
```

Default must remain:

```properties
requirement.analyzer.mode=rule_based
```

Acceptance criteria:

- App behavior does not change when mode is `rule_based`.
- Business services depend on `RequirementAnalyzer`, not provider-specific classes.
- No real AI call is required yet.

---

### Step 2: Prompt builder

Implement:

```text
RequirementToWfmPromptBuilder
```

The prompt must include:

- WFM v1 structure
- Core node roles
- Core transition semantics
- Forbidden UI fields
- ID rules
- Requirement text
- JSON-only instruction
- Prompt version

Prompt must explicitly say:

```text
Return only valid JSON.
Do not return Markdown.
Do not generate React Flow.
Do not generate Mermaid.
Do not generate layout.
Do not include UI fields.
```

Acceptance criteria:

- Prompt builder is deterministic.
- Prompt builder has unit tests.
- Prompt includes all forbidden UI fields.
- Prompt includes WFM role/kind guidance.

---

### Step 3: AI response parser

Implement:

```text
AiWfmResponseParser
```

Parser responsibility:

- Reject empty response
- Reject invalid JSON
- Reject JSON without WFM root fields
- Extract JSON if provider accidentally wraps it in code fences
- Parse into WFM DTO/model
- Never trust raw AI text directly

Acceptance criteria:

- Invalid JSON does not crash the application unexpectedly.
- Markdown-only response is rejected.
- JSON with missing WFM fields is rejected.
- Valid WFM JSON is parsed successfully.

---

### Step 4: AiRequirementAnalyzer orchestration

Implement:

```text
AiRequirementAnalyzer
```

Expected pipeline:

```text
Requirement Text
→ RequirementWfmCache lookup
→ PromptBuilder
→ AiProvider
→ AiWfmResponseParser
→ WfmValidator
→ WfmNormalizer
→ Save WFM to cache
→ WFM Final
```

Important rules:

- AI output must pass validation before use.
- WFM must be normalized before returning.
- Do not return raw AI WFM directly.
- Do not mutate validated WFM into React Flow.
- Do not add UI fields to WFM.

Acceptance criteria:

- Analyzer returns WFM final, not raw AI output.
- Validator is always called.
- Normalizer is always called after validation.
- Cache hit bypasses provider call.
- Cache miss calls provider.

---

### Step 5: Requirement WFM cache

Implement cache after the parser/analyzer skeleton is stable.

Recommended MVP storage:

```text
PostgreSQL table
```

Do not use only in-memory cache for MVP, because the system is shared by multiple leaders/projects and should survive server restarts.

Suggested cache key:

```text
SHA256(normalizedRequirementText + analyzerMode + provider + model + promptVersion)
```

Cache value should include:

```json
{
  "wfm": {},
  "metadata": {
    "analyzer": "AI",
    "provider": "GEMINI",
    "model": "gemini-2.5-flash-lite",
    "promptVersion": "requirement-to-wfm-v1",
    "createdAt": "..."
  }
}
```

Cache policy:

- Cache only validated + normalized WFM.
- Do not use raw AI response as source of truth.
- Cache miss when requirement text changes.
- Cache miss when provider changes.
- Cache miss when model changes.
- Cache miss when prompt version changes.
- Cache miss when analyzer mode changes.
- TTL default: 30 days.

Suggested table:

```sql
CREATE TABLE requirement_wfm_cache (
    id UUID PRIMARY KEY,
    cache_key VARCHAR(128) NOT NULL UNIQUE,
    normalized_requirement_hash VARCHAR(128) NOT NULL,
    analyzer_mode VARCHAR(32) NOT NULL,
    provider VARCHAR(32),
    model VARCHAR(128),
    prompt_version VARCHAR(128) NOT NULL,
    wfm_json JSONB NOT NULL,
    metadata_json JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP
);
```

Acceptance criteria:

- Cache hit returns cached validated WFM.
- Cache miss calls provider.
- Cache hit metadata includes `cacheHit = true`.
- Cache miss metadata includes `cacheHit = false`.
- Expired cache is ignored.
- Cache key is deterministic.

---

### Step 6: Fallback to rule-based analyzer

Fallback rules:

```text
AI timeout → rule-based fallback
AI provider error → rule-based fallback
AI empty response → rule-based fallback
AI invalid JSON → rule-based fallback
AI invalid WFM → rule-based fallback
```

Only fallback when configured:

```properties
ai.fallback-to-rule-based=true
```

Metadata should indicate fallback:

```json
{
  "metadata": {
    "analyzer": "AI",
    "provider": "GEMINI",
    "model": "gemini-2.5-flash-lite",
    "fallbackUsed": true,
    "fallbackReason": "AI_INVALID_WFM"
  }
}
```

Acceptance criteria:

- Fallback does not remove `wfm`.
- Fallback does not remove `flowchart`.
- Fallback reason is visible in metadata.
- Raw provider errors are not exposed to client.

---

### Step 7: GeminiProvider

Implement Gemini only after fake provider tests pass.

Provider responsibility:

- Build provider request
- Send prompt
- Apply timeout
- Return text response
- Map provider errors to controlled app errors
- Never expose API key
- Avoid logging full requirement text or raw AI response in production

Acceptance criteria:

- GeminiProvider is behind `AiProvider`.
- Business services do not import Gemini-specific classes.
- API key comes from environment/config.
- Timeout is configured.
- Provider errors are sanitized.

---

### Step 8: API integration

Update:

```text
POST /api/requirements/generate-flow
```

Expected flow:

```text
POST /api/requirements/generate-flow
        │
        ▼
Read analyzer mode
        │
        ├── RULE_BASED
        │       ▼
        │   RuleBasedRequirementAnalyzer
        │       ▼
        │   WfmValidator
        │       ▼
        │   WfmNormalizer
        │
        └── AI
                ▼
          RequirementWfmCache lookup
                │
                ├── Cache Hit
                │       ▼
                │   Cached WFM Final
                │
                └── Cache Miss
                        ▼
                  AiRequirementAnalyzer
                        ▼
                  PromptBuilder
                        ▼
                  GeminiProvider
                        ▼
                  AiWfmResponseParser
                        ▼
                  WfmValidator
                        ▼
                  WfmNormalizer
                        ▼
                  Save WFM to Cache
                        ▼
                    WFM Final
        │
        ▼
Compile WFM → Flowchart
        │
        ▼
Generate Test Cases from WFM
        │
        ▼
Return:
{
  "wfm": {},
  "flowchart": {},
  "testCases": [],
  "metadata": {}
}
```

Acceptance criteria:

- `wfm` exists.
- `flowchart` exists.
- `metadata` exists.
- `flowchart` remains backward-compatible.
- Test cases are generated from WFM.
- WFM does not contain UI fields.

---

## 6. Metadata Contract

Recommended metadata shape:

```json
{
  "analyzer": "AI",
  "provider": "GEMINI",
  "model": "gemini-2.5-flash-lite",
  "promptVersion": "requirement-to-wfm-v1",
  "cacheHit": false,
  "fallbackUsed": false,
  "fallbackReason": null,
  "validationWarnings": [],
  "validationErrors": [],
  "latencyMs": 1234
}
```

For rule-based mode:

```json
{
  "analyzer": "RULE_BASED",
  "provider": null,
  "model": null,
  "promptVersion": null,
  "cacheHit": false,
  "fallbackUsed": false,
  "validationWarnings": [],
  "validationErrors": []
}
```

---

## 7. Test Plan

### Unit tests

Add tests for:

1. Prompt builder

Verify it includes:

- WFM schema
- Core node roles
- Core transition semantics
- Forbidden UI fields
- Requirement text
- JSON-only instruction

2. AI parser

Verify it rejects:

- Empty response
- Markdown-only response
- Invalid JSON
- JSON without WFM fields

Verify it accepts:

- Valid WFM JSON
- JSON wrapped in code fence if extractor supports cleanup

3. AI analyzer with fake provider

Verify:

- Provider is called on cache miss
- Provider is not called on cache hit
- Parser is called
- Validator is called
- Normalizer is called
- Final WFM is returned

4. Fallback

Verify:

- Timeout → rule-based fallback
- Invalid JSON → rule-based fallback
- Invalid WFM → rule-based fallback
- Metadata includes fallback reason

5. Cache

Verify:

- Stable key for same input/config
- Different key when prompt version changes
- Different key when model changes
- Expired cache ignored
- Only validated WFM is saved

6. WFM safety

Verify AI output with forbidden fields is rejected or cleaned according to current validator/normalizer policy:

- position
- color
- shape
- sourceHandle
- targetHandle
- edgeLabel

---

### Integration tests

Add/update tests for:

1. Rule-based mode

Expected:

- Response has `wfm`
- Response has `flowchart`
- Response has `metadata.analyzer = RULE_BASED`

2. AI mode with fake provider

Expected:

- Response has AI-generated WFM
- Response has flowchart compiled from WFM
- Response has metadata provider/model/promptVersion
- Response has `cacheHit = false` on first call

3. AI mode cache hit

Expected:

- First call saves cache
- Second call returns cache hit
- Provider is not called again if test setup can verify it
- Metadata has `cacheHit = true`

4. AI failure with fallback enabled

Expected:

- Response has rule-based WFM
- Response has `flowchart`
- Metadata has `fallbackUsed = true`
- Metadata has fallback reason

5. WFM no UI fields

Expected WFM JSON does not contain:

- position
- color
- shape
- sourceHandle
- targetHandle
- edgeLabel

6. Test cases from WFM

Expected:

- Test cases exist if generator is already supported
- Test cases trace back to WFM nodes/transitions if supported
- Test cases are not generated from React Flow graph

---

## 8. Security and Logging Requirements

Do not log by default:

- Full requirement text
- Raw AI response
- API key
- Provider request payload

Safe logs:

```text
requestId
analyzerMode
provider
model
promptVersion
cacheHit
latencyMs
success/failure
fallbackUsed
validationErrorCount
```

Provider errors must be sanitized before returning to client.

---

## 9. Coding Constraints

Backend:

- Keep separation of concerns.
- Do not call Gemini directly from controller/service business logic.
- Use `AiProvider` interface.
- Validate AI output before use.
- Normalize WFM before compile/render/test generation.
- Preserve backward compatibility.

Frontend, if touched:

- Follow `fe-style-guide.md`.
- Use registry pattern.
- Do not declare direct union string types for node types if registry exists.
- Use `import type` for type-only imports.
- Do not put UI fields into WFM.
- Do not generate semantic test cases from React Flow graph.

General:

- Do not rewrite the whole project.
- Do not add large dependencies unless necessary.
- Do not implement non-MVP providers.
- Do not implement AI repair in MVP.

---

## 10. Final Acceptance Criteria

The AI MVP is complete when:

1. Default behavior remains rule-based.
2. AI mode can be enabled by configuration.
3. GeminiProvider is implemented behind `AiProvider`.
4. Prompt builder generates strict Requirement → WFM prompt.
5. AI response parser handles invalid responses safely.
6. AI output passes WFM validation before use.
7. WFM is normalized before returning.
8. Cache is checked before AI call.
9. Cache stores only validated + normalized WFM.
10. Cache metadata reports hit/miss.
11. AI failures fallback to rule-based when configured.
12. API still returns backward-compatible `flowchart`.
13. API returns `wfm` and `metadata`.
14. Test cases, if returned, are generated from WFM.
15. WFM does not contain UI fields.
16. Tests cover prompt, parser, analyzer, fallback, cache, and API integration.
17. No DeepSeek/OpenRouter/Ollama implementation is included in this MVP.
18. No AI repair loop is included in this MVP.

---

## 11. Recommended Codex Execution Strategy

When asking Codex to implement this, split work into smaller prompts if possible:

```text
Prompt 1: Add AI interfaces, config, fake provider, and tests.
Prompt 2: Add prompt builder and parser with tests.
Prompt 3: Add AiRequirementAnalyzer orchestration and fallback.
Prompt 4: Add Requirement WFM cache and tests.
Prompt 5: Add GeminiProvider.
Prompt 6: Integrate API and add integration tests.
Prompt 7: Senior review pass for architecture violations.
```

Avoid giving Codex the entire AI implementation in one huge step unless the repo is small and tests are fast.

---

## 12. Senior Review Checklist

Before merging, review these questions:

1. Does any business service import Gemini-specific code?
2. Can the app still run with `requirement.analyzer.mode=rule_based`?
3. Does AI output pass validator before use?
4. Does normalizer run before WFM is compiled to flowchart/test cases?
5. Does cache store only final WFM, not raw AI output as source of truth?
6. Does changing prompt version cause cache miss?
7. Does fallback preserve `wfm`, `flowchart`, and `metadata`?
8. Does WFM contain any UI fields?
9. Are test cases generated from WFM, not React Flow graph?
10. Are provider errors sanitized?
11. Are full requirement text and raw AI response excluded from normal production logs?
12. Did the change avoid implementing non-MVP providers?

