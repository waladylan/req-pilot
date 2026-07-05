from collections import defaultdict, deque
from typing import Any

from app.schemas.wfm_v2 import WfmValidationIssue, WfmValidationResult


class WfmV2ValidationError(ValueError):
    def __init__(self, result: WfmValidationResult) -> None:
        super().__init__("; ".join(issue.message for issue in result.errors))
        self.result = result


class WfmV2Validator:
    CORE_NODE_KINDS = {
        "START",
        "END",
        "ACTION",
        "USER_TASK",
        "SYSTEM_TASK",
        "APPROVAL",
        "DECISION",
        "MERGE",
        "PARALLEL_SPLIT",
        "PARALLEL_JOIN",
        "WAIT",
        "NOTIFICATION",
        "ERROR",
    }
    LINEAR_TASK_KINDS = {"ACTION", "USER_TASK", "SYSTEM_TASK", "NOTIFICATION", "WAIT"}
    APPROVAL_OUTCOMES = {
        "APPROVED",
        "REJECTED",
        "NEED CHANGES",
        "NEEDS CHANGES",
        "MORE INFO REQUIRED",
        "MORE INFORMATION REQUIRED",
    }
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
    BUSINESS_CONDITION_MARKERS = {
        ">",
        "<",
        ">=",
        "<=",
        "=",
        "amount",
        "total",
        "price",
        "budget",
        "valid",
        "invalid",
        "stock",
        "vip",
    }

    def validate(self, payload: dict[str, Any]) -> WfmValidationResult:
        errors: list[WfmValidationIssue] = []
        warnings: list[WfmValidationIssue] = []

        self._reject_ui_fields(payload, "$", errors)
        if payload.get("wfmVersion") != "2.0":
            self._error(errors, "INVALID_WFM_VERSION", "wfmVersion must be 2.0", "wfmVersion")
        if not self._has_text(payload.get("workflowId")):
            self._error(errors, "WORKFLOW_ID_REQUIRED", "workflowId is required", "workflowId")
        if not self._has_text(payload.get("workflowName")):
            self._error(errors, "WORKFLOW_NAME_REQUIRED", "workflowName is required", "workflowName")

        nodes = payload.get("nodes")
        transitions = payload.get("transitions")
        if not isinstance(nodes, list) or not nodes:
            self._error(errors, "NODES_REQUIRED", "nodes must not be empty", "nodes")
            return WfmValidationResult(valid=False, errors=errors, warnings=warnings)
        if not isinstance(transitions, list):
            self._error(errors, "TRANSITIONS_REQUIRED", "transitions must be an array", "transitions")
            return WfmValidationResult(valid=False, errors=errors, warnings=warnings)

        node_by_id = self._validate_nodes(nodes, errors, warnings)
        incoming, outgoing = self._validate_transitions(transitions, node_by_id, errors)
        if not errors:
            self._validate_semantics(nodes, transitions, incoming, outgoing, errors, warnings)
            self._validate_reachability(nodes, outgoing, errors, warnings)
            self._validate_cycles(nodes, outgoing, errors, warnings)
        return WfmValidationResult(valid=not errors, errors=errors, warnings=warnings)

    def validate_or_raise(self, payload: dict[str, Any]) -> WfmValidationResult:
        result = self.validate(payload)
        if not result.valid:
            raise WfmV2ValidationError(result)
        return result

    def _validate_nodes(
        self,
        nodes: list[Any],
        errors: list[WfmValidationIssue],
        warnings: list[WfmValidationIssue],
    ) -> dict[str, dict[str, Any]]:
        node_by_id: dict[str, dict[str, Any]] = {}
        start_count = 0
        end_count = 0

        for index, node in enumerate(nodes):
            path = f"nodes[{index}]"
            if not isinstance(node, dict):
                self._error(errors, "NODE_INVALID", "Node must be an object", path)
                continue
            node_id = node.get("id")
            kind = self._text(node.get("kind"))
            if not self._has_text(node_id):
                self._error(errors, "NODE_ID_REQUIRED", "Node id is required", f"{path}.id")
                continue
            if node_id in node_by_id:
                self._error(errors, "DUPLICATE_NODE_ID", f"Node id '{node_id}' must be unique", f"{path}.id")
            else:
                node_by_id[str(node_id)] = node
            if not self._has_text(kind):
                self._error(errors, "NODE_KIND_REQUIRED", "Node kind is required", f"{path}.kind")
            elif kind not in self.CORE_NODE_KINDS:
                self._warning(warnings, "UNKNOWN_NODE_KIND", f"Node kind '{kind}' is not a core kind", f"{path}.kind")
            if not self._has_text(node.get("name")):
                self._error(errors, "NODE_NAME_REQUIRED", "Node name is required", f"{path}.name")
            if kind == "START":
                start_count += 1
            if kind == "END":
                end_count += 1

        if start_count != 1:
            self._error(errors, "START_COUNT_INVALID", "There must be exactly one START node", "nodes")
        if end_count < 1:
            self._error(errors, "END_REQUIRED", "There must be at least one END node", "nodes")
        return node_by_id

    def _validate_transitions(
        self,
        transitions: list[Any],
        node_by_id: dict[str, dict[str, Any]],
        errors: list[WfmValidationIssue],
    ) -> tuple[dict[str, list[dict[str, Any]]], dict[str, list[dict[str, Any]]]]:
        transition_ids: set[str] = set()
        incoming: dict[str, list[dict[str, Any]]] = defaultdict(list)
        outgoing: dict[str, list[dict[str, Any]]] = defaultdict(list)

        for index, transition in enumerate(transitions):
            path = f"transitions[{index}]"
            if not isinstance(transition, dict):
                self._error(errors, "TRANSITION_INVALID", "Transition must be an object", path)
                continue
            transition_id = transition.get("id")
            if not self._has_text(transition_id):
                self._error(errors, "TRANSITION_ID_REQUIRED", "Transition id is required", f"{path}.id")
            elif transition_id in transition_ids:
                self._error(
                    errors,
                    "DUPLICATE_TRANSITION_ID",
                    f"Transition id '{transition_id}' must be unique",
                    f"{path}.id",
                )
            else:
                transition_ids.add(str(transition_id))

            source = transition.get("source")
            target = transition.get("target")
            if source not in node_by_id:
                self._error(
                    errors,
                    "TRANSITION_SOURCE_NOT_FOUND",
                    f"Transition source '{source}' must reference an existing node",
                    f"{path}.source",
                )
            if target not in node_by_id:
                self._error(
                    errors,
                    "TRANSITION_TARGET_NOT_FOUND",
                    f"Transition target '{target}' must reference an existing node",
                    f"{path}.target",
                )
            if source in node_by_id and target in node_by_id:
                outgoing[str(source)].append(transition)
                incoming[str(target)].append(transition)
        return incoming, outgoing

    def _validate_semantics(
        self,
        nodes: list[dict[str, Any]],
        transitions: list[dict[str, Any]],
        incoming: dict[str, list[dict[str, Any]]],
        outgoing: dict[str, list[dict[str, Any]]],
        errors: list[WfmValidationIssue],
        warnings: list[WfmValidationIssue],
    ) -> None:
        for index, node in enumerate(nodes):
            node_id = str(node.get("id"))
            kind = self._text(node.get("kind"))
            path = f"nodes[{index}]"
            in_count = len(incoming[node_id])
            out_count = len(outgoing[node_id])

            if kind == "START":
                if in_count:
                    self._error(errors, "START_HAS_INCOMING", "START must not have incoming transitions", path)
                if out_count != 1:
                    self._error(errors, "START_OUTGOING_INVALID", "START must have exactly one outgoing transition", path)
            elif kind == "END":
                if in_count < 1:
                    self._error(errors, "END_INCOMING_REQUIRED", "END must have at least one incoming transition", path)
                if out_count:
                    self._error(errors, "END_HAS_OUTGOING", "END must not have outgoing transitions", path)
            elif kind in self.LINEAR_TASK_KINDS:
                if out_count > 1:
                    self._error(
                        errors,
                        "LINEAR_TASK_OUTGOING_INVALID",
                        f"{kind} should have at most one outgoing transition",
                        path,
                    )
            elif kind == "DECISION":
                if out_count < 2:
                    self._error(errors, "DECISION_OUTGOING_INVALID", "DECISION must have at least two outgoing transitions", path)
                for transition in outgoing[node_id]:
                    if not self._transition_meaning(transition):
                        self._error(
                            errors,
                            "DECISION_TRANSITION_MEANING_REQUIRED",
                            "Every DECISION outgoing transition must have label, condition, or outcome",
                            self._transition_path(transitions, transition),
                        )
            elif kind == "APPROVAL":
                if not self._has_text(node.get("name")):
                    self._error(errors, "APPROVAL_NAME_REQUIRED", "APPROVAL must have a name", path)
                for transition in outgoing[node_id]:
                    meaning = self._transition_meaning(transition)
                    if meaning and self._looks_like_business_condition(meaning):
                        self._error(
                            errors,
                            "APPROVAL_MIXES_BUSINESS_CONDITION",
                            "APPROVAL outcomes must be approval-related; use a DECISION node for business conditions",
                            self._transition_path(transitions, transition),
                        )
                    elif meaning and meaning.upper() not in self.APPROVAL_OUTCOMES:
                        self._warning(
                            warnings,
                            "APPROVAL_OUTCOME_NON_STANDARD",
                            f"Approval outcome '{meaning}' is not a standard approval outcome",
                            self._transition_path(transitions, transition),
                        )
            elif kind == "MERGE":
                if in_count < 2:
                    self._warning(warnings, "MERGE_INCOMING_LOW", "MERGE should have multiple incoming transitions", path)
                if out_count != 1:
                    self._error(errors, "MERGE_OUTGOING_INVALID", "MERGE should have exactly one outgoing transition", path)
            elif kind == "PARALLEL_SPLIT":
                if in_count > 1:
                    self._warning(warnings, "PARALLEL_SPLIT_INCOMING_HIGH", "PARALLEL_SPLIT should have one incoming transition", path)
                if out_count < 2:
                    self._error(errors, "PARALLEL_SPLIT_OUTGOING_INVALID", "PARALLEL_SPLIT must have at least two outgoing transitions", path)
            elif kind == "PARALLEL_JOIN":
                if in_count < 2:
                    self._error(errors, "PARALLEL_JOIN_INCOMING_INVALID", "PARALLEL_JOIN must have at least two incoming transitions", path)
                if out_count != 1:
                    self._error(errors, "PARALLEL_JOIN_OUTGOING_INVALID", "PARALLEL_JOIN must have one outgoing transition", path)

    def _validate_reachability(
        self,
        nodes: list[dict[str, Any]],
        outgoing: dict[str, list[dict[str, Any]]],
        errors: list[WfmValidationIssue],
        warnings: list[WfmValidationIssue],
    ) -> None:
        start_nodes = [node for node in nodes if node.get("kind") == "START"]
        if len(start_nodes) != 1:
            return
        start_id = str(start_nodes[0].get("id"))
        reachable = self._reachable_from(start_id, outgoing)
        node_by_id = {str(node.get("id")): node for node in nodes}
        for index, node in enumerate(nodes):
            node_id = str(node.get("id"))
            if node_id not in reachable and not self._is_detached(node):
                self._error(
                    errors,
                    "NODE_UNREACHABLE",
                    f"Node '{node_id}' must be reachable from START",
                    f"nodes[{index}]",
                )

        terminal_kinds = {"END", "ERROR"}
        for index, node in enumerate(nodes):
            node_id = str(node.get("id"))
            if node_id not in reachable or node.get("kind") in terminal_kinds:
                continue
            downstream = self._reachable_from(node_id, outgoing)
            if not any(node_by_id[target].get("kind") in terminal_kinds for target in downstream if target in node_by_id):
                self._warning(
                    warnings,
                    "PATH_WITHOUT_TERMINAL",
                    f"Node '{node_id}' does not have a path to END or ERROR",
                    f"nodes[{index}]",
                )

    def _validate_cycles(
        self,
        nodes: list[dict[str, Any]],
        outgoing: dict[str, list[dict[str, Any]]],
        errors: list[WfmValidationIssue],
        warnings: list[WfmValidationIssue],
    ) -> None:
        node_ids = [str(node.get("id")) for node in nodes]
        visiting: set[str] = set()
        visited: set[str] = set()

        def visit(node_id: str, stack: list[str]) -> None:
            if node_id in visited:
                return
            if node_id in visiting:
                cycle = [*stack[stack.index(node_id) :], node_id] if node_id in stack else [node_id]
                if not self._cycle_allows_loop(cycle, outgoing):
                    self._error(
                        errors,
                        "CYCLE_REQUIRES_LOOP_MARKER",
                        "Cycle detected without data.loop = true",
                        "transitions",
                    )
                return
            visiting.add(node_id)
            for transition in outgoing[node_id]:
                visit(str(transition.get("target")), [*stack, node_id])
            visiting.remove(node_id)
            visited.add(node_id)

        for node_id in node_ids:
            visit(node_id, [])

    def _cycle_allows_loop(self, cycle: list[str], outgoing: dict[str, list[dict[str, Any]]]) -> bool:
        cycle_set = set(cycle)
        for node_id in cycle_set:
            for transition in outgoing[node_id]:
                if transition.get("target") in cycle_set and isinstance(transition.get("data"), dict):
                    if transition["data"].get("loop") is True:
                        return True
        return False

    def _reachable_from(self, start_id: str, outgoing: dict[str, list[dict[str, Any]]]) -> set[str]:
        reachable: set[str] = set()
        queue: deque[str] = deque([start_id])
        while queue:
            node_id = queue.popleft()
            if node_id in reachable:
                continue
            reachable.add(node_id)
            for transition in outgoing[node_id]:
                target = str(transition.get("target"))
                if target not in reachable:
                    queue.append(target)
        return reachable

    def _transition_meaning(self, transition: dict[str, Any]) -> str | None:
        return self._text(transition.get("outcome")) or self._text(transition.get("label")) or self._text(transition.get("condition"))

    def _transition_path(self, transitions: list[dict[str, Any]], transition: dict[str, Any]) -> str:
        try:
            index = transitions.index(transition)
        except ValueError:
            return "transitions"
        return f"transitions[{index}]"

    def _looks_like_business_condition(self, value: str) -> bool:
        normalized = value.lower()
        return any(marker in normalized for marker in self.BUSINESS_CONDITION_MARKERS)

    def _is_detached(self, node: dict[str, Any]) -> bool:
        data = node.get("data")
        return isinstance(data, dict) and data.get("detached") is True

    def _reject_ui_fields(self, value: Any, path: str, errors: list[WfmValidationIssue]) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                child_path = f"{path}.{key}" if path != "$" else key
                if key in self.UI_FIELDS:
                    self._error(
                        errors,
                        "UI_FIELD_NOT_ALLOWED",
                        f"{key} is a UI-only field and is not allowed in WFM",
                        child_path,
                    )
                self._reject_ui_fields(child, child_path, errors)
        elif isinstance(value, list):
            for index, child in enumerate(value):
                self._reject_ui_fields(child, f"{path}[{index}]", errors)

    def _has_text(self, value: Any) -> bool:
        return isinstance(value, str) and bool(value.strip())

    def _text(self, value: Any) -> str | None:
        return value.strip() if isinstance(value, str) and value.strip() else None

    def _error(self, errors: list[WfmValidationIssue], code: str, message: str, path: str) -> None:
        errors.append(WfmValidationIssue(code=code, message=message, path=path))

    def _warning(self, warnings: list[WfmValidationIssue], code: str, message: str, path: str) -> None:
        warnings.append(WfmValidationIssue(code=code, message=message, path=path))
