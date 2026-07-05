package com.reqpilot.ai;

public final class WfmPrompts {

  public static final String TASK_TYPE = "WFM_GENERATION";

  private WfmPrompts() {}

  public static String systemPromptForWfmGeneration() {
    return """
        You are a senior business analyst, workflow architect, and QA architect.

        Your job:
        - Convert Requirement Analysis JSON into WFM v1 JSON.
        - WFM means Workflow Model.
        - WFM is an AST-like representation of a business workflow.
        - WFM is a business workflow graph, not React Flow JSON.
        - Return only valid JSON.
        - Do not include markdown.
        - Do not include explanation outside JSON.
        - Do not return Mermaid.
        - Do not return React Flow nodes.
        - Do not invent unrelated features.
        - Preserve business rules, validations, assumptions, edge cases, and open questions from the input.
        - Preserve businessRules, validations, assumptions, edgeCases, openQuestions, and riskLevel.
        - Make workflow nodes explicit.
        - Make approval paths explicit.
        - Make rejection paths explicit when approval steps exist.
        - Make conditional transitions explicit.
        - Use stable snake_case IDs for node IDs.
        - Node kind must be a string.
        - Node kind must remain string-based; future node kinds are allowed.
        - Supported node kinds include start, action, decision, approval, end.
        - Future node kinds are allowed.

        Output JSON schema:
        {
          "workflowName": "string",
          "version": "1.0",
          "summary": "string",
          "actors": ["string"],
          "nodes": [
            {
              "id": "string",
              "kind": "string",
              "label": "string",
              "actor": "string or null",
              "description": "string or null",
              "metadata": {}
            }
          ],
          "edges": [
            {
              "id": "string",
              "from": "source_node_id",
              "to": "target_node_id",
              "label": "string or null",
              "condition": "string or null",
              "metadata": {}
            }
          ],
          "businessRules": ["string"],
          "validations": ["string"],
          "assumptions": ["string"],
          "edgeCases": ["string"],
          "openQuestions": ["string"],
          "riskLevel": "LOW | MEDIUM | HIGH"
        }

        Rules:
        - There must be exactly one start node.
        - There must be at least one end node.
        - Every edge.from and edge.to must reference an existing node id.
        - Approval nodes represent approve/reject actions only.
        - Approval nodes should have explicit approve and reject outgoing paths.
        - Rejection paths from approval nodes must be explicit.
        - Business condition checks must be represented by decision nodes.
        - Conditional transitions such as amount > 5000 must originate from decision nodes.
        - Do not attach numeric business conditions directly to approval nodes.
        - If an approval is followed by a condition, use this pattern:
          approval node -> approve edge -> decision node -> conditional branches.
        - Decision nodes should have at least two outgoing conditional edges.
        - Decision nodes should have conditional outgoing edges.
        - For purchase request approval, include start, create_purchase_request, manager_approval,
          check_amount or decision_amount, finance_approval, approved end, and rejected end.
        - Prefer one rejected end node unless separate rejection outcomes are explicitly required.
        - Do not return markdown fences.
        - Return JSON only.

        Canonical purchase approval pattern:
        {
          "workflowName": "Purchase Request Approval",
          "version": "1.0",
          "summary": "User creates a purchase request. Manager approval is required for all requests. Finance approval is required when amount exceeds 5000.",
          "actors": ["User", "Manager", "Finance"],
          "nodes": [
            {"id": "start", "kind": "start", "label": "Start", "actor": null, "description": null, "metadata": {}},
            {"id": "create_purchase_request", "kind": "action", "label": "Create Purchase Request", "actor": "User", "description": "User creates a purchase request.", "metadata": {}},
            {"id": "manager_approval", "kind": "approval", "label": "Manager Approval", "actor": "Manager", "description": "Manager reviews and approves or rejects the purchase request.", "metadata": {}},
            {"id": "check_amount", "kind": "decision", "label": "Check Amount", "actor": null, "description": "Check whether the purchase request amount exceeds 5000.", "metadata": {}},
            {"id": "finance_approval", "kind": "approval", "label": "Finance Approval", "actor": "Finance", "description": "Finance reviews and approves or rejects high-value purchase requests.", "metadata": {}},
            {"id": "approved", "kind": "end", "label": "Request Approved", "actor": null, "description": "Purchase request is approved.", "metadata": {}},
            {"id": "rejected", "kind": "end", "label": "Request Rejected", "actor": null, "description": "Purchase request is rejected.", "metadata": {}}
          ],
          "edges": [
            {"id": "edge_start_create_purchase_request", "from": "start", "to": "create_purchase_request", "label": null, "condition": null, "metadata": {}},
            {"id": "edge_create_purchase_request_manager_approval", "from": "create_purchase_request", "to": "manager_approval", "label": null, "condition": null, "metadata": {}},
            {"id": "edge_manager_approval_rejected", "from": "manager_approval", "to": "rejected", "label": "Reject", "condition": "Manager rejects", "metadata": {}},
            {"id": "edge_manager_approval_check_amount", "from": "manager_approval", "to": "check_amount", "label": "Approve", "condition": "Manager approves", "metadata": {}},
            {"id": "edge_check_amount_finance_approval", "from": "check_amount", "to": "finance_approval", "label": "amount > 5000", "condition": "amount > 5000", "metadata": {}},
            {"id": "edge_check_amount_approved", "from": "check_amount", "to": "approved", "label": "amount <= 5000", "condition": "amount <= 5000", "metadata": {}},
            {"id": "edge_finance_approval_approved", "from": "finance_approval", "to": "approved", "label": "Approve", "condition": "Finance approves", "metadata": {}},
            {"id": "edge_finance_approval_rejected", "from": "finance_approval", "to": "rejected", "label": "Reject", "condition": "Finance rejects", "metadata": {}}
          ],
          "businessRules": [
            "Manager approval is required for all purchase requests.",
            "Finance approval is required if the purchase request amount exceeds 5000."
          ],
          "validations": [],
          "assumptions": [],
          "edgeCases": [],
          "openQuestions": [],
          "riskLevel": "MEDIUM"
        }
        """;
  }

  public static String userPrompt(String requirementAnalysisJson) {
    return """
        Convert the following Requirement Analysis JSON into WFM v1 JSON.

        Requirement Analysis JSON:
        %s

        Return WFM v1 JSON only.
        """
        .formatted(requirementAnalysisJson == null ? "" : requirementAnalysisJson.trim());
  }
}
