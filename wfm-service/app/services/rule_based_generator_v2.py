import re
from typing import Any


class RuleBasedGeneratorV2:
    def generate(self, requirement: str, source: str = "RULE_BASED") -> dict[str, Any]:
        lowered = requirement.lower()
        if "purchase request" in lowered or "purchase" in lowered:
            return self._purchase_request(requirement, source)
        return self._linear_workflow(requirement, source)

    def mock(self) -> dict[str, Any]:
        return self._purchase_request(
            "The user creates a purchase request. Manager approves. If amount > 5000, finance approval is required.",
            "MOCK",
        )

    def _purchase_request(self, requirement: str, source: str) -> dict[str, Any]:
        threshold = self._amount_threshold(requirement) or "1000"
        return {
            "wfmVersion": "2.0",
            "workflowId": "purchase_request_approval",
            "workflowName": "Purchase Request Approval",
            "description": "Purchase request approval with manager and finance review.",
            "direction": "LR",
            "nodes": [
                self._node("start", "START", "Start"),
                self._node("create_purchase_request", "USER_TASK", "Create Purchase Request", "User"),
                self._node("manager_approval", "APPROVAL", "Manager Approval", "Manager"),
                self._node("manager_approved", "DECISION", "Manager Approved?"),
                self._node("amount_over_threshold", "DECISION", f"Amount > {threshold}?"),
                self._node("finance_approval", "APPROVAL", "Finance Approval", "Finance"),
                self._node("finance_approved", "DECISION", "Finance Approved?"),
                self._node("request_approved", "END", "Request Approved"),
                self._node("request_rejected", "END", "Request Rejected"),
            ],
            "transitions": [
                self._transition("t_start_create", "start", "create_purchase_request"),
                self._transition("t_create_manager", "create_purchase_request", "manager_approval"),
                self._transition("t_manager_reviewed", "manager_approval", "manager_approved"),
                self._transition(
                    "t_manager_rejected",
                    "manager_approved",
                    "request_rejected",
                    label="Rejected",
                    outcome="Rejected",
                ),
                self._transition(
                    "t_manager_approved",
                    "manager_approved",
                    "amount_over_threshold",
                    label="Approved",
                    outcome="Approved",
                ),
                self._transition(
                    "t_amount_yes",
                    "amount_over_threshold",
                    "finance_approval",
                    label="Yes",
                    condition=f"Amount > {threshold}",
                ),
                self._transition(
                    "t_amount_no",
                    "amount_over_threshold",
                    "request_approved",
                    label="No",
                    condition=f"Amount <= {threshold}",
                ),
                self._transition("t_finance_reviewed", "finance_approval", "finance_approved"),
                self._transition(
                    "t_finance_approved",
                    "finance_approved",
                    "request_approved",
                    label="Approved",
                    outcome="Approved",
                ),
                self._transition(
                    "t_finance_rejected",
                    "finance_approved",
                    "request_rejected",
                    label="Rejected",
                    outcome="Rejected",
                ),
            ],
            "metadata": {"source": source, "language": "unknown", "warnings": []},
        }

    def _linear_workflow(self, requirement: str, source: str) -> dict[str, Any]:
        title = self._title(requirement)
        actions = self._actions(requirement, title)
        if not actions:
            actions = [title]

        nodes: list[dict[str, Any]] = [self._node("start", "START", "Start")]
        transitions: list[dict[str, Any]] = []
        previous = "start"
        for index, action in enumerate(actions, start=1):
            node_id = self._slug(action) or f"action_{index}"
            kind = self._kind(action)
            nodes.append(self._node(node_id, kind, action))
            transitions.append(self._transition(f"t_{previous}_{node_id}", previous, node_id))
            previous = node_id
        nodes.append(self._node("end", "END", "End"))
        transitions.append(self._transition(f"t_{previous}_end", previous, "end"))

        return {
            "wfmVersion": "2.0",
            "workflowId": self._slug(title),
            "workflowName": title,
            "description": None,
            "direction": "LR",
            "nodes": nodes,
            "transitions": transitions,
            "metadata": {"source": source, "language": "unknown", "warnings": []},
        }

    def _node(self, node_id: str, kind: str, name: str, actor: str | None = None) -> dict[str, Any]:
        return {
            "id": node_id,
            "kind": kind,
            "name": name,
            "description": None,
            "actor": actor,
            "data": {},
        }

    def _transition(
        self,
        transition_id: str,
        source: str,
        target: str,
        label: str | None = None,
        condition: str | None = None,
        outcome: str | None = None,
    ) -> dict[str, Any]:
        return {
            "id": transition_id,
            "source": source,
            "target": target,
            "label": label,
            "condition": condition,
            "outcome": outcome,
            "data": {},
        }

    def _amount_threshold(self, requirement: str) -> str | None:
        match = re.search(r"(?:amount|total|price)\s*(?:>|greater than|over)\s*\$?([0-9][0-9,]*)", requirement, re.I)
        if match:
            return match.group(1).replace(",", "")
        return None

    def _actions(self, requirement: str, title: str) -> list[str]:
        parts = re.split(r"\n+|(?<=[.!?])\s+", requirement)
        actions: list[str] = []
        for part in parts:
            line = part.strip().strip("-* ")
            if not line or line == title or line.lower().startswith("feature:"):
                continue
            normalized = line.rstrip(".:;")
            if normalized.lower() in {"start", "end", "bắt đầu", "kết thúc"}:
                continue
            actions.append(normalized)
        return actions

    def _kind(self, value: str) -> str:
        normalized = value.lower()
        if "approve" in normalized or "review" in normalized:
            return "APPROVAL"
        if any(keyword in normalized for keyword in ["if ", "whether", "valid", "invalid", "available"]):
            return "DECISION"
        if any(keyword in normalized for keyword in ["notify", "email", "message"]):
            return "NOTIFICATION"
        if any(keyword in normalized for keyword in ["wait", "delay"]):
            return "WAIT"
        if any(keyword in normalized for keyword in ["error", "fail", "exception"]):
            return "ERROR"
        if any(keyword in normalized for keyword in ["user", "enter", "submit", "create"]):
            return "USER_TASK"
        if any(keyword in normalized for keyword in ["system", "validate", "calculate"]):
            return "SYSTEM_TASK"
        return "ACTION"

    def _title(self, requirement: str) -> str:
        for raw_line in requirement.splitlines():
            line = raw_line.strip().strip("-* ")
            if line:
                return line.removeprefix("Feature:").strip() or "Workflow"
        return "Workflow"

    def _slug(self, value: str) -> str:
        slug = re.sub(r"[^a-zA-Z0-9]+", "_", value.strip().lower()).strip("_")
        return slug or "workflow"
