# WFM v2 Specification

WFM v2 is the workflow AST used by the Python workflow engine to convert natural language requirements into deterministic downstream artifacts. It is a business workflow model, not a React Flow graph.

## Purpose

WFM v2 separates workflow meaning from rendering. AI, rule-based, and mock generators produce WFM v2 JSON. The workflow engine canonicalizes and validates that JSON, then deterministically maps it to the existing flowchart contract returned through Spring Boot.

## Differences From WFM v1

- WFM v1 used `schemaVersion`, `modelType`, `workflow`, `extensions`, and `ast`.
- WFM v2 uses a flatter AST with `wfmVersion`, `workflowId`, `workflowName`, `nodes`, `transitions`, and `metadata`.
- WFM v2 uses `kind` as a string-based node kind. It is not a closed enum.
- WFM v2 uses `source` and `target` for transitions instead of `from` and `to`.
- WFM v2 stores transition meaning in `label`, `condition`, and `outcome`.
- WFM v2 explicitly separates approval/review tasks from business decisions.

## Top-Level Shape

```json
{
  "wfmVersion": "2.0",
  "workflowId": "purchase_request_approval",
  "workflowName": "Purchase Request Approval",
  "description": "optional description",
  "direction": "LR",
  "nodes": [],
  "transitions": [],
  "metadata": {
    "source": "AI",
    "language": "en",
    "warnings": []
  }
}
```

WFM must not contain UI-only fields such as `x`, `y`, `position`, `color`, `shape`, `sourceHandle`, `targetHandle`, or `reactFlowType`.

## Node Shape

```json
{
  "id": "manager_approval",
  "kind": "APPROVAL",
  "name": "Manager Approval",
  "description": null,
  "actor": "Manager",
  "data": {}
}
```

`kind` is string-based. Known core kinds receive semantic validation. Unknown kinds are allowed only when they satisfy generic node rules.

Core kinds:

- `START`
- `END`
- `ACTION`
- `USER_TASK`
- `SYSTEM_TASK`
- `APPROVAL`
- `DECISION`
- `MERGE`
- `PARALLEL_SPLIT`
- `PARALLEL_JOIN`
- `WAIT`
- `NOTIFICATION`
- `ERROR`

## Transition Shape

```json
{
  "id": "t_amount_yes",
  "source": "amount_over_threshold",
  "target": "finance_approval",
  "label": "Yes",
  "condition": "Amount > 1000",
  "outcome": null,
  "data": {}
}
```

Transition labels and conditions are preserved by the WFM v2 to flowchart mapper.

## Validation Rules

- `wfmVersion` must be `2.0`.
- `workflowId` and `workflowName` are required.
- Node IDs must be unique.
- Transition IDs must be unique.
- Every transition source and target must reference an existing node.
- Exactly one `START` node is required.
- At least one `END` node is required.
- `START` has no incoming transitions and exactly one outgoing transition.
- `END` has at least one incoming transition and no outgoing transitions.
- Linear task kinds should have at most one outgoing transition.
- `DECISION` must have at least two outgoing transitions.
- Every outgoing `DECISION` transition must have `label`, `condition`, or `outcome`.
- `APPROVAL` must represent review/approval work only.
- Approval outcomes should be approval-related: `Approved`, `Rejected`, `Need Changes`, or `More Info Required`.
- Business conditions such as `amount > 1000` must be modeled with `DECISION`, not direct approval branches.
- Nodes must be reachable from `START` unless intentionally detached through metadata.
- Cycles should be explicitly marked with `data.loop = true`.

## Canonicalization Rules

- Missing workflow metadata is filled conservatively.
- IDs are normalized to safe stable snake_case.
- Missing transition IDs are generated as `t_1`, `t_2`, etc.
- Node kind casing is normalized to uppercase.
- Empty strings are normalized to `null` for optional text fields.
- Missing metadata is initialized.
- Canonicalization does not invent business logic.
- Ambiguous approval/business-condition mixing is rejected by validation.

## Example

```json
{
  "wfmVersion": "2.0",
  "workflowId": "purchase_request_approval",
  "workflowName": "Purchase Request Approval",
  "direction": "LR",
  "nodes": [
    {"id": "start", "kind": "START", "name": "Start", "data": {}},
    {"id": "create_purchase_request", "kind": "USER_TASK", "name": "Create Purchase Request", "actor": "User", "data": {}},
    {"id": "manager_approval", "kind": "APPROVAL", "name": "Manager Approval", "actor": "Manager", "data": {}},
    {"id": "manager_approved", "kind": "DECISION", "name": "Manager Approved?", "data": {}},
    {"id": "amount_over_threshold", "kind": "DECISION", "name": "Amount > 1000?", "data": {}},
    {"id": "finance_approval", "kind": "APPROVAL", "name": "Finance Approval", "actor": "Finance", "data": {}},
    {"id": "finance_approved", "kind": "DECISION", "name": "Finance Approved?", "data": {}},
    {"id": "request_approved", "kind": "END", "name": "Request Approved", "data": {}},
    {"id": "request_rejected", "kind": "END", "name": "Request Rejected", "data": {}}
  ],
  "transitions": [
    {"id": "t_start_create", "source": "start", "target": "create_purchase_request", "data": {}},
    {"id": "t_create_manager", "source": "create_purchase_request", "target": "manager_approval", "data": {}},
    {"id": "t_manager_reviewed", "source": "manager_approval", "target": "manager_approved", "data": {}},
    {"id": "t_manager_rejected", "source": "manager_approved", "target": "request_rejected", "label": "Rejected", "outcome": "Rejected", "data": {}},
    {"id": "t_manager_approved", "source": "manager_approved", "target": "amount_over_threshold", "label": "Approved", "outcome": "Approved", "data": {}},
    {"id": "t_amount_yes", "source": "amount_over_threshold", "target": "finance_approval", "label": "Yes", "condition": "Amount > 1000", "data": {}},
    {"id": "t_amount_no", "source": "amount_over_threshold", "target": "request_approved", "label": "No", "condition": "Amount <= 1000", "data": {}},
    {"id": "t_finance_reviewed", "source": "finance_approval", "target": "finance_approved", "data": {}},
    {"id": "t_finance_approved", "source": "finance_approved", "target": "request_approved", "label": "Approved", "outcome": "Approved", "data": {}},
    {"id": "t_finance_rejected", "source": "finance_approved", "target": "request_rejected", "label": "Rejected", "outcome": "Rejected", "data": {}}
  ],
  "metadata": {"source": "MOCK", "language": "en", "warnings": []}
}
```

## Compatibility

WFM v1 remains available for compatibility. The Python endpoint `/internal/workflow/generate` accepts `options.wfmVersion` to choose `1.0` or `2.0`. Spring Boot requests WFM v2 by default and continues returning the public shape:

```json
{
  "wfm": {},
  "flowchart": {},
  "metadata": {}
}
```

React Flow remains a renderer. WFM v2 never stores layout, coordinates, handles, colors, or UI state.
