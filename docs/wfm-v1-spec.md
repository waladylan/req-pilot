# WFM v1 — Workflow Model Specification

**Status:** Draft v1  
**Model type:** `WORKFLOW_AST`  
**Primary purpose:** Convert natural-language requirements into a stable business workflow model that can be compiled into flowcharts, test cases, documentation, and future automation outputs.

---

## 1. Overview

WFM v1, short for **Workflow Model v1**, is the canonical domain model for the project **Requirement → Flowchart → Test Cases**.

WFM is not a React Flow graph.  
WFM is not Mermaid.  
WFM is not a UI layout format.  
WFM is a **business workflow AST**.

The intended pipeline is:

```text
Requirement Text
      ↓
AI Parser / Rule Parser
      ↓
WFM v1: Workflow AST
      ↓
Compilers / Renderers
      ↓
React Flow
Mermaid
Test Cases
BPMN
PlantUML
Documentation
Automation Tests
```

The core design decision is:

> AI should generate only WFM. It should not generate React Flow nodes, Mermaid syntax, colors, shapes, coordinates, handles, or UI-specific data.

---

## 2. Design Goals

### 2.1 Business-first model

WFM represents business meaning, not visual presentation.

Allowed:

```json
{
  "role": "DECISION",
  "kind": "CREDENTIAL_VALIDATION",
  "title": "Credentials are valid?"
}
```

Not allowed:

```json
{
  "x": 240,
  "y": 120,
  "shape": "diamond",
  "color": "green",
  "sourceHandle": "right"
}
```

### 2.2 Renderer-independent

A valid WFM document must be renderable into multiple targets without asking AI again:

- React Flow
- Mermaid
- BPMN
- PlantUML
- SVG/PNG export
- Test case table
- User stories
- Acceptance criteria
- Automation test scripts

### 2.3 AI-friendly

WFM must be simple enough for lightweight/free LLMs to produce reliably.

The AI does not need to reason about layout, canvas state, colors, ports, or rendering details.

### 2.4 Extensible node semantics

WFM must not lock business node types into a hard enum.

It uses:

```text
role = controlled core workflow grammar
kind = extensible business/domain type
```

Example:

```json
{
  "role": "ACTION",
  "kind": "SEND_EMAIL",
  "title": "Send verification email"
}
```

If the renderer does not know `SEND_EMAIL`, it can still render the node using the fallback `ACTION` role.

### 2.5 Versioned schema

Every WFM document must include:

```json
{
  "schemaVersion": "1.0"
}
```

Future versions can introduce new features without breaking old WFM documents.

---

## 3. Core Concepts

| Concept | Meaning |
|---|---|
| `WfmDocument` | The complete workflow document. |
| `Workflow` | Metadata about the workflow, such as title, domain, language. |
| `AST` | The business workflow AST containing actors, variables, nodes, transitions, and annotations. |
| `Node` | A business step, decision, input, output, error, start, or end point. |
| `Transition` | A business flow between two nodes. This is not a visual edge. |
| `Role` | A controlled structural role used by compilers. |
| `Kind` | An extensible business/domain subtype. |
| `Actor` | A user, system, admin, guest, or external system involved in the workflow. |
| `Variable` | A business variable or state used in decisions and generated tests. |
| `Annotation` | A business note, warning, or comment attached to the workflow, node, or transition. |
| `Extension` | Optional definitions for custom node kinds and transition kinds. |
| `Compiler / Renderer` | A module that converts WFM into another target such as React Flow, Mermaid, or test cases. |

---

## 4. Top-level Document Structure

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

TypeScript type:

```ts
export type WfmDocument = {
  schemaVersion: "1.0";
  modelType: "WORKFLOW_AST";
  workflow: WfmWorkflow;
  extensions?: WfmExtensions;
  ast: WfmAst;
};
```

---

## 5. Workflow Metadata

```ts
export type WfmWorkflow = {
  id: string;
  title: string;
  description?: string;
  language?: WfmLanguage;
  domain?: string;
  sourceRequirement?: string;
};
```

Registry:

