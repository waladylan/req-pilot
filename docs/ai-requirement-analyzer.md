# AI Requirement Analyzer Architecture (MVP)

## 1. Purpose

This document defines the MVP architecture for adding AI-based requirement analysis to the project:

```text
Requirement → WFM v1 → Flowchart / Test Cases / Mermaid / Future Exporters
```

The MVP target is:

```text
Rule-based Analyzer + Gemini Flash
```

AI is introduced only as a parser from natural language requirement text to **WFM v1 JSON**.

AI must not generate React Flow, Mermaid, layout, color, shape, test cases, BPMN, PlantUML, or any UI-specific artifact.

---

## 2. Current Project Context

### Tech Stack

- Frontend: React + TypeScript + Vite
- Flowchart UI: React Flow
- Backend: Java Spring Boot
- Database: PostgreSQL
- UI direction: canvas-first workspace similar to Figma / Weavy / modern node editors
- Current backend: rule-based parser
- AI status: not implemented yet

### Existing Workflow

```text
Requirement Text
→ RuleBasedRequirementAnalyzer
→ WFM v1
→ WfmValidator
→ WfmNormalizer
→ WFM Compilers / Renderers
   - WFM → React Flow
   - WFM → Test Cases
   - WFM → Mermaid
   - Future: WFM → BPMN / PlantUML / Automation Test / User Story
```

---

## 3. Core Principle

## WFM is the source of truth

WFM is a Workflow AST, not a React Flow graph.

```text
Requirement Text
       │
       ▼
     WFM v1
       │
       ├── React Flow Renderer
       ├── Test Case Generator
       ├── Mermaid Exporter
       ├── BPMN Exporter
       ├── PlantUML Exporter
       └── Automation Test Generator
```

AI only generates WFM JSON.

Everything else must be generated from WFM.

---

## 4. What AI Is Responsible For

AI is responsible for:

- Understanding natural language requirement text
- Extracting workflow actors if available
- Extracting workflow nodes
- Extracting workflow transitions
- Mapping node role to WFM core grammar
- Mapping transition semantic to WFM core transition semantics
- Returning valid WFM v1 JSON

AI is not responsible for:

- React Flow nodes
- React Flow edges
- Mermaid syntax
- BPMN XML
- Layout
- Position
- Color
- Shape
- Icons
- Edge labels
- Test case generation
- UI state
- Canvas state

---

## 5. WFM v1 Contract

AI output must follow WFM v1.

```json
{
  "schemaVersion": "1.0",
  "modelType": "WORKFLOW_AST",
  "workflow": {},
  "extensions": {},
  "ast": {
    "actors": [],
    "variables": [],
    "nodes": [],
    "transitions": [],
    "annotations": []
  }
}
```

### Core Node Roles

Controlled grammar roles:

```text
START
END
ACTION
DECISION
INPUT
OUTPUT
ERROR
SUBPROCESS
```

### Node Role vs Kind

WFM must not use a hardcoded `NodeKind` as the main semantic layer.

Use:

```text
role = controlled workflow grammar
kind = extensible business/domain type
```

Example:

```json
{
  "id": "N5",
  "role": "ACTION",
  "kind": "SEND_EMAIL",
  "title": "Send verification email",
  "actorId": "SYSTEM"
}
```

### Core Transition Semantics

```text
DEFAULT
YES
NO
SUCCESS
FAILURE
CANCEL
RETRY
TIMEOUT
```

Example:

```json
{
  "id": "T3",
  "from": "N3",
  "to": "N4",
  "semantic": "YES",
  "condition": "Credentials are valid"
}
```

---

## 6. Forbidden Fields in WFM

AI must never include UI-specific fields in WFM.

Forbidden fields:

```text
x
y
position
color
shape
width
height
selected
dragging
sourceHandle
targetHandle
reactFlowType
edgeLabel
```

Canvas state must be stored separately.

```json
{
  "workflowId": "string",
  "nodePositions": {
    "N1": {
      "x": 100,
      "y": 200
    }
  },
  "viewport": {
    "x": 0,
    "y": 0,
    "zoom": 1
  }
}
```

