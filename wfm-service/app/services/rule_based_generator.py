import re
from typing import Any


class RuleBasedGenerator:
    def generate(self, requirement: str) -> dict[str, Any]:
        title = self._title(requirement)
        actions = self._actions(requirement, title)

        nodes: list[dict[str, Any]] = [
            {"id": "N1", "role": "START", "kind": "START", "title": "Start"},
        ]
        transitions: list[dict[str, Any]] = []
        previous = "N1"
        node_index = 2
        transition_index = 1

        if not actions:
            actions = [title]

        for action in actions:
            condition, outcome = self._split_condition(action)
            if condition:
                decision_id = f"N{node_index}"
                node_index += 1
                nodes.append(
                    {
                        "id": decision_id,
                        "role": "DECISION",
                        "kind": "DECISION",
                        "title": condition,
                    }
                )
                transitions.append(self._transition(transition_index, previous, decision_id, "DEFAULT"))
                transition_index += 1
                previous = decision_id

                if outcome:
                    action_id = f"N{node_index}"
                    node_index += 1
                    nodes.append(
                        {
                            "id": action_id,
                            "role": self._node_role(outcome),
                            "kind": self._node_role(outcome),
                            "title": outcome,
                        }
                    )
                    transitions.append(self._transition(transition_index, previous, action_id, "YES", condition))
                    transition_index += 1
                    previous = action_id
                continue

            action_id = f"N{node_index}"
            node_index += 1
            nodes.append(
                {
                    "id": action_id,
                    "role": self._node_role(action),
                    "kind": self._node_role(action),
                    "title": action,
                }
            )
            transitions.append(self._transition(transition_index, previous, action_id, "DEFAULT"))
            transition_index += 1
            previous = action_id

        end_id = f"N{node_index}"
        nodes.append({"id": end_id, "role": "END", "kind": "END", "title": "End"})
        transitions.append(self._transition(transition_index, previous, end_id, "DEFAULT"))

        return {
            "schemaVersion": "1.0",
            "modelType": "WORKFLOW_AST",
            "workflow": {
                "id": self._slug(title),
                "title": title,
                "language": "unknown",
                "sourceRequirement": requirement,
            },
            "extensions": {"nodeKinds": [], "transitionKinds": []},
            "ast": {
                "actors": [],
                "variables": [],
                "nodes": nodes,
                "transitions": transitions,
                "annotations": [],
            },
        }

    def _transition(
        self,
        index: int,
        source: str,
        target: str,
        semantic: str,
        condition: str | None = None,
    ) -> dict[str, Any]:
        transition: dict[str, Any] = {
            "id": f"T{index}",
            "from": source,
            "to": target,
            "semantic": semantic,
        }
        if condition:
            transition["condition"] = condition
        return transition

    def _actions(self, requirement: str, title: str) -> list[str]:
        parts = re.split(r"\n+|(?<=[.!?])\s+", requirement)
        actions: list[str] = []
        for part in parts:
            line = part.strip().strip("-* ")
            if not line or line == title or line.lower().startswith("feature:"):
                continue
            normalized_line = line.rstrip(".:;")
            if normalized_line.lower() in {"start", "end", "bắt đầu", "ket thuc", "kết thúc"}:
                continue
            actions.append(normalized_line)
        return actions

    def _split_condition(self, text: str) -> tuple[str | None, str | None]:
        match = re.match(r"^(?:if|when|nếu|khi)\s+(.+?)(?:,\s*|\s+then\s+|\s+thì\s+)(.+)$", text, re.I)
        if not match:
            return None, None
        return match.group(1).strip().rstrip(":"), match.group(2).strip().rstrip(".")

    def _node_role(self, text: str) -> str:
        normalized = text.lower()
        if any(keyword in normalized for keyword in ["input", "upload", "enter", "nhập", "submit"]):
            return "INPUT"
        if any(keyword in normalized for keyword in ["error", "fail", "invalid", "lỗi"]):
            return "ERROR"
        if any(keyword in normalized for keyword in ["show", "display", "message", "return", "redirect"]):
            return "OUTPUT"
        return "ACTION"

    def _title(self, requirement: str) -> str:
        for raw_line in requirement.splitlines():
            line = raw_line.strip().strip("-* ")
            if line:
                return line.removeprefix("Feature:").strip() or "Workflow"
        return "Workflow"

    def _slug(self, value: str) -> str:
        slug = "".join(char.lower() if char.isalnum() else "-" for char in value).strip("-")
        return "-".join(part for part in slug.split("-") if part) or "workflow"
