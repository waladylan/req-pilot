# AI → WFM Architecture

Version: 1.0

---

# 1. Overview

The AI → WFM pipeline converts a natural language software requirement into a structured Workflow Model (WFM).

Unlike traditional AI-only generation, this architecture separates **reasoning** from **deterministic processing**.

```text
Natural Language Requirement
            │
            ▼
     Requirement Analysis (AI)
            │
            ▼
         WFM Generator (AI)
            │
            ▼
        WFM Validation
            │
            ▼
        Rule-based Engine
      ┌────────┴─────────┐
      ▼                  ▼
 Flowchart          Test Cases
```

The LLM is only responsible for semantic understanding.

Everything after WFM should be deterministic.

---

# 2. Design Goals

The architecture is designed around several principles.

## 2.1 AI only understands

AI is responsible for understanding business requirements.

Examples:

- actors
- workflow
- business rules
- validations
- edge cases
- assumptions
- ambiguities

AI should **not** generate final artifacts directly.

---

## 2.2 WFM is the Single Source of Truth

Everything should originate from WFM.

```text
Requirement

↓

Requirement Analysis

↓

WFM

↓

Flowchart
Test Cases
Documentation
Automation
Simulation
```

Nothing should bypass WFM.

---

## 2.3 Deterministic Outputs

Flowchart generation must not call AI.

Test case generation must not call AI.

This guarantees:

- reproducible output
- lower cost
- easier debugging
- easier testing

---

## 2.4 Extensible

The architecture should support future outputs without modifying AI prompts.

Example:

```text
Requirement

↓

WFM

├── Flowchart

├── Test Cases

├── BPMN

├── PlantUML

├── Mermaid

├── Sequence Diagram

├── User Story

├── API Contract

└── Automation Script
```

---

# 3. Pipeline

## Step 1

Input

Natural language requirement.

Example

```
User can create a purchase request.

Manager approves.

If amount > 5000,
Finance approval is required.
```

---

## Step 2

Requirement Analysis (AI)

Purpose

Extract semantic information.

Output

```json
{
  "summary": "...",
  "actors": [],
  "modules": [],
  "businessRules": [],
  "validations": [],
  "edgeCases": [],
  "assumptions": [],
  "openQuestions": []
}
```

Responsibilities

- understand business
- identify actors
- identify validations
- identify workflow
- identify ambiguities

---

## Step 3

Generate WFM (AI)

Purpose

Transform semantic analysis into Workflow Model.

Output

```json
{
  "workflowName": "...",
  "nodes": [],
  "edges": []
}
```

Responsibilities

- create workflow graph
- create decisions
- create approvals
- preserve business rules

---

## Step 4

Validate WFM

No AI.

Validation includes

- duplicate node IDs
- invalid edges
- missing start node
- missing end node
- orphan nodes
- invalid references

AI-generated WFM is treated as a draft. The backend must not pass raw AI WFM directly to downstream generators.

Current deterministic quality pipeline:

```text
Requirement Analysis JSON

↓

WFM Generator (AI draft)

↓

Structural WFM Validation

↓

Semantic WFM Validation

↓

WFM Normalization / Repair

↓

Structural WFM Validation

↓

Semantic WFM Validation

↓

Normalized WFM
```

The normalized WFM is the only WFM that should be used by flowchart generation, test case generation, or any future downstream output.

### Approval And Decision Semantics

Approval nodes and decision nodes have different responsibilities.

- Approval node = an actor approves or rejects.
- Decision node = business condition branching.

Approval nodes must not directly branch on numeric business conditions such as:

- `amount > 5000`
- `amount <= 5000`
- `totalAmount >= 10000`
- `quantity < 1`
- `price == 0`

Numeric condition edges must originate from decision nodes.

Canonical purchase approval pattern:

```text
Manager Approval

├── Reject → Request Rejected

└── Approve → Check Amount
                 ├── amount > 5000 → Finance Approval
                 └── amount <= 5000 → Request Approved
```

If AI produces this non-canonical pattern:

```text
Manager Approval

├── amount > 5000 → Finance Approval
├── amount <= 5000 → Request Approved
└── Rejected → Request Rejected
```