---

## 7. MVP Scope

MVP includes:

- Existing `RuleBasedRequirementAnalyzer`
- New `AiRequirementAnalyzer`
- New `AiProvider` abstraction
- Gemini Flash provider
- Prompt builder for Requirement → WFM
- AI response parser
- WFM validation after AI output
- WFM normalization after validation
- Fallback to rule-based analyzer if AI fails
- Lightweight requirement-to-WFM cache for AI mode
- Metadata in API response

MVP does not include:

- DeepSeek provider
- OpenRouter provider
- Ollama provider
- AI repair loop
- Prompt version A/B testing
- Confidence scoring
- Human approval workflow
- BPMN export
- PlantUML export
- Automation test generation
- AI-generated test cases
- AI-generated React Flow

---

## 8. High-Level Architecture

The AI path should include cache before calling the provider.

```text
                 Requirement Text
                        │
                        ▼
        Normalize Requirement Text for Cache Key
                        │
                        ▼
              RequirementAnalyzer
                        │
        ┌───────────────┴────────────────┐
        │                                │
        ▼                                ▼
RuleBasedRequirementAnalyzer     AiRequirementAnalyzer
                                         │
                                         ▼
                                RequirementWfmCache
                                         │
                    ┌────────────────────┴────────────────────┐
                    │                                         │
                    ▼                                         ▼
               Cache Hit                                 Cache Miss
                    │                                         │
                    ▼                                         ▼
              Cached WFM                              AI Module
                                                              │
                         ┌────────────────────────────────────┼────────────────────────────────────┐
                         │                                    │                                    │
                         ▼                                    ▼                                    ▼
                   PromptBuilder                        AiProvider                        AiResponseParser
                                                              │
                                                              ▼
                                                       GeminiProvider
                                                              │
                                                              ▼
                                                       Raw AI Response
                                                              │
                                                              ▼
                                                       WFM Draft JSON
                                                              │
                                                              ▼
                                                       WfmValidator
                                                              │
                                                              ▼
                                                      WfmNormalizer
                                                              │
                                                              ▼
                                                        WFM Final
                                                              │
                                                              ▼
                                                    Save WFM to Cache
                                                              │
                    ┌─────────────────────────────────────────┘
                    │
                    ▼
                 WFM Final
                    │
        ┌───────────┼──────────────────┐
        ▼           ▼                  ▼
 React Flow    Test Case           Mermaid
 Compiler      Generator           Exporter
```

---

## 9. Recommended Module Structure

Exact package names can follow the current backend convention.

Recommended structure:

```text
requirement/
  analyzer/
    RequirementAnalyzer.java
    RuleBasedRequirementAnalyzer.java
    AiRequirementAnalyzer.java
    RequirementAnalyzerFactory.java

ai/
  AiProvider.java
  AiProviderType.java
  AiGenerationRequest.java
  AiGenerationResponse.java

ai/gemini/
  GeminiProvider.java
  GeminiProperties.java

ai/prompt/
  RequirementToWfmPromptBuilder.java
  PromptTemplate.java

ai/parser/
  AiWfmResponseParser.java

ai/cache/
  RequirementWfmCache.java
  RequirementCacheKeyBuilder.java
  RequirementWfmCacheEntry.java

wfm/
  WfmValidator.java
  WfmNormalizer.java
  WfmValidationResult.java
```

---

## 10. RequirementAnalyzer Interface

The business layer should depend on `RequirementAnalyzer`, not directly on Gemini or any other AI provider.

```java
public interface RequirementAnalyzer {
    WfmModel analyze(String requirementText);
}
```

Implementations:

```text
RuleBasedRequirementAnalyzer
AiRequirementAnalyzer
```

---

## 11. AiProvider Interface

AI provider must be generic.

```java
public interface AiProvider {
    AiGenerationResponse generate(AiGenerationRequest request);
}
```

The application should not call Gemini SDK or Gemini HTTP client directly from business services.