```ts
export const WFM_LANGUAGE_REGISTRY = {
  VI: {
    value: "vi",
    label: "Vietnamese",
  },
  EN: {
    value: "en",
    label: "English",
  },
  UNKNOWN: {
    value: "unknown",
    label: "Unknown",
  },
} as const;

export type WfmLanguage =
  (typeof WFM_LANGUAGE_REGISTRY)[keyof typeof WFM_LANGUAGE_REGISTRY]["value"];
```

Example:

```json
{
  "id": "user-login",
  "title": "User Login",
  "description": "Login flow with validation, success, failure, retry, and cancel.",
  "language": "en",
  "domain": "authentication"
}
```

Rules:

- `id` is required.
- `title` is required.
- `sourceRequirement` is optional and should be used only for traceability.
- Rendering should not depend on `sourceRequirement`.

---

## 6. AST Structure

```ts
export type WfmAst = {
  actors?: WfmActor[];
  variables?: WfmVariable[];
  nodes: WfmNode[];
  transitions: WfmTransition[];
  annotations?: WfmAnnotation[];
};
```

Rules:

- `nodes` is required.
- `transitions` is required.
- `actors`, `variables`, and `annotations` are optional.
- AST data represents business workflow semantics only.

---

## 7. Node Role and Node Kind

WFM v1 separates structural role from business kind.

```text
role = required, controlled workflow grammar
kind = required, extensible domain/business type
```

### 7.1 Node role registry

```ts
export const WFM_NODE_ROLE_REGISTRY = {
  START: {
    label: "Start",
    description: "Entry point of the workflow",
  },
  END: {
    label: "End",
    description: "Terminal point of the workflow",
  },
  ACTION: {
    label: "Action",
    description: "A business action or processing step",
  },
  DECISION: {
    label: "Decision",
    description: "A conditional branching point",
  },
  INPUT: {
    label: "Input",
    description: "User or system input",
  },
  OUTPUT: {
    label: "Output",
    description: "Visible output, message, result, or response",
  },
  ERROR: {
    label: "Error",
    description: "Error, failure, or exceptional state",
  },
  SUBPROCESS: {
    label: "Subprocess",
    description: "A nested or reusable workflow",
  },
} as const;

export type WfmNodeRole = keyof typeof WFM_NODE_ROLE_REGISTRY;
```

### 7.2 Node type

```ts
export type WfmNode = {
  id: string;
  role: WfmNodeRole;
  kind: string;
  title: string;
  description?: string;
  actorId?: string;
  tags?: string[];
  data?: Record<string, unknown>;
};
```

### 7.3 Node examples

Core node:

```json
{
  "id": "N1",
  "role": "START",
  "kind": "START",
  "title": "Start"
}
```

Domain-specific action:

```json
{
  "id": "N6",
  "role": "ACTION",
  "kind": "SEND_EMAIL",
  "title": "Send verification email",
  "actorId": "SYSTEM"
}
```

Domain-specific decision:

```json
{
  "id": "N7",
  "role": "DECISION",
  "kind": "PAYMENT_APPROVED",
  "title": "Payment is approved?",
  "actorId": "PAYMENT_GATEWAY"
}
```

### 7.4 Node rules

- `id` must be unique within the document.
- `role` must be one of the core roles.
- `kind` is a string and may be custom.
- `title` is required.
- `actorId` should reference an existing actor when provided.
- `data` may contain business data only.
- `data` must not contain UI state, coordinates, colors, shapes, handles, or React Flow metadata.

---

## 8. Transitions

A transition represents business control flow between nodes.

It is intentionally named `transition`, not `edge`, because `edge` is a visual/canvas concept.

```ts
export type WfmTransition = {
  id: string;
  from: string;
  to: string;
  semantic: WfmTransitionSemantic;
  kind?: string;
  condition?: string;
  description?: string;
  data?: Record<string, unknown>;
};
```

### 8.1 Transition semantic registry

```ts
export const WFM_TRANSITION_SEMANTIC_REGISTRY = {
  DEFAULT: {
    label: "Default",
    description: "Normal sequence flow",
  },
  YES: {
    label: "Yes",
    description: "Positive branch",
  },
  NO: {
    label: "No",
    description: "Negative branch",
  },
  SUCCESS: {
    label: "Success",
    description: "Successful result",
  },
  FAILURE: {
    label: "Failure",
    description: "Failed result",
  },
  CANCEL: {
    label: "Cancel",
    description: "User cancels the flow",
  },
  RETRY: {
    label: "Retry",
    description: "Retry branch",
  },
  TIMEOUT: {
    label: "Timeout",
    description: "Timeout branch",
  },
} as const;

export type WfmTransitionSemantic = keyof typeof WFM_TRANSITION_SEMANTIC_REGISTRY;
```

