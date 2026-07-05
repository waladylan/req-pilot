from typing import Any

from app.schemas.flowchart import FlowEdge, FlowNode, Flowchart
from app.schemas.wfm import WfmDocument, WfmNode, WfmTransition


class FlowchartMapper:
    EDGE_TYPES = {"DEFAULT", "YES", "NO", "SUCCESS", "FAILURE", "CANCEL", "RETRY", "TIMEOUT"}

    def map(self, wfm: WfmDocument | dict[str, Any]) -> Flowchart:
        if isinstance(wfm, dict) and wfm.get("wfmVersion") == "2.0":
            return self._map_v2(wfm)

        ast = wfm.ast
        if ast is None:
            return Flowchart(nodes=[], edges=[], mermaid="flowchart LR\n")

        return Flowchart(
            nodes=[self._to_flow_node(node) for node in ast.nodes],
            edges=[self._to_flow_edge(transition) for transition in ast.transitions],
            mermaid=self._to_mermaid(wfm),
        )

    def _to_flow_node(self, node: WfmNode) -> FlowNode:
        return FlowNode(
            id=node.id or "",
            label=node.title or node.id or "",
            type=self._node_type(node.role),
        )

    def _node_type(self, role: str | None) -> str:
        if role == "START":
            return "START"
        if role == "END":
            return "END"
        if role == "DECISION":
            return "DECISION"
        return "ACTION"

    def _to_flow_edge(self, transition: WfmTransition) -> FlowEdge:
        return FlowEdge(
            id=transition.id or "",
            source=transition.from_ or "",
            target=transition.to or "",
            label=transition.condition,
            type=self._edge_type(transition.semantic),
        )

    def _edge_type(self, semantic: str | None) -> str:
        return semantic if semantic in self.EDGE_TYPES else "DEFAULT"

    def _to_mermaid(self, wfm: WfmDocument) -> str:
        ast = wfm.ast
        if ast is None:
            return "flowchart LR\n"

        lines = ["flowchart LR"]
        for node in ast.nodes:
            lines.append(f"  {node.id}{self._format_mermaid_node(node)}")
        for transition in ast.transitions:
            label = ""
            if transition.condition and transition.condition.strip():
                label = f"|{self._escape_mermaid(transition.condition)}| "
            lines.append(f"  {transition.from_} --> {label}{transition.to}")
        return "\n".join(lines) + "\n"

    def _format_mermaid_node(self, node: WfmNode) -> str:
        label = self._escape_mermaid(node.title or node.id or "")
        role = node.role
        if role in {"START", "END"}:
            return f'(["{label}"])'
        if role == "DECISION":
            return f'{{"{label}"}}'
        if role in {"INPUT", "OUTPUT"}:
            return f'[/"{label}"/]'
        if role == "SUBPROCESS":
            return f'[[\"{label}\"]]'
        return f'["{label}"]'

    def _escape_mermaid(self, label: str) -> str:
        return label.replace("\\", "\\\\").replace('"', '\\"').replace("|", "\\|")

    def _map_v2(self, wfm: dict[str, Any]) -> Flowchart:
        nodes = wfm.get("nodes") if isinstance(wfm.get("nodes"), list) else []
        transitions = wfm.get("transitions") if isinstance(wfm.get("transitions"), list) else []
        return Flowchart(
            nodes=[self._to_flow_node_v2(node) for node in nodes if isinstance(node, dict)],
            edges=[self._to_flow_edge_v2(transition) for transition in transitions if isinstance(transition, dict)],
            mermaid=self._to_mermaid_v2(nodes, transitions),
        )

    def _to_flow_node_v2(self, node: dict[str, Any]) -> FlowNode:
        node_id = str(node.get("id") or "")
        return FlowNode(
            id=node_id,
            label=str(node.get("name") or node_id),
            type=self._node_type_v2(str(node.get("kind") or "")),
        )

    def _node_type_v2(self, kind: str) -> str:
        normalized = kind.upper()
        if normalized == "START":
            return "START"
        if normalized in {"END", "ERROR"}:
            return "END"
        if normalized == "DECISION":
            return "DECISION"
        return "ACTION"

    def _to_flow_edge_v2(self, transition: dict[str, Any]) -> FlowEdge:
        label = self._edge_label_v2(transition)
        return FlowEdge(
            id=str(transition.get("id") or ""),
            source=str(transition.get("source") or ""),
            target=str(transition.get("target") or ""),
            label=label,
            type=self._edge_type_v2(label),
        )

    def _edge_label_v2(self, transition: dict[str, Any]) -> str | None:
        for key in ("label", "condition", "outcome"):
            value = transition.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
        return None

    def _edge_type_v2(self, label: str | None) -> str:
        if not label:
            return "DEFAULT"
        normalized = label.lower()
        if normalized in {"yes", "approved", "valid", "success", "succeeded"}:
            return "YES"
        if normalized in {"no", "rejected", "invalid", "cancelled", "canceled"}:
            return "NO"
        if "fail" in normalized or "error" in normalized:
            return "FAILURE"
        if "success" in normalized:
            return "SUCCESS"
        return "DEFAULT"

    def _to_mermaid_v2(self, nodes: list[Any], transitions: list[Any]) -> str:
        lines = ["flowchart LR"]
        for node in nodes:
            if isinstance(node, dict):
                lines.append(f"  {node.get('id')}{self._format_mermaid_node_v2(node)}")
        for transition in transitions:
            if not isinstance(transition, dict):
                continue
            label = ""
            transition_label = self._edge_label_v2(transition)
            if transition_label:
                label = f"|{self._escape_mermaid(transition_label)}| "
            lines.append(f"  {transition.get('source')} --> {label}{transition.get('target')}")
        return "\n".join(lines) + "\n"

    def _format_mermaid_node_v2(self, node: dict[str, Any]) -> str:
        label = self._escape_mermaid(str(node.get("name") or node.get("id") or ""))
        kind = str(node.get("kind") or "").upper()
        if kind in {"START", "END", "ERROR"}:
            return f'(["{label}"])'
        if kind == "DECISION":
            return f'{{"{label}"}}'
        return f'["{label}"]'