Provider-specific details must stay inside provider implementation.

---

## 12. Analyzer Mode

The analyzer mode should be configurable.

Recommended modes:

```text
RULE_BASED
AI
HYBRID
```

For MVP:

```text
RULE_BASED
AI
```

`HYBRID` can be reserved for a later phase.

### Mode Behavior

#### RULE_BASED

```text
Requirement Text
→ RuleBasedRequirementAnalyzer
→ WFM
```

#### AI

```text
Requirement Text
→ AiRequirementAnalyzer
→ GeminiProvider
→ WFM Draft
→ WfmValidator
→ WfmNormalizer
→ WFM Final
```

If AI fails in MVP, fallback to rule-based analyzer if configured.

---

## 13. Configuration

Recommended Spring configuration:

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

Default mode should remain:

```properties
requirement.analyzer.mode=rule_based
```

This prevents AI from changing existing behavior unless explicitly enabled.

---

## 14. API Response Metadata

`POST /api/requirements/generate-flow` should remain backward-compatible.

Expected response direction:

```json
{
  "wfm": {},
  "flowchart": {},
  "metadata": {
    "analyzer": "AI",
    "provider": "GEMINI",
    "model": "gemini-2.5-flash-lite",
    "fallbackUsed": false,
    "cacheHit": false,
    "promptVersion": "requirement-to-wfm-v1",
    "validationWarnings": [],
    "validationErrors": [],
    "repairAttempts": 0
  }
}
```

For rule-based mode:

```json
{
  "wfm": {},
  "flowchart": {},
  "metadata": {
    "analyzer": "RULE_BASED",
    "provider": null,
    "model": null,
    "fallbackUsed": false,
    "validationWarnings": [],
    "validationErrors": []
  }
}
```

Do not remove `flowchart` because it is needed for backward compatibility.

---

## 15. Prompt Strategy

The prompt must be strict and narrow.

AI must return only JSON.

AI must not return Markdown, explanations, comments, or code fences.

### Prompt Rules

The prompt must say:

```text
You are a workflow AST generator.
Convert the requirement into WFM v1 JSON.
Return only valid JSON.
Do not include Markdown.
Do not include explanations.
Do not generate React Flow.
Do not generate Mermaid.
Do not generate layout.
Do not include UI fields.
Use role for core workflow grammar.
Use kind for extensible business/domain meaning.
```

### Prompt Must Include

- WFM schema contract
- Allowed core node roles
- Allowed core transition semantics
- Forbidden UI fields
- ID generation convention
- Requirement text
- Output-only-JSON instruction

---

## 16. Suggested Prompt Template

```text
You are a workflow AST generator.

Your task is to convert the user's requirement into WFM v1 JSON.

Return only valid JSON.
Do not return Markdown.
Do not wrap the JSON in code fences.
Do not include explanations.

WFM is a Workflow AST, not a UI graph.

Do not generate:
- React Flow
- Mermaid
- BPMN
- PlantUML
- layout
- position
- color
- shape
- icon
- edge label
- UI state

Use this WFM v1 structure:

{
  "schemaVersion": "1.0",
  "modelType": "WORKFLOW_AST",
  "workflow": {},
  "extensions": {},
  "ast": {
    "actors": [],
    "variables": [],
    "nodes": [],
    "transitions": [],
    "annotations": []
  }
}

Core node roles:
- START
- END
- ACTION
- DECISION
- INPUT
- OUTPUT
- ERROR
- SUBPROCESS

Use:
- role = core workflow grammar
- kind = extensible business/domain type

Core transition semantics:
- DEFAULT
- YES
- NO
- SUCCESS
- FAILURE
- CANCEL
- RETRY
- TIMEOUT

Forbidden fields in WFM:
- x
- y
- position
- color
- shape
- width
- height
- selected
- dragging
- sourceHandle
- targetHandle
- reactFlowType
- edgeLabel

ID rules:
- Node IDs should be stable within the generated model: N1, N2, N3...
- Transition IDs should be stable within the generated model: T1, T2, T3...
- Actor IDs should be uppercase semantic names when possible, for example USER, SYSTEM, ADMIN.

Requirement:
{{requirementText}}

Return WFM v1 JSON only.
```