### 8.2 Transition examples

Normal sequence:

```json
{
  "id": "T1",
  "from": "N1",
  "to": "N2",
  "semantic": "DEFAULT"
}
```

Decision branch:

```json
{
  "id": "T3",
  "from": "N3",
  "to": "N4",
  "semantic": "YES",
  "condition": "Credentials are valid"
}
```

Domain-specific transition kind:

```json
{
  "id": "T7",
  "from": "N5",
  "to": "N8",
  "semantic": "FAILURE",
  "kind": "PAYMENT_DECLINED",
  "condition": "Payment provider declines the transaction"
}
```

### 8.3 Transition rules

- `id` must be unique within the document.
- `from` must reference an existing node.
- `to` must reference an existing node.
- `semantic` must be one of the core transition semantics.
- `kind` is optional and extensible.
- `condition` should be business-readable.
- UI must not render `condition` as an edge label by default.
- Renderer may use `semantic` to determine edge color.

---

## 9. Actors

Actors represent participants in the workflow.

```ts
export const WFM_ACTOR_TYPE_REGISTRY = {
  USER: {
    label: "User",
  },
  SYSTEM: {
    label: "System",
  },
  EXTERNAL_SYSTEM: {
    label: "External System",
  },
  ADMIN: {
    label: "Admin",
  },
  GUEST: {
    label: "Guest",
  },
} as const;

export type WfmActorType = keyof typeof WFM_ACTOR_TYPE_REGISTRY;

export type WfmActor = {
  id: string;
  name: string;
  type: WfmActorType;
};
```

Example:

```json
{
  "id": "SYSTEM",
  "name": "System",
  "type": "SYSTEM"
}
```

Actors help generate better test cases:

```text
User enters username and password.
System validates credentials.
```

---

## 10. Variables

Variables represent business data, flags, or states used in the workflow.

```ts
export const WFM_VARIABLE_TYPE_REGISTRY = {
  STRING: {
    label: "String",
  },
  NUMBER: {
    label: "Number",
  },
  BOOLEAN: {
    label: "Boolean",
  },
  DATE: {
    label: "Date",
  },
  OBJECT: {
    label: "Object",
  },
  ARRAY: {
    label: "Array",
  },
  UNKNOWN: {
    label: "Unknown",
  },
} as const;

export type WfmVariableType = keyof typeof WFM_VARIABLE_TYPE_REGISTRY;

export type WfmVariable = {
  id: string;
  name: string;
  type: WfmVariableType;
  description?: string;
  required?: boolean;
  defaultValue?: unknown;
};
```

Example:

```json
{
  "id": "V1",
  "name": "credentialsValid",
  "type": "BOOLEAN",
  "description": "Whether username and password are valid."
}
```

Variables are useful for:

- Decision conditions
- Test case generation
- Automation test generation
- State machine generation
- API contract generation

---

## 11. Annotations

Annotations are business comments, warnings, or notes.

They are not UI comments.

```ts
export const WFM_ANNOTATION_TARGET_REGISTRY = {
  WORKFLOW: {
    label: "Workflow",
  },
  NODE: {
    label: "Node",
  },
  TRANSITION: {
    label: "Transition",
  },
} as const;

export type WfmAnnotationTarget = keyof typeof WFM_ANNOTATION_TARGET_REGISTRY;

export const WFM_ANNOTATION_SEVERITY_REGISTRY = {
  INFO: {
    label: "Info",
  },
  WARNING: {
    label: "Warning",
  },
  ERROR: {
    label: "Error",
  },
} as const;

export type WfmAnnotationSeverity = keyof typeof WFM_ANNOTATION_SEVERITY_REGISTRY;

export type WfmAnnotation = {
  id: string;
  targetType: WfmAnnotationTarget;
  targetId?: string;
  text: string;
  severity?: WfmAnnotationSeverity;
};
```

