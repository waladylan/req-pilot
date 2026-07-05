# REQ-PILOT — Project Overview

> Version: draft  
> Scope: Requirement → WFM v2 → Flowchart → Test Cases  
> Architecture style: monorepo + Docker Compose + Spring Boot + Python workflow-engine + React Flow

---

## 1. Product Goal

REQ-PILOT helps teams convert natural language requirements into structured software artifacts.

```text
Natural Language Requirement
        |
        v
Workflow Model (WFM v2)
        |
        +--------------------+
        |                    |
        v                    v
Flowchart              Test Cases
```

Core idea:

```text
Requirement text is input.
WFM v2 is source of truth.
Flowchart is visual artifact.
Test Cases are QA artifact.
```

---

## 2. Product Structure

The product is organized around projects.  
Each project contains multiple requirements.  
Each requirement owns its generated artifacts.

```text
Project
 |
 +-- Requirement 1
 |    |
 |    +-- Requirement Text
 |    +-- WFM v2 JSON
 |    +-- Flowchart JSON
 |    +-- Test Cases JSON
 |    +-- Metadata JSON
 |
 +-- Requirement 2
 |    |
 |    +-- Requirement Text
 |    +-- WFM v2 JSON
 |    +-- Flowchart JSON
 |    +-- Test Cases JSON
 |    +-- Metadata JSON
 |
 +-- Requirement N
      |
      +-- Requirement Text
      +-- WFM v2 JSON
      +-- Flowchart JSON
      +-- Test Cases JSON
      +-- Metadata JSON
```

Storage rule:

```text
Project
  -> Requirements
       -> requirementText
       -> wfmVersion = "2.0"
       -> wfmJson
       -> flowchartJson
       -> metadataJson
       -> testCasesJson
       -> testCaseMetadataJson
```

---

## 3. System Architecture

```text
User Browser
    |
    v
React Frontend
    |
    |  HTTP /api
    v
Spring Boot Backend
    |
    |  internal HTTP
    v
Python Workflow Engine
    |
    +-- WFM v2 Generator
    +-- WFM v2 Canonicalizer
    +-- WFM v2 Validator
    +-- WFM v2 -> Flowchart Mapper
    +-- WFM v2 -> Test Case Generator
    |
    v
LLM Provider
```

Persistence path:

```text
Spring Boot Backend
    |
    v
PostgreSQL
    |
    +-- projects
    +-- requirements
```

Important boundary:

```text
Frontend
  -> calls Spring Boot only

Spring Boot
  -> public API
  -> project / requirement persistence
  -> calls Python workflow-engine
  -> does not call LLM
  -> does not generate WFM
  -> does not generate Flowchart
  -> does not generate Test Cases

Python workflow-engine
  -> owns WFM v2 generation
  -> owns Flowchart generation from WFM v2
  -> owns Test Case generation from WFM v2
```

---

## 4. Artifact Generation Flow

### 4.1 Requirement → WFM v2 → Flowchart

```text
Requirement Text
        |
        v
Spring Boot API
        |
        v
Python Workflow Engine
        |
        +-- Requirement Analysis
        |
        v
WFM v2 Generator
        |
        v
WFM v2 Canonicalizer
        |
        v
WFM v2 Validator
        |
        v
WFM v2 -> Flowchart Mapper
        |
        v
Return { wfm, flowchart, metadata }
        |
        v
Spring Boot saves artifacts into Requirement
        |
        v
Frontend renders Flowchart
```

### 4.2 WFM v2 → Test Cases

```text
Saved Requirement
        |
        v
Load WFM v2 JSON
        |
        v
Spring Boot API
        |
        v
Python Workflow Engine
        |
        v
WFM v2 Test Case Generator
        |
        +-- Build graph from nodes/transitions
        +-- Enumerate meaningful paths
        +-- Cover decision branches
        +-- Cover approval outcomes
        +-- Generate negative/error paths
        |
        v
Return { testCaseSet, metadata }
        |
        v
Spring Boot saves Test Cases into Requirement
        |
        v
Frontend displays Test Cases
```

---

## 5. WFM v2 Concept

WFM v2 is a workflow AST.

