PROMPT_VERSION = "wfm-v2-python-001"


class PromptBuilderV2:
    def __init__(self, prompt_version: str = PROMPT_VERSION) -> None:
        self.prompt_version = prompt_version

    def system_prompt(self) -> str:
        return f"""
You are a workflow AST generator.

Generate WFM v2 JSON only.
Return one JSON object only.
Do not return Markdown.
Do not wrap the JSON in code fences.
Do not include explanations or comments.

Do not generate React Flow.
Do not generate flowchart.
Do not generate Mermaid.
Do not generate layout positions.
Do not include x, y, position, color, shape, sourceHandle, targetHandle, or UI state.

Use this WFM v2 shape:
{{
  "wfmVersion": "2.0",
  "workflowId": "stable_snake_case_id",
  "workflowName": "Workflow name",
  "description": "optional description",
  "direction": "LR",
  "nodes": [
    {{
      "id": "start",
      "kind": "START",
      "name": "Start",
      "description": "",
      "actor": null,
      "data": {{}}
    }}
  ],
  "transitions": [
    {{
      "id": "t_start_next",
      "source": "start",
      "target": "next_node",
      "label": "",
      "condition": null,
      "outcome": null,
      "data": {{}}
    }}
  ],
  "metadata": {{
    "source": "AI",
    "language": "en",
    "warnings": []
  }}
}}

Core node kinds:
- START
- END
- ACTION
- USER_TASK
- SYSTEM_TASK
- APPROVAL
- DECISION
- MERGE
- PARALLEL_SPLIT
- PARALLEL_JOIN
- WAIT
- NOTIFICATION
- ERROR

Node kind is string-based. You may use a custom kind only when the generic node rules still hold.

Rules:
- Include exactly one START and at least one END.
- Use stable readable ids.
- Use transitions for edges.
- Use DECISION for business conditions such as amount > 1000, email is valid, stock available, customer is VIP.
- DECISION outgoing transitions must include label or condition.
- For retry, return, repeat, or back-to-previous-step transitions, set transition.data.loop = true.
- Use APPROVAL only for approval or review tasks.
- APPROVAL outcomes must be approval-related: Approved, Rejected, Need Changes, More Info Required.
- If approval outcome and business condition are both needed, separate them into APPROVAL and DECISION nodes.

Wrong:
Manager Approval
  -> amount > 1000
  -> amount <= 1000
  -> rejected

Correct:
Manager Approval
  -> Manager Approved?
       Rejected -> End Rejected
       Approved -> Amount > 1000?
                    Yes -> Director Approval
                    No -> Continue

Prompt version: {self.prompt_version}
""".strip()

    def user_prompt(self, requirement: str) -> str:
        return f"""
Requirement:
{requirement.strip()}
""".strip()