Example:

```json
{
  "id": "A1",
  "targetType": "NODE",
  "targetId": "N5",
  "text": "Authentication may fail if the auth server is unavailable.",
  "severity": "WARNING"
}
```

---

## 12. Extensions

Extensions allow WFM to support custom domain-specific node kinds and transition kinds without changing the core schema.

```ts
export type WfmExtensions = {
  nodeKinds?: WfmNodeKindDefinition[];
  transitionKinds?: WfmTransitionKindDefinition[];
};
```

### 12.1 Custom node kind definition

```ts
export type WfmNodeKindDefinition = {
  kind: string;
  extendsRole: WfmNodeRole;
  label: string;
  description?: string;
  namespace?: string;
};
```

Example:

```json
{
  "kind": "PAYMENT_CAPTURE",
  "extendsRole": "ACTION",
  "label": "Payment Capture",
  "description": "Captures payment from a payment provider",
  "namespace": "payment"
}
```

### 12.2 Custom transition kind definition

```ts
export type WfmTransitionKindDefinition = {
  kind: string;
  extendsSemantic: WfmTransitionSemantic;
  label: string;
  description?: string;
  namespace?: string;
};
```

Example:

```json
{
  "kind": "PAYMENT_DECLINED",
  "extendsSemantic": "FAILURE",
  "label": "Payment Declined",
  "description": "The payment provider declined the transaction",
  "namespace": "payment"
}
```

### 12.3 Extension fallback behavior

If a renderer recognizes a custom `kind`, it may use custom icons, styles, or domain-specific behavior.

If it does not recognize a custom `kind`, it must fallback safely:

```text
node.kind fallback → node.role
transition.kind fallback → transition.semantic
```

Example:

```text
PAYMENT_CAPTURE is unknown
↓
role ACTION is known
↓
Render as a normal action node
```

---

## 13. Canvas View State

Canvas layout must be stored separately from WFM.

```ts
export type WorkflowCanvasViewState = {
  workflowId: string;
  nodePositions: Record<
    string,
    {
      x: number;
      y: number;
    }
  >;
  viewport?: {
    x: number;
    y: number;
    zoom: number;
  };
};
```

Important boundary:

```text
WFM = business source of truth
Canvas View State = visual layout state
```

When a user drags a node:

```text
Update Canvas View State
Do not update WFM
```

When a user edits a node label:

```text
Update WFM node.title
Then sync WFM back to requirement text
```

---

## 14. Renderer Mapping

Renderer mapping must not live inside WFM.

Example React Flow rendering registry:

```ts
export const NODE_RENDER_REGISTRY = {
  START: {
    shape: "pill",
    color: "#22C55E",
  },
  END: {
    shape: "pill",
    color: "#EF4444",
  },
  ACTION: {
    shape: "rectangle",
    color: "#3B82F6",
  },
  DECISION: {
    shape: "diamond",
    color: "#F59E0B",
  },
  INPUT: {
    shape: "parallelogram",
    color: "#8B5CF6",
  },
  OUTPUT: {
    shape: "parallelogram",
    color: "#06B6D4",
  },
  ERROR: {
    shape: "warning-rectangle",
    color: "#EF4444",
  },
  SUBPROCESS: {
    shape: "rectangle",
    color: "#64748B",
  },
} as const;
```

Example edge rendering registry:

```ts
export const EDGE_RENDER_REGISTRY = {
  DEFAULT: {
    color: "#6B7280",
  },
  YES: {
    color: "#22C55E",
  },
  NO: {
    color: "#EF4444",
  },
  SUCCESS: {
    color: "#3B82F6",
  },
  FAILURE: {
    color: "#F97316",
  },
  CANCEL: {
    color: "#EF4444",
  },
  RETRY: {
    color: "#A855F7",
  },
  TIMEOUT: {
    color: "#F59E0B",
  },
} as const;
```

Suggested React Flow mapping:

| WFM Role | React Flow Shape |
|---|---|
| `START` | Pill / rounded rectangle |
| `END` | Pill / rounded rectangle |
| `ACTION` | Rectangle |
| `DECISION` | Diamond |
| `INPUT` | Parallelogram |
| `OUTPUT` | Parallelogram |
| `ERROR` | Warning rectangle |
| `SUBPROCESS` | Rectangle / subprocess style |