```text
WFM v2
 |
 +-- workflowId
 +-- workflowName
 +-- wfmVersion = "2.0"
 +-- direction
 +-- nodes
 |    |
 |    +-- START
 |    +-- ACTION
 |    +-- USER_TASK
 |    +-- SYSTEM_TASK
 |    +-- APPROVAL
 |    +-- DECISION
 |    +-- MERGE
 |    +-- PARALLEL_SPLIT
 |    +-- PARALLEL_JOIN
 |    +-- NOTIFICATION
 |    +-- WAIT
 |    +-- ERROR
 |    +-- END
 |
 +-- transitions
      |
      +-- source
      +-- target
      +-- label
      +-- condition
      +-- outcome
```

Key modeling rule:

```text
Approval node
    |
    v
Approval outcome decision
    |
    +-- Approved
    +-- Rejected

Business condition decision
    |
    +-- amount > 1000
    +-- amount <= 1000
```

Wrong:

```text
Manager Approval
    |
    +-- amount > 1000
    +-- amount <= 1000
    +-- rejected
```

Correct:

```text
Manager Approval
        |
        v
Manager Approved?
        |
        +-- Rejected -> Request Rejected
        |
        +-- Approved -> Amount > 1000?
                            |
                            +-- Yes -> Finance Approval
                            |
                            +-- No  -> Request Approved
```

---

## 6. Example: Purchase Request Workflow

```text
Create Purchase Request
        |
        v
Manager Approval
        |
        v
Manager Approved?
        |
        +-------------------------+
        |                         |
        v                         v
Manager rejects              Manager approves
        |                         |
        v                         v
Request Rejected         Check Amount
                              |
                              +--------------------------+
                              |                          |
                              v                          v
                        amount > 5000             amount <= 5000
                              |                          |
                              v                          v
                       Finance Approval          Request Approved
                              |
                              v
                       Finance Approved?
                              |
                    +---------+---------+
                    |                   |
                    v                   v
             Finance approves     Finance rejects
                    |                   |
                    v                   v
             Request Approved    Request Rejected
```

Expected test cases from this WFM v2:

```text
Test Cases
 |
 +-- TC-001: Manager rejects purchase request
 |
 +-- TC-002: Manager approves and amount <= 5000
 |
 +-- TC-003: Manager approves, amount > 5000, finance approves
 |
 +-- TC-004: Manager approves, amount > 5000, finance rejects
```

---

## 7. Repository Structure

```text
REQ-PILOT
 |
 +-- frontend
 |    |
 |    +-- React + TypeScript + Vite
 |    +-- React Flow canvas
 |    +-- Project / Requirement UI
 |    +-- Node Inspector
 |    +-- Edge Inspector
 |    +-- Test Cases panel
 |
 +-- backend
 |    |
 |    +-- Spring Boot API
 |    +-- Project APIs
 |    +-- Requirement APIs
 |    +-- Artifact persistence
 |    +-- Python workflow-engine client
 |
 +-- wfm-service
 |    |
 |    +-- FastAPI app
 |    +-- WFM v2 generator
 |    +-- WFM v2 validator
 |    +-- WFM v2 canonicalizer
 |    +-- WFM v2 -> Flowchart mapper
 |    +-- WFM v2 -> Test Case generator
 |
 +-- docs
 |    |
 |    +-- architecture docs
 |    +-- WFM v2 spec
 |    +-- deployment notes
 |
 +-- scripts
 |    |
 |    +-- deploy scripts
 |    +-- smoke test scripts
 |
 +-- docker-compose.yml
 +-- docker-compose.prod.yml
 +-- .env.example
 +-- README.md
```

---

## 8. Backend API Overview

```text
Project APIs
 |
 +-- GET    /api/projects
 +-- POST   /api/projects
 +-- GET    /api/projects/{projectId}
 +-- PUT    /api/projects/{projectId}
 +-- DELETE /api/projects/{projectId}

Requirement APIs
 |
 +-- GET    /api/projects/{projectId}/requirements
 +-- POST   /api/projects/{projectId}/requirements
 +-- GET    /api/requirements/{requirementId}
 +-- PUT    /api/requirements/{requirementId}
 +-- DELETE /api/requirements/{requirementId}

Artifact APIs
 |
 +-- POST /api/requirements/{requirementId}/generate-flow
 +-- POST /api/requirements/{requirementId}/generate-test-cases
 +-- GET  /api/requirements/{requirementId}/test-cases

Legacy API
 |
 +-- POST /api/requirements/generate-flow
```

