import re
from copy import deepcopy
from typing import Any


class WfmV2Canonicalizer:
    CORE_KINDS = {
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
    SEMANTIC_LABELS = {
        "DEFAULT": "Default",
        "YES": "Yes",
        "NO": "No",
        "SUCCESS": "Success",
        "FAILURE": "Failure",
        "CANCEL": "Cancel",
        "RETRY": "Retry",
        "TIMEOUT": "Timeout",
    }

    def canonicalize(self, payload: dict[str, Any], requirement: str | None = None) -> tuple[dict[str, Any], list[str]]:
        source = deepcopy(payload)
        warnings: list[str] = []

        wfm = self._strip_ui_fields(source, "$", warnings)
        workflow = wfm.get("workflow") if isinstance(wfm.get("workflow"), dict) else {}
        ast = wfm.get("ast") if isinstance(wfm.get("ast"), dict) else {}

        wfm["wfmVersion"] = self._wfm_version(wfm)
        title = (
            self._text(wfm.get("workflowName"))
            or self._text(wfm.get("workflow_name"))
            or self._text(workflow.get("title"))
            or self._text(workflow.get("name"))
            or self._title_from_requirement(requirement)
            or "Workflow"
        )
        wfm["workflowName"] = title
        workflow_id = wfm.get("workflowId") or wfm.get("workflow_id") or workflow.get("id")
        wfm["workflowId"] = self._safe_id(workflow_id, self._slug(title), warnings, "workflowId")
        wfm["description"] = self._optional_text(wfm.get("description"))
        wfm["direction"] = self._text(wfm.get("direction")) or "LR"

        metadata = wfm.get("metadata") if isinstance(wfm.get("metadata"), dict) else {}
        metadata["source"] = self._optional_text(metadata.get("source"))
        metadata["language"] = self._optional_text(metadata.get("language")) or "unknown"
        metadata["warnings"] = metadata.get("warnings") if isinstance(metadata.get("warnings"), list) else []
        wfm["metadata"] = metadata

        nodes_source = wfm.get("nodes")
        if not isinstance(nodes_source, list) and isinstance(ast.get("nodes"), list):
            nodes_source = ast.get("nodes")
            warnings.append("Canonicalized nodes from WFM AST shape.")
        transitions_source = wfm.get("transitions")
        if not isinstance(transitions_source, list) and isinstance(ast.get("transitions"), list):
            transitions_source = ast.get("transitions")
            warnings.append("Canonicalized transitions from WFM AST shape.")

        nodes, node_id_map = self._canonicalize_nodes(nodes_source, warnings)
        wfm["nodes"] = nodes
        transitions = self._canonicalize_transitions(transitions_source, node_id_map, warnings)
        self._normalize_branching_nodes(nodes, transitions, warnings)
        self._fill_decision_transition_meanings(nodes, transitions, warnings)
        self._mark_cycle_transitions(nodes, transitions, warnings)
        wfm["transitions"] = transitions
        if warnings:
            metadata["warnings"] = list(dict.fromkeys([*metadata["warnings"], *warnings]))
        return wfm, warnings

    def _canonicalize_nodes(self, value: Any, warnings: list[str]) -> tuple[list[dict[str, Any]], dict[str, str]]:
        nodes = value if isinstance(value, list) else []
        canonical_nodes: list[dict[str, Any]] = []
        node_id_map: dict[str, str] = {}
        used_ids: set[str] = set()
        for index, node_value in enumerate(nodes, start=1):
            node = dict(node_value) if isinstance(node_value, dict) else {}
            original_id = self._text(node.get("id"))
            raw_kind = self._text(node.get("kind")) or self._text(node.get("role")) or self._text(node.get("type")) or "ACTION"
            kind = raw_kind.upper().replace("-", "_").replace(" ", "_")
            if kind != raw_kind:
                warnings.append(f"Normalized node kind '{raw_kind}' to '{kind}'.")
            fallback_name = self._node_name(node) or f"node_{index}"
            fallback_id = self._slug(fallback_name) or f"node_{index}"
            node_id = self._safe_id(node.get("id"), fallback_id, warnings, f"nodes[{index - 1}].id")
            node_id = self._dedupe_id(node_id, used_ids)
            used_ids.add(node_id)
            if original_id:
                node_id_map[original_id] = node_id
            name = self._node_name(node) or node_id.replace("_", " ").title()
            node["id"] = node_id
            node["kind"] = kind
            node["name"] = name
            node["description"] = self._optional_text(node.get("description"))
            node["actor"] = self._optional_text(node.get("actor"))
            node["data"] = node.get("data") if isinstance(node.get("data"), dict) else {}
            canonical_nodes.append(node)
        return canonical_nodes, node_id_map

    def _canonicalize_transitions(
        self,
        value: Any,
        node_id_map: dict[str, str],
        warnings: list[str],
    ) -> list[dict[str, Any]]:
        transitions = value if isinstance(value, list) else []
        canonical_transitions: list[dict[str, Any]] = []
        used_ids: set[str] = set()
        for index, transition_value in enumerate(transitions, start=1):
            transition = dict(transition_value) if isinstance(transition_value, dict) else {}
            fallback_id = f"t_{index}"
            transition_id = self._safe_id(transition.get("id"), fallback_id, warnings, f"transitions[{index - 1}].id")
            transition_id = self._dedupe_id(transition_id, used_ids)
            used_ids.add(transition_id)
            transition["id"] = transition_id
            transition["source"] = self._canonical_reference(
                transition.get("source") or transition.get("from") or transition.get("from_"),
                node_id_map,
            )
            transition["target"] = self._canonical_reference(
                transition.get("target") or transition.get("to"),
                node_id_map,
            )
            semantic = self._semantic(transition)
            transition["label"] = self._optional_text(transition.get("label")) or self._semantic_label(semantic)
            transition["condition"] = self._optional_text(transition.get("condition"))
            transition["outcome"] = self._optional_text(transition.get("outcome")) or self._semantic_label(semantic)
            transition["data"] = transition.get("data") if isinstance(transition.get("data"), dict) else {}
            if semantic and not transition["data"].get("semantic"):
                transition["data"]["semantic"] = semantic
            canonical_transitions.append(transition)
        return canonical_transitions

    def _normalize_branching_nodes(
        self,
        nodes: list[dict[str, Any]],
        transitions: list[dict[str, Any]],
        warnings: list[str],
    ) -> None:
        outgoing_count: dict[str, int] = {}
        for transition in transitions:
            source = self._text(transition.get("source"))
            if source:
                outgoing_count[source] = outgoing_count.get(source, 0) + 1

        for node in nodes:
            node_id = self._text(node.get("id"))
            kind = self._text(node.get("kind"))
            if not node_id or kind not in self.LINEAR_TASK_KINDS:
                continue
            if outgoing_count.get(node_id, 0) <= 1:
                continue
            node["kind"] = "DECISION"
            warnings.append(
                f"Normalized branching {kind} node '{node_id}' to DECISION because it has multiple outgoing transitions."
            )

    def _fill_decision_transition_meanings(
        self,
        nodes: list[dict[str, Any]],
        transitions: list[dict[str, Any]],
        warnings: list[str],
    ) -> None:
        node_by_id = {self._text(node.get("id")): node for node in nodes if self._text(node.get("id"))}
        for transition in transitions:
            source = self._text(transition.get("source"))
            if not source:
                continue
            source_node = node_by_id.get(source)
            if not source_node or self._text(source_node.get("kind")) != "DECISION":
                continue
            if self._transition_has_meaning(transition):
                continue
            target = self._text(transition.get("target"))
            target_node = node_by_id.get(target)
            meaning = self._branch_meaning(target_node)
            if meaning:
                transition["label"] = meaning
                transition["outcome"] = meaning
                data = transition.get("data") if isinstance(transition.get("data"), dict) else {}
                semantic = self._semantic_from_meaning(meaning)
                if semantic and not data.get("semantic"):
                    data["semantic"] = semantic
                transition["data"] = data
                warnings.append(
                    f"Filled missing branch meaning for transition '{transition.get('id')}' from target node."
                )

    def _mark_cycle_transitions(
        self,
        nodes: list[dict[str, Any]],
        transitions: list[dict[str, Any]],
        warnings: list[str],
    ) -> None:
        node_order = [str(node.get("id")) for node in nodes if self._text(node.get("id"))]
        node_ids = set(node_order)
        outgoing: dict[str, list[dict[str, Any]]] = {}
        for transition in transitions:
            source = str(transition.get("source") or "")
            target = str(transition.get("target") or "")
            if source in node_ids and target in node_ids:
                outgoing.setdefault(source, []).append(transition)

        visiting: set[str] = set()
        visited: set[str] = set()

        def visit(node_id: str) -> None:
            if node_id in visited:
                return
            visiting.add(node_id)
            for transition in outgoing.get(node_id, []):
                target = str(transition.get("target") or "")
                if target in visiting:
                    data = transition.get("data") if isinstance(transition.get("data"), dict) else {}
                    if data.get("loop") is not True:
                        data["loop"] = True
                        transition["data"] = data
                        warnings.append(f"Marked transition '{transition.get('id')}' as a loop edge.")
                    continue
                if target not in visited:
                    visit(target)
            visiting.remove(node_id)
            visited.add(node_id)

        for node_id in node_order:
            if node_id not in visited:
                visit(node_id)

    def _canonical_reference(self, value: Any, node_id_map: dict[str, str]) -> str:
        raw = self._optional_text(value)
        if not raw:
            return ""
        return node_id_map.get(raw, raw)

    def _safe_id(self, value: Any, fallback: str, warnings: list[str], path: str) -> str:
        raw = self._text(value)
        if not raw:
            warnings.append(f"Generated missing id for {path}.")
            return fallback
        safe = self._slug(raw)
        if safe != raw:
            warnings.append(f"Normalized id '{raw}' to '{safe}' at {path}.")
        return safe

    def _dedupe_id(self, value: str, used_ids: set[str]) -> str:
        if value not in used_ids:
            return value
        suffix = 2
        while f"{value}_{suffix}" in used_ids:
            suffix += 1
        return f"{value}_{suffix}"

    def _optional_text(self, value: Any) -> str | None:
        text = self._text(value)
        return text if text else None

    def _node_name(self, node: dict[str, Any]) -> str | None:
        data = node.get("data") if isinstance(node.get("data"), dict) else {}
        return (
            self._text(node.get("name"))
            or self._text(node.get("title"))
            or self._text(node.get("label"))
            or self._text(data.get("title"))
            or self._text(data.get("label"))
        )

    def _semantic(self, transition: dict[str, Any]) -> str | None:
        value = (
            self._text(transition.get("semantic"))
            or self._text(transition.get("type"))
            or self._text(transition.get("edgeType"))
        )
        if not value:
            data = transition.get("data") if isinstance(transition.get("data"), dict) else {}
            value = self._text(data.get("semantic")) or self._text(data.get("type"))
        if not value:
            return None
        normalized = value.upper().replace("-", "_").replace(" ", "_")
        return normalized if normalized in self.SEMANTIC_LABELS else None

    def _semantic_label(self, semantic: str | None) -> str | None:
        if not semantic or semantic == "DEFAULT":
            return None
        return self.SEMANTIC_LABELS.get(semantic)

    def _transition_has_meaning(self, transition: dict[str, Any]) -> bool:
        return bool(
            self._text(transition.get("label"))
            or self._text(transition.get("condition"))
            or self._text(transition.get("outcome"))
        )

    def _branch_meaning(self, target_node: dict[str, Any] | None) -> str | None:
        if not target_node:
            return None
        kind = self._text(target_node.get("kind")) or ""
        name = self._text(target_node.get("name")) or self._text(target_node.get("id")) or "Branch"
        normalized = name.lower()
        if kind == "ERROR" or any(marker in normalized for marker in ["error", "fail", "failure", "invalid", "reject"]):
            return "Failure"
        if any(marker in normalized for marker in ["cancel", "cancelled", "canceled"]):
            return "Cancel"
        if any(marker in normalized for marker in ["retry", "again", "try again"]):
            return "Retry"
        if any(marker in normalized for marker in ["success", "valid", "approve", "complete", "logged in", "dashboard"]):
            return "Success"
        return name

    def _semantic_from_meaning(self, meaning: str) -> str | None:
        normalized = meaning.lower()
        if normalized in {"failure", "fail", "error", "invalid", "rejected"}:
            return "FAILURE"
        if normalized in {"cancel", "cancelled", "canceled"}:
            return "CANCEL"
        if normalized in {"retry", "try again"}:
            return "RETRY"
        if normalized in {"success", "valid", "approved"}:
            return "SUCCESS"
        return None

    def _wfm_version(self, wfm: dict[str, Any]) -> str:
        raw = self._text(wfm.get("wfmVersion")) or self._text(wfm.get("wfm_version")) or "2.0"
        return "2.0" if raw in {"2", "2.0"} else raw

    def _strip_ui_fields(self, value: Any, path: str, warnings: list[str]) -> Any:
        if isinstance(value, dict):
            cleaned: dict[str, Any] = {}
            for key, item in value.items():
                if key in self.UI_FIELDS:
                    warnings.append(f"Removed UI-only field at {path}.{key}.")
                    continue
                cleaned[key] = self._strip_ui_fields(item, f"{path}.{key}", warnings)
            return cleaned
        if isinstance(value, list):
            return [self._strip_ui_fields(item, f"{path}[{index}]", warnings) for index, item in enumerate(value)]
        return value

    def _text(self, value: Any) -> str | None:
        if value is None:
            return None
        if not isinstance(value, str):
            return str(value).strip()
        return value.strip()

    def _slug(self, value: str) -> str:
        slug = re.sub(r"[^a-zA-Z0-9]+", "_", value.strip().lower()).strip("_")
        return slug or "workflow"

    def _title_from_requirement(self, requirement: str | None) -> str | None:
        if not requirement:
            return None
        for raw_line in requirement.splitlines():
            line = raw_line.strip().strip("-* ")
            if line:
                return line.removeprefix("Feature:").strip() or "Workflow"
        return None