Suggested transition color mapping:

| WFM Semantic | React Flow Edge Color |
|---|---|
| `DEFAULT` | Gray |
| `YES` | Green |
| `NO` | Red |
| `SUCCESS` | Blue |
| `FAILURE` | Orange |
| `CANCEL` | Red |
| `RETRY` | Purple |
| `TIMEOUT` | Amber |

---

## 15. Validation Rules

WFM v1 must have a validator.

### 15.1 Required structural rules

1. `schemaVersion` must be `"1.0"`.
2. `modelType` must be `"WORKFLOW_AST"`.
3. `workflow.id` is required.
4. `workflow.title` is required.
5. `ast.nodes` is required and must not be empty.
6. `ast.transitions` is required.
7. Node IDs must be unique.
8. Transition IDs must be unique.
9. Every transition `from` must reference an existing node.
10. Every transition `to` must reference an existing node.
11. Exactly one `START` role node is required.
12. At least one `END` role node is required.
13. `START` nodes should not have incoming transitions.
14. `END` nodes should not have outgoing transitions.
15. Every non-START node should be reachable from START.
16. Every path should eventually reach END, except explicit `RETRY` or loop flows.
17. `DECISION` nodes should have at least two outgoing transitions when possible.
18. Node `role` must be valid.
19. Transition `semantic` must be valid.
20. Actor IDs referenced by nodes should exist in `ast.actors` when actors are provided.

### 15.2 Extension rules

1. Custom node `kind` is allowed.
2. If a node kind is defined in `extensions.nodeKinds`, `extendsRole` must be a valid role.
3. If a transition kind is defined in `extensions.transitionKinds`, `extendsSemantic` must be a valid semantic.
4. Unknown node kinds must fallback to `role`.
5. Unknown transition kinds must fallback to `semantic`.

### 15.3 UI separation rules

Validator should reject or warn if WFM contains UI-only fields such as:

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
style
className
```

These fields belong to renderer or canvas view state, not WFM.

---

## 16. Normalization Rules

A normalizer may clean AI or rule-parser output before validation.

Suggested normalization steps:

1. Trim empty titles and descriptions.
2. Generate stable IDs if missing.
3. Normalize role and semantic casing to uppercase.
4. Fill missing `kind` with the same value as `role` for core nodes.
5. Infer simple actors when not provided:
   - `USER`
   - `SYSTEM`
6. Remove duplicate tags.
7. Remove empty optional arrays.
8. Remove UI-only fields.
9. Convert obvious branch words:
   - `confirm`, `valid`, `approved`, `có`, `đúng` → `YES`
   - `cancel`, `invalid`, `rejected`, `không`, `sai` → `NO`
   - `success`, `passed`, `thành công` → `SUCCESS`
   - `failure`, `error`, `failed`, `lỗi`, `thất bại` → `FAILURE`

---

## 17. AI Output Contract

AI providers must return strict JSON only.

The AI must not return:

- Markdown
- Explanation text
- React Flow JSON
- Mermaid syntax
- Coordinates
- Colors
- Shapes
- Handles
- Edge labels
- CSS class names

Expected AI output:

```json
{
  "schemaVersion": "1.0",
  "modelType": "WORKFLOW_AST",
  "workflow": {
    "id": "string",
    "title": "string",
    "description": "string",
    "language": "vi",
    "domain": "string"
  },
  "extensions": {
    "nodeKinds": [],
    "transitionKinds": []
  },
  "ast": {
    "actors": [],
    "variables": [],
    "nodes": [],
    "transitions": [],
    "annotations": []
  }
}
```

Backend flow:

```text
AI raw response
      ↓
Parse JSON
      ↓
Normalize WFM
      ↓
Validate WFM
      ↓