---

## 17. AI Output Handling

AI output must not be trusted directly.

Pipeline:

```text
Raw AI Response
→ Extract JSON
→ Parse JSON
→ WFM Schema Validation
→ WFM Semantic Validation
→ WFM Normalization
→ WFM Final
```

### Parser Responsibility

`AiWfmResponseParser` should:

- Reject empty responses
- Reject non-JSON responses
- Extract JSON only if provider returns accidental wrapping
- Parse JSON into WFM DTO/model
- Reject JSON that does not match WFM model

### Validator Responsibility

`WfmValidator` should check:

- Required root fields
- `schemaVersion`
- `modelType`
- Node ID uniqueness
- Transition ID uniqueness
- Transition `from` exists
- Transition `to` exists
- START node existence
- END node existence
- DECISION outgoing transitions
- Unknown or missing role
- Unknown transition semantic
- Forbidden UI fields

### Normalizer Responsibility

`WfmNormalizer` should:

- Normalize missing optional arrays to empty arrays
- Normalize transition semantic if missing to `DEFAULT`
- Normalize actor references if possible
- Sort nodes/transitions deterministically if needed
- Preserve business meaning
- Never add UI fields

---

## 18. Fallback Strategy

For MVP, fallback should be simple.

```text
AI succeeds
→ use AI WFM

AI fails to respond
→ fallback rule-based

AI returns invalid JSON
→ fallback rule-based

AI returns invalid WFM
→ fallback rule-based
```

Metadata should indicate fallback:

```json
{
  "metadata": {
    "analyzer": "AI",
    "provider": "GEMINI",
    "fallbackUsed": true,
    "fallbackReason": "AI_RETURNED_INVALID_WFM"
  }
}
```

Do not implement AI repair in MVP.

---

## 19. Error Handling

Common error categories:

```text
AI_TIMEOUT
AI_PROVIDER_ERROR
AI_EMPTY_RESPONSE
AI_INVALID_JSON
AI_INVALID_WFM
AI_UNSUPPORTED_PROVIDER
AI_CONFIG_MISSING
```

If fallback is enabled:

```text
Return rule-based WFM + metadata warning
```

If fallback is disabled:

```text
Return controlled error response according to current backend convention
```

Do not expose raw provider errors or API keys in API response.

---

## 20. Security and Privacy

Requirement text may contain sensitive business information.

MVP rules:

- Do not log full requirement text in production logs by default
- Do not log AI API key
- Do not log full raw AI response unless debug mode is explicitly enabled
- Mask provider errors if they include request payload
- Store only necessary metadata
- Keep provider configuration in environment variables

Recommended safe logging:

```text
requestId
analyzerMode
provider
model
latencyMs
success/failure
fallbackUsed
validationErrorCount
```

---

## 21. Caching Strategy

Caching should be part of the MVP AI architecture because the expected usage is internal and repeated across multiple leaders/projects.

The cache should sit before the AI provider. It must cache only validated and normalized WFM, never raw untrusted AI output.

### Cache Flow

```text
Requirement Text
        │
        ▼
Normalize text
        │
        ▼
Build cache key
        │
        ▼
Check RequirementWfmCache
        │
        ├── HIT
        │     ▼
        │  Return cached WFM
        │     ▼
        │  metadata.cacheHit = true
        │
        └── MISS
              ▼
          Call AI provider
              ▼
          Parse / Validate / Normalize WFM
              ▼
          Save WFM to cache
              ▼
          metadata.cacheHit = false
```

Suggested cache key:

```text
SHA256(normalizedRequirementText + analyzerMode + provider + model + promptVersion + wfmSchemaVersion)
```

`promptVersion` is important. When the prompt changes, the system should not reuse WFM generated by an older prompt unless explicitly allowed.

Suggested cache value:

