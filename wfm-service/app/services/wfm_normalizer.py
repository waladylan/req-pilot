from copy import deepcopy
from typing import Any


class WfmNormalizer:
    def normalize(self, payload: dict[str, Any], requirement: str | None = None) -> dict[str, Any]:
        wfm = deepcopy(payload)
        wfm["schemaVersion"] = wfm.get("schemaVersion") or "1.0"
        wfm["modelType"] = wfm.get("modelType") or "WORKFLOW_AST"

        workflow = wfm.get("workflow") if isinstance(wfm.get("workflow"), dict) else {}
        title = workflow.get("title") or self._title_from_requirement(requirement) or "Workflow"
        workflow["id"] = workflow.get("id") or self._slug(title)
        workflow["title"] = title
        workflow["language"] = workflow.get("language") or "unknown"
        if requirement and not workflow.get("sourceRequirement"):
            workflow["sourceRequirement"] = requirement
        wfm["workflow"] = workflow

        extensions = wfm.get("extensions") if isinstance(wfm.get("extensions"), dict) else {}
        extensions["nodeKinds"] = extensions.get("nodeKinds") if isinstance(extensions.get("nodeKinds"), list) else []
        extensions["transitionKinds"] = (
            extensions.get("transitionKinds") if isinstance(extensions.get("transitionKinds"), list) else []
        )
        wfm["extensions"] = extensions

        ast = wfm.get("ast") if isinstance(wfm.get("ast"), dict) else {}
        ast["actors"] = ast.get("actors") if isinstance(ast.get("actors"), list) else []
        ast["variables"] = ast.get("variables") if isinstance(ast.get("variables"), list) else []
        ast["nodes"] = self._normalize_nodes(ast.get("nodes"))
        ast["transitions"] = self._normalize_transitions(ast.get("transitions"))
        ast["annotations"] = ast.get("annotations") if isinstance(ast.get("annotations"), list) else []
        wfm["ast"] = ast
        return wfm

    def _normalize_nodes(self, value: Any) -> list[dict[str, Any]]:
        nodes = value if isinstance(value, list) else []
        normalized: list[dict[str, Any]] = []
        for index, node_value in enumerate(nodes, start=1):
            node = dict(node_value) if isinstance(node_value, dict) else {}
            role = node.get("role") or "ACTION"
            node["id"] = node.get("id") or f"N{index}"
            node["role"] = role
            node["kind"] = node.get("kind") or role
            node["title"] = node.get("title") or node["id"]
            node["tags"] = node.get("tags") if isinstance(node.get("tags"), list) else []
            normalized.append(node)
        return normalized

    def _normalize_transitions(self, value: Any) -> list[dict[str, Any]]:
        transitions = value if isinstance(value, list) else []
        normalized: list[dict[str, Any]] = []
        for index, transition_value in enumerate(transitions, start=1):
            transition = dict(transition_value) if isinstance(transition_value, dict) else {}
            transition["id"] = transition.get("id") or f"T{index}"
            transition["semantic"] = transition.get("semantic") or "DEFAULT"
            normalized.append(transition)
        return normalized

    def _title_from_requirement(self, requirement: str | None) -> str | None:
        if not requirement:
            return None
        for raw_line in requirement.splitlines():
            line = raw_line.strip().strip("-* ")
            if line:
                return line.removeprefix("Feature:").strip() or "Workflow"
        return None

    def _slug(self, value: str) -> str:
        slug = "".join(char.lower() if char.isalnum() else "-" for char in value).strip("-")
        return "-".join(part for part in slug.split("-") if part) or "workflow"