The WFM normalizer repairs it by inserting a decision node after the approval node and moving the numeric condition edges to that decision node.

If validation fails

↓

reject WFM

↓

regenerate (optional)

---

## Step 5

Generate Flowchart

Rule-based only.

Input

WFM

Output

Mermaid

Example

```text
flowchart TD

Start

↓

Create Request

↓

Manager Approval

↓

Decision

↓

Finance Approval
```

---

## Step 6

Generate Test Cases

Rule-based only.

Input

WFM

Output

Structured Test Cases

Example

```text
TC-001

Happy Path

TC-002

Manager Reject

TC-003

Finance Reject

TC-004

Amount > 5000

TC-005

Amount <= 5000
```

---

# 4. Why WFM?

Without WFM

```text
Requirement

↓

AI

↓

Flowchart

↓

AI

↓

Test Cases
```

Problems

- duplicated reasoning
- inconsistent output
- expensive
- hard to debug

---

With WFM

```text
Requirement

↓

AI

↓

WFM

↓

Flowchart

↓

Test Cases
```

Advantages

- AI reasons once
- deterministic outputs
- reusable
- traceable
- cheaper

---

# 5. WFM Design

WFM is **not** a flowchart.

WFM is an Abstract Syntax Tree (AST) for business workflows.

It contains only business meaning.

Example

```text
Start

↓

Action

↓

Approval

↓

Decision

↓

Approval

↓

End
```

Rendering is performed later.

---

## Nodes

Current node kinds

- start
- action
- decision
- approval
- end

Node kind is **String**, not enum.

Future examples

- loop
- timer
- webhook
- email
- script
- parallel
- gateway
- subprocess

No Java code changes should be required.

---

## Edges

Edges describe transitions.

```text
A

↓

B
```

Optional

- condition
- label
- metadata

---

# 6. Separation of Responsibility

Requirement Analysis

Responsible for

- understanding

Not responsible for

- graph structure

---

WFM Generator

Responsible for

- workflow graph

Not responsible for

- rendering

---

Flowchart Generator

Responsible for

- visualization

Not responsible for

- business reasoning

---

Test Case Generator

Responsible for

- workflow coverage

Not responsible for

- business understanding

---

# 7. Traceability

Every artifact must reference WFM.

Example

```text
Requirement

↓

WFM Node

↓

Flowchart Node

↓

Test Case
```

Example

```text
Node

manager_approval

↓

Flowchart

Manager Approval

↓

Test Case

TC-002
```

This enables:

- impact analysis
- debugging
- coverage calculation

---

# 8. AI Boundary

AI should only exist here

```text
Requirement

↓

Requirement Analysis

↓

WFM
```

Everything after that

↓

Rule Engine

No AI.

---

# 9. Future Roadmap

Current

```text
Requirement

↓

Analysis

↓

WFM

↓

Flowchart

↓

Test Cases
```

Future

```text
Requirement

↓

Analysis

↓

WFM

├── Mermaid

├── BPMN

├── PlantUML

├── Test Cases

├── User Stories

├── API Spec

├── Automation

├── State Machine

├── Sequence Diagram

├── Documentation

├── Risk Analysis

└── Impact Analysis
```

No additional AI prompts should be required.

---

# 10. Benefits

## Lower Cost

AI runs only twice.

Everything else is generated locally.

---

## Higher Consistency

Every artifact comes from the same WFM.

---

## Better Debugging

Errors can be isolated to:

- Requirement Analysis
- WFM
- Flowchart
- Test Cases

---

## Easier Testing

Rule-based generators can be unit tested.

---

## Extensible

Adding new outputs only requires implementing another deterministic generator.

No prompt changes are required.

---

# 11. Final Architecture

```text
                Natural Language Requirement
                           │
                           ▼
                Requirement Analysis (AI)
                           │
                           ▼
                   Workflow Model (WFM)
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
    Flowchart         Test Cases        Documentation
    Generator         Generator          Generator
         │                 │                 │
         ▼                 ▼                 ▼
     Mermaid          QA Suite         Markdown/PDF
```

**WFM is the canonical representation of business workflow.**
All downstream artifacts are generated deterministically from WFM.
