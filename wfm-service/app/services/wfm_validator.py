from typing import Any


class WfmValidationError(ValueError):
    def __init__(self, errors: list[str]) -> None:
        super().__init__("; ".join(errors))
        self.errors = errors


class WfmValidator:
    NODE_ROLES = {"START", "END", "ACTION", "DECISION", "INPUT", "OUTPUT", "ERROR", "SUBPROCESS"}
    TRANSITION_SEMANTICS = {"DEFAULT", "YES", "NO", "SUCCESS", "FAILURE", "CANCEL", "RETRY", "TIMEOUT"}
    UI_FIELDS = {
        "x",
        "y",
        "position",
        "color",
        "shape",
        "width",
        "height",
        "selected",
        "dragging",
        "sourceHandle",
        "targetHandle",
        "reactFlowType",
        "edgeLabel",
    }

    def validate(self, payload: dict[str, Any]) -> list[str]:
        errors: list[str] = []
        self._reject_ui_fields(payload, "$", errors)

        if payload.get("schemaVersion") != "1.0":
            errors.append("$.schemaVersion must be 1.0")
        if payload.get("modelType") != "WORKFLOW_AST":
            errors.append("$.modelType must be WORKFLOW_AST")

        workflow = payload.get("workflow")
        if not isinstance(workflow, dict):
            errors.append("$.workflow is required")
        else:
            if not self._has_text(workflow.get("id")):
                errors.append("$.workflow.id is required")
            if not self._has_text(workflow.get("title")):
                errors.append("$.workflow.title is required")

        ast = payload.get("ast")
        if not isinstance(ast, dict):
            errors.append("$.ast is required")
            self._raise_if_errors(errors)
            return []

        nodes = ast.get("nodes")
        transitions = ast.get("transitions")
        if not isinstance(nodes, list) or not nodes:
            errors.append("$.ast.nodes must not be empty")
            self._raise_if_errors(errors)
            return []
        if not isinstance(transitions, list):
            errors.append("$.ast.transitions must be an array")
            self._raise_if_errors(errors)
            return []

        node_ids = self._validate_nodes(nodes, errors)
        self._validate_transitions(transitions, node_ids, errors)
        self._raise_if_errors(errors)
        return []

    def _validate_nodes(self, nodes: list[Any], errors: list[str]) -> set[str]:
        node_ids: set[str] = set()
        start_count = 0
        end_count = 0

        for index, node in enumerate(nodes):
            path = f"$.ast.nodes[{index}]"
            if not isinstance(node, dict):
                errors.append(f"{path} must be an object")
                continue
            node_id = node.get("id")
            role = node.get("role")
            if not self._has_text(node_id):
                errors.append(f"{path}.id is required")
            elif node_id in node_ids:
                errors.append(f"{path}.id must be unique")
            else:
                node_ids.add(node_id)
            if role not in self.NODE_ROLES:
                errors.append(f"{path}.role is invalid")
            if role == "START":
                start_count += 1
            if role == "END":
                end_count += 1
            if not self._has_text(node.get("title")):
                errors.append(f"{path}.title is required")

        if start_count != 1:
            errors.append("$.ast.nodes must contain exactly one START node")
        if end_count < 1:
            errors.append("$.ast.nodes must contain at least one END node")
        return node_ids

    def _validate_transitions(self, transitions: list[Any], node_ids: set[str], errors: list[str]) -> None:
        transition_ids: set[str] = set()
        for index, transition in enumerate(transitions):
            path = f"$.ast.transitions[{index}]"
            if not isinstance(transition, dict):
                errors.append(f"{path} must be an object")
                continue
            transition_id = transition.get("id")
            if not self._has_text(transition_id):
                errors.append(f"{path}.id is required")
            elif transition_id in transition_ids:
                errors.append(f"{path}.id must be unique")
            else:
                transition_ids.add(transition_id)
            if transition.get("from") not in node_ids:
                errors.append(f"{path}.from must reference an existing node")
            if transition.get("to") not in node_ids:
                errors.append(f"{path}.to must reference an existing node")
            if transition.get("semantic") not in self.TRANSITION_SEMANTICS:
                errors.append(f"{path}.semantic is invalid")

    def _reject_ui_fields(self, value: Any, path: str, errors: list[str]) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                child_path = f"{path}.{key}"
                if key in self.UI_FIELDS:
                    errors.append(f"{child_path} is a UI-only field and is not allowed in WFM")
                self._reject_ui_fields(child, child_path, errors)
        elif isinstance(value, list):
            for index, child in enumerate(value):
                self._reject_ui_fields(child, f"{path}[{index}]", errors)

    def _has_text(self, value: Any) -> bool:
        return isinstance(value, str) and bool(value.strip())

    def _raise_if_errors(self, errors: list[str]) -> None:
        if errors:
            raise WfmValidationError(errors)