```json
{
  "wfm": {},
  "metadata": {
    "analyzer": "AI",
    "provider": "GEMINI",
    "model": "gemini-2.5-flash-lite",
    "promptVersion": "requirement-to-wfm-v1",
    "wfmSchemaVersion": "1.0",
    "createdAt": "...",
    "expiresAt": "..."
  }
}
```

### Cache Rules

- Cache only successful WFM Final output after validation and normalization.
- Do not cache invalid AI responses.
- Do not cache fallback WFM as AI result unless metadata clearly marks `fallbackUsed = true`.
- Do not include UI fields in cached WFM.
- Cache should be bypassable by config or request flag for debugging/regeneration.
- Cache metadata should be returned in API response as `metadata.cacheHit`.

### Recommended Storage

For MVP, PostgreSQL is enough. A simple table can be used:

```text
requirement_wfm_cache
- id
- cache_key
- normalized_requirement_hash
- analyzer_mode
- provider
- model
- prompt_version
- wfm_schema_version
- wfm_json
- metadata_json
- created_at
- expires_at
```

Redis can be considered later if latency becomes important, but PostgreSQL is simpler and easier to inspect during MVP.

---

## 22. Recommended MVP Implementation Phases

### Phase AI-0: Documentation

- Add this architecture document
- Align team on WFM contract
- Align team on AI scope

### Phase AI-1: Interfaces and Configuration

- Add `AiProvider`
- Add `AiGenerationRequest`
- Add `AiGenerationResponse`
- Add analyzer mode config
- Add `AiRequirementAnalyzer`
- Do not call real AI yet if not ready

### Phase AI-2: Gemini Provider

- Implement `GeminiProvider`
- Add timeout
- Add model config
- Add safe error handling
- Add API key config

### Phase AI-3: Prompt + Parser + Validation

- Add `RequirementToWfmPromptBuilder`
- Add `AiWfmResponseParser`
- Validate WFM after AI output
- Normalize WFM after validation

### Phase AI-4: Cache Integration

- Add `RequirementWfmCache` abstraction
- Add `RequirementCacheKeyBuilder`
- Cache only validated and normalized WFM
- Add `metadata.cacheHit`
- Add config to enable/disable cache

### Phase AI-5: API Integration

- Integrate analyzer mode into `POST /api/requirements/generate-flow`
- Keep backward-compatible `flowchart`
- Return metadata
- Add fallback to rule-based

### Phase AI-6: Tests

- Unit test prompt builder
- Unit test parser
- Unit test AI analyzer with fake provider
- Integration test API response shape
- Test fallback behavior
- Test invalid AI output handling

---

## 23. Test Requirements

### Unit Tests

Cover:

1. Prompt builder includes:
   - WFM schema
   - Core roles
   - Core transition semantics
   - Forbidden UI fields
   - Requirement text

2. AI parser rejects:
   - Empty response
   - Markdown-only response
   - Invalid JSON
   - JSON without WFM fields

3. AI analyzer:
   - Calls provider
   - Parses response
   - Validates WFM
   - Normalizes WFM
   - Returns WFM

4. Fallback:
   - Provider timeout → rule-based fallback
   - Invalid JSON → rule-based fallback
   - Invalid WFM → rule-based fallback

5. WFM safety:
   - AI output with `position` is rejected or cleaned according to validator/normalizer policy
   - AI output with `color` is rejected or cleaned according to validator/normalizer policy

6. Cache:
   - Same normalized requirement + same provider/model/promptVersion returns cache hit
   - Different promptVersion returns cache miss
   - Invalid AI output is not cached
   - Cached WFM does not contain UI fields

### Integration Tests

For `POST /api/requirements/generate-flow`:

1. Rule-based mode:
   - returns `wfm`
   - returns `flowchart`
   - returns `metadata.analyzer = RULE_BASED`

2. AI mode with fake provider:
   - returns AI-generated WFM
   - returns `flowchart` compiled from WFM
   - returns metadata provider/model