---

## 9. Python Workflow Engine Internal APIs

```text
Python Workflow Engine
 |
 +-- POST /internal/workflow/generate
 |     |
 |     +-- input: requirement text
 |     +-- output: { wfm, flowchart, metadata }
 |
 +-- POST /internal/test-cases/generate
 |     |
 |     +-- input: WFM v2
 |     +-- output: { testCaseSet, metadata }
 |
 +-- GET /health
```

---

## 10. Frontend Editing Rules

```text
Requirement Text
        |
        | Generate
        v
Flowchart
```

Allowed:

```text
Edit Requirement Text
        |
        v
Generate Flowchart

Edit Flowchart Node
        |
        v
Update Flowchart only

Edit Flowchart Edge Label
        |
        v
Update Flowchart only
```

Not allowed:

```text
Flowchart edit
        |
        v
Requirement Text auto-update
```

Source of truth rules:

```text
Requirement text
  -> source input for generation

WFM v2
  -> source of truth for generated artifacts

Flowchart
  -> editable visual artifact

Test Cases
  -> generated QA artifact from WFM v2
```

---

## 11. Deployment Architecture

Target deployment for MVP:

```text
Internet
    |
    v
Nginx
    |
    +-- /        -> React static frontend
    |
    +-- /api/**  -> Spring Boot backend
                    |
                    +-- PostgreSQL
                    |
                    +-- Python workflow-engine
```

Docker Compose services:

```text
Docker Host
 |
 +-- frontend-nginx
 |    |
 |    +-- public: 80 / 443
 |
 +-- backend
 |    |
 |    +-- internal: 8080
 |    +-- calls workflow-engine:8001
 |    +-- calls postgres:5432
 |
 +-- workflow-engine
 |    |
 |    +-- internal: 8001
 |    +-- calls LLM provider
 |
 +-- postgres
      |
      +-- internal: 5432
      +-- persistent volume
```

Public exposure rule:

```text
Public:
  - 80
  - 443

Private/internal only:
  - backend:8080
  - workflow-engine:8001
  - postgres:5432
```

---

## 12. MVP Roadmap

```text
Phase 1
 |
 +-- Requirement input
 +-- WFM v2 generation
 +-- Flowchart rendering

Phase 2
 |
 +-- Project -> Requirements structure
 +-- Artifact persistence
 +-- Node/edge editing

Phase 3
 |
 +-- Test Case generation from WFM v2
 +-- Test Case persistence
 +-- Test Case UI

Phase 4
 |
 +-- Deployment
 +-- Domain
 +-- HTTPS
 +-- Backup

Phase 5
 |
 +-- Better WFM v2 quality
 +-- Better layout
 +-- Export
 +-- Collaboration
```

---

## 13. Current Risks

```text
Risk
 |
 +-- WFM v2 quality depends on prompt and validator
 |
 +-- Flowchart layout may still need refinement for complex branches
 |
 +-- Test case generation currently path-based
 |
 +-- Loop handling is limited
 |
 +-- PostgreSQL in Docker is acceptable for MVP but needs backup
 |
 +-- Python workflow-engine should not be exposed publicly
```

---

## 14. Smoke Test Checklist

```text
Deploy App
 |
 +-- Open frontend
 |
 +-- Create Project
 |
 +-- Create Requirement
 |
 +-- Enter Requirement Text
 |
 +-- Generate Flow
 |    |
 |    +-- response has WFM v2
 |    +-- response has Flowchart
 |    +-- flowchart renders
 |
 +-- Edit Node
 |    |
 |    +-- node title updates
 |    +-- requirement text unchanged
 |
 +-- Edit Edge Label
 |    |
 |    +-- edge label updates
 |    +-- requirement text unchanged
 |
 +-- Generate Test Cases
 |    |
 |    +-- test cases generated from WFM v2
 |    +-- test cases saved under selected Requirement
 |
 +-- Refresh Browser
      |
      +-- Project persists
      +-- Requirement persists
      +-- WFM persists
      +-- Flowchart persists
      +-- Test Cases persist
```