If valid: use WFM
If invalid: fallback to RuleBasedRequirementAnalyzer
```

---

## 18. Suggested Module Structure

### 18.1 Frontend

```text
src/domain/wfm/
├── wfm.types.ts
├── wfm.constants.ts
├── wfm.validator.ts
├── wfm.normalizer.ts
├── wfm.graph.ts
├── wfm-to-react-flow.ts
├── react-flow-to-wfm.ts
├── wfm-to-requirement.ts
├── wfm-to-test-cases.ts
├── wfm-to-mermaid.ts
└── index.ts
```

### 18.2 Backend

```text
backend/src/main/java/.../wfm/
├── dto/
├── model/
├── validator/
├── normalizer/
├── compiler/
└── service/
```

Suggested services:

```text
RequirementAnalyzer
├── RuleBasedRequirementAnalyzer
└── AiRequirementAnalyzer

WfmValidator
WfmNormalizer
WfmToFlowCompiler
WfmToTestCaseCompiler
WfmToMermaidCompiler
```

---

## 19. Example WFM v1 — User Login

This example includes the main transition colors:

```text
DEFAULT = gray
YES = green
NO = red
SUCCESS = blue
FAILURE = orange
RETRY = purple
CANCEL = red
```

```json
{
  "schemaVersion": "1.0",
  "modelType": "WORKFLOW_AST",
  "workflow": {
    "id": "user-login",
    "title": "User Login",
    "description": "Login flow with validation, success, failure, retry, and cancel.",
    "language": "en",
    "domain": "authentication"
  },
  "extensions": {
    "nodeKinds": [
      {
        "kind": "CREDENTIAL_VALIDATION",
        "extendsRole": "ACTION",
        "label": "Credential Validation",
        "description": "Validates username and password",
        "namespace": "authentication"
      }
    ],
    "transitionKinds": []
  },
  "ast": {
    "actors": [
      {
        "id": "USER",
        "name": "User",
        "type": "USER"
      },
      {
        "id": "SYSTEM",
        "name": "System",
        "type": "SYSTEM"
      }
    ],
    "variables": [
      {
        "id": "V1",
        "name": "credentialsProvided",
        "type": "BOOLEAN",
        "description": "Whether username and password are provided."
      },
      {
        "id": "V2",
        "name": "credentialsValid",
        "type": "BOOLEAN",
        "description": "Whether username and password are valid."
      }
    ],
    "nodes": [
      {
        "id": "N1",
        "role": "START",
        "kind": "START",
        "title": "Start"
      },
      {
        "id": "N2",
        "role": "INPUT",
        "kind": "LOGIN_FORM_INPUT",
        "title": "User enters username and password",
        "actorId": "USER"
      },
      {
        "id": "N3",
        "role": "DECISION",
        "kind": "REQUIRED_INFORMATION_CHECK",
        "title": "Required information is provided?",
        "actorId": "SYSTEM"
      },
      {
        "id": "N4",
        "role": "OUTPUT",
        "kind": "VALIDATION_ERROR_MESSAGE",
        "title": "Show validation error",
        "actorId": "SYSTEM"
      },
      {
        "id": "N5",
        "role": "ACTION",
        "kind": "CREDENTIAL_VALIDATION",
        "title": "Validate credentials",
        "actorId": "SYSTEM"
      },
      {
        "id": "N6",
        "role": "ACTION",
        "kind": "AUTHENTICATE_USER",
        "title": "Authenticate user",
        "actorId": "SYSTEM"
      },
      {
        "id": "N7",
        "role": "OUTPUT",
        "kind": "REDIRECT_TO_DASHBOARD",
        "title": "Redirect to Dashboard",
        "actorId": "SYSTEM"
      },
      {
        "id": "N8",
        "role": "ERROR",
        "kind": "INVALID_CREDENTIALS_ERROR",
        "title": "Show invalid username or password",
        "actorId": "SYSTEM"
      },
      {
        "id": "N9",
        "role": "DECISION",
        "kind": "RETRY_LOGIN_DECISION",
        "title": "User wants to retry?",
        "actorId": "USER"
      },
      {
        "id": "N10",
        "role": "END",
        "kind": "END",
        "title": "End"
      }
    ],
    "transitions": [
      {
        "id": "T1",
        "from": "N1",
        "to": "N2",
        "semantic": "DEFAULT"
      },
      {
        "id": "T2",
        "from": "N2",
        "to": "N3",
        "semantic": "DEFAULT"
      },
      {
        "id": "T3",
        "from": "N3",
        "to": "N5",
        "semantic": "YES",
        "condition": "Required information is provided"
      },
      {
        "id": "T4",
        "from": "N3",
        "to": "N4",
        "semantic": "NO",
        "condition": "Required information is missing"
      },
      {
        "id": "T5",
        "from": "N4",
        "to": "N10",
        "semantic": "DEFAULT"
      },
      {
        "id": "T6",
        "from": "N5",
        "to": "N6",
        "semantic": "SUCCESS",
        "condition": "Credentials are valid"
      },
      {
        "id": "T7",
        "from": "N5",
        "to": "N8",
        "semantic": "FAILURE",
        "condition": "Credentials are invalid"
      },
      {
        "id": "T8",
        "from": "N6",
        "to": "N7",
        "semantic": "DEFAULT"
      },
      {
        "id": "T9",
        "from": "N7",
        "to": "N10",
        "semantic": "DEFAULT"
      },
      {
        "id": "T10",
        "from": "N8",
        "to": "N9",
        "semantic": "DEFAULT"
      },
      {
        "id": "T11",
        "from": "N9",
        "to": "N2",
        "semantic": "RETRY",
        "condition": "User retries login"
      },
      {
        "id": "T12",
        "from": "N9",
        "to": "N10",
        "semantic": "CANCEL",
        "condition": "User cancels login"
      }
    ],
    "annotations": [
      {
        "id": "A1",
        "targetType": "NODE",
        "targetId": "N5",
        "text": "Authentication may fail if the auth server is unavailable.",
        "severity": "WARNING"
      }
    ]
  }
}
```

---

## 20. Minimal Valid WFM

```json
{
  "schemaVersion": "1.0",
  "modelType": "WORKFLOW_AST",
  "workflow": {
    "id": "minimal-flow",
    "title": "Minimal Flow",
    "language": "en"
  },
  "ast": {
    "nodes": [
      {
        "id": "N1",
        "role": "START",
        "kind": "START",
        "title": "Start"
      },
      {
        "id": "N2",
        "role": "END",
        "kind": "END",
        "title": "End"
      }
    ],
    "transitions": [
      {
        "id": "T1",
        "from": "N1",
        "to": "N2",
        "semantic": "DEFAULT"
      }
    ]
  }
}
```

---

## 21. Implementation Acceptance Criteria

A correct WFM v1 implementation must satisfy:

1. WFM is treated as the source of truth for business workflow.
2. React Flow is treated as a compiled visual view.
3. Canvas positions are stored outside WFM.
4. AI returns WFM JSON only.
5. Rule-based parser can also return WFM.
6. WFM can be compiled into React Flow nodes and edges.
7. WFM can be compiled into readable requirement text.
8. WFM can be compiled into test cases.
9. WFM validator rejects invalid node references.
10. WFM validator rejects duplicate IDs.
11. WFM validator warns or rejects UI-only fields.
12. Unknown node `kind` does not break rendering if `role` is valid.
13. Unknown transition `kind` does not break rendering if `semantic` is valid.
14. Node role and transition semantic use registry pattern.
15. No string literal union types are hardcoded directly.

---

## 22. Future Version Ideas

Potential WFM v1.1 or v2 additions:

- Nested subprocess body
- Swimlanes
- Parallel fork/join
- Timers and scheduled events
- API contract references
- Data schema references
- Test data generation hints
- Risk/priority metadata
- BPMN-compatible event types
- State machine export
- Playwright/Cypress automation metadata

These should be added only when needed, without weakening the v1 boundary between business model and UI rendering.

---

## 23. Final Summary

WFM v1 is the stable business workflow AST for the application.

The most important rules are:

```text
WFM is not UI.
WFM is not React Flow.
WFM is not Mermaid.
WFM is the business workflow source of truth.
```

Core entities:

```text
WfmDocument
Workflow
AST
Actor
Variable
Node
Transition
Annotation
Extension
```

Core node model:

```text
role = controlled workflow grammar
kind = extensible business/domain type
```

Core transition model:

```text
semantic = controlled flow meaning
kind = optional extensible domain meaning
```

This makes WFM simple enough for free AI models to generate, stable enough for validators and tests, and flexible enough to support future outputs such as BPMN, PlantUML, documentation, and automation tests.