3. AI failure with fallback enabled:
   - returns rule-based WFM
   - returns metadata fallbackUsed = true

4. WFM does not contain UI fields:
   - no `position`
   - no `color`
   - no `shape`
   - no `sourceHandle`
   - no `targetHandle`
   - no `edgeLabel`

5. AI cache behavior:
   - first AI request returns `metadata.cacheHit = false`
   - second request with same normalized requirement returns `metadata.cacheHit = true`
   - provider is not called on cache hit

---

## 24. Future Roadmap

After MVP, the AI module can expand naturally.

### Phase 2: DeepSeek Provider

Add:

```text
DeepSeekProvider
```

Use case:

- Lower-cost API fallback
- Better long-term cost control

### Phase 3: OpenRouter Provider

Add:

```text
OpenRouterProvider
```

Use case:

- Flexible model routing
- Access to free/cheap models
- Backup provider

### Phase 4: Ollama / Local Provider

Add:

```text
OllamaProvider
```

Use case:

- Internal high-volume usage
- Privacy-sensitive requirements
- Cost control

Candidate local models:

```text
Qwen
DeepSeek Distill
Mistral
```

### Phase 5: Hybrid Mode

```text
AI success
→ use AI WFM

AI invalid
→ retry repair

still invalid
→ fallback rule-based
```

### Phase 6: AI Repair

Add repair prompt:

```text
Invalid WFM + validation errors
→ AI repair
→ validate again
```

### Phase 7: Quality Scoring

Add:

- confidence
- ambiguity warnings
- missing actor warnings
- missing branch warnings
- incomplete workflow warnings

---

## 25. Architecture Decision Summary

| Decision | Choice |
|---|---|
| MVP AI provider | Gemini Flash |
| Default analyzer | Rule-based |
| AI output | WFM JSON only |
| Source of truth | WFM v1 |
| React Flow | Renderer only |
| Test cases | Generated from WFM, not AI |
| Mermaid | Generated from WFM, not AI |
| Fallback | Rule-based |
| AI repair | Not in MVP |
| AI cache | MVP, PostgreSQL-backed or equivalent |
| Cache key | Requirement + analyzer/provider/model/promptVersion/schemaVersion |
| DeepSeek | Future phase |
| OpenRouter | Future phase |
| Ollama/local | Future phase |

---

## 26. Non-Negotiable Rules

- AI must not generate React Flow.
- AI must not generate Mermaid.
- AI must not generate layout.
- AI must not generate colors or shapes.
- AI must not generate test cases directly.
- AI must only generate WFM v1 JSON.
- WFM must not contain UI fields.
- WFM must pass validation before use.
- WFM must be normalized before compiling/rendering.
- Existing `flowchart` response must remain backward-compatible.
- Default mode must remain rule-based until AI is explicitly enabled.
- Business services must depend on `RequirementAnalyzer`, not provider-specific code.
- Provider-specific code must stay behind `AiProvider`.
- Cache must store only validated and normalized WFM.
- Cache must not store raw untrusted AI output as source of truth.

---

## 27. Final Recommended MVP Flow

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
        │       ▼
        │   WFM
        │
        └── AI
                ▼
          AiRequirementAnalyzer
                ▼
          Normalize requirement text
                ▼
          Build cache key
                ▼
          RequirementWfmCache
                │
          ┌─────┴─────┐
          │           │
          ▼           ▼
      Cache Hit   Cache Miss
          │           │
          │           ▼
          │     RequirementToWfmPromptBuilder
          │           ▼
          │     GeminiProvider
          │           ▼
          │     AiWfmResponseParser
          │           ▼
          │     WfmValidator
          │           ▼
          │     WfmNormalizer
          │           ▼
          │     Save WFM to Cache
          │           │
          └─────┬─────┘
                ▼
              WFM
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
  "metadata": {
    "analyzer": "AI | RULE_BASED",
    "provider": "GEMINI | null",
    "model": "gemini-2.5-flash-lite | null",
    "cacheHit": true,
    "fallbackUsed": false
  }
}
```

