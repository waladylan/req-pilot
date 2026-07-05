import {
  DEFAULT_EDGE_SEMANTIC,
  DEFAULT_NODE_KIND,
  NODE_RENDER_REGISTRY,
  WFM_MODEL_TYPE,
  WFM_SCHEMA_VERSION,
} from "@/constants";
import { getEdgeLabel, getNodeTitle } from "@/helpers/flowchart";
import type {
  FlowEdgeDTO,
  FlowNodeDTO,
  FlowNodeKind,
  FlowchartDTO,
  WfmDocument,
  WfmNode,
  WfmNodeRole,
  WfmTransition,
  WfmTransitionSemantic,
} from "@/types/requirement";

export function reactFlowToWfm(currentWfm: WfmDocument, flowchart: FlowchartDTO): WfmDocument {
  const nodesById = new Map(currentWfm.ast.nodes.map((node) => [node.id, node]));
  const transitionsById = new Map(
    currentWfm.ast.transitions.map((transition) => [transition.id, transition]),
  );

  return {
    ...currentWfm,
    schemaVersion: WFM_SCHEMA_VERSION,
    modelType: WFM_MODEL_TYPE,
    ast: {
      actors: currentWfm.ast.actors ?? [],
      annotations: currentWfm.ast.annotations ?? [],
      variables: currentWfm.ast.variables ?? [],
      nodes: flowchart.nodes.map((node) => toWfmNode(node, nodesById.get(node.id))),
      transitions: flowchart.edges.map((edge) =>
        toWfmTransition(edge, transitionsById.get(edge.id)),
      ),
    },
  };
}

export function updateWfmNodeTitle(
  currentWfm: WfmDocument,
  nodeId: string,
  title: string,
): WfmDocument {
  const nextTitle = normalizeText(title, "Untitled node");
  return updateWfmNode(currentWfm, nodeId, (node) => ({
    ...node,
    title: nextTitle,
  }));
}

export function updateWfmNodeRole(
  currentWfm: WfmDocument,
  nodeId: string,
  role: WfmNodeRole,
): WfmDocument {
  return updateWfmNode(currentWfm, nodeId, (node) => ({
    ...node,
    kind: node.role === role ? node.kind : role,
    role,
  }));
}

export function updateWfmNodeKind(
  currentWfm: WfmDocument,
  nodeId: string,
  kind: string,
): WfmDocument {
  return updateWfmNode(currentWfm, nodeId, (node) => ({
    ...node,
    kind: normalizeText(kind, node.role),
  }));
}

export function updateWfmNodeMetadata(
  currentWfm: WfmDocument,
  nodeId: string,
  metadata: Pick<WfmNode, "actorId" | "description">,
): WfmDocument {
  return updateWfmNode(currentWfm, nodeId, (node) => ({
    ...node,
    actorId: normalizeOptionalText(metadata.actorId),
    description: normalizeOptionalText(metadata.description),
  }));
}

export function updateWfmTransitionSemantic(
  currentWfm: WfmDocument,
  transitionId: string,
  semantic: WfmTransitionSemantic,
): WfmDocument {
  return updateWfmTransition(currentWfm, transitionId, (transition) => ({
    ...transition,
    semantic,
  }));
}

export function updateWfmTransitionCondition(
  currentWfm: WfmDocument,
  transitionId: string,
  condition: string,
): WfmDocument {
  return updateWfmTransition(currentWfm, transitionId, (transition) => ({
    ...transition,
    condition: normalizeOptionalText(condition),
  }));
}

export function updateWfmTransitionDescription(
  currentWfm: WfmDocument,
  transitionId: string,
  description: string,
): WfmDocument {
  return updateWfmTransition(currentWfm, transitionId, (transition) => ({
    ...transition,
    description: normalizeOptionalText(description),
  }));
}

export function addWfmNode(
  currentWfm: WfmDocument,
  role: WfmNodeRole,
  options: {
    actorId?: string;
    description?: string;
    kind?: string;
    title?: string;
  } = {},
): { node: WfmNode; wfm: WfmDocument } {
  const ast = normalizeAst(currentWfm);
  const node: WfmNode = {
    actorId: normalizeOptionalText(options.actorId),
    description: normalizeOptionalText(options.description),
    id: createNextId(
      ast.nodes.map((item) => item.id),
      "N",
    ),
    kind: normalizeText(options.kind, role),
    role,
    tags: [],
    title: normalizeText(options.title, NODE_RENDER_REGISTRY[role].defaultTitle),
  };

  return {
    node,
    wfm: {
      ...currentWfm,
      ast: {
        ...ast,
        nodes: [...ast.nodes, node],
      },
    },
  };
}

export function deleteWfmNode(currentWfm: WfmDocument, nodeId: string): WfmDocument {
  const ast = normalizeAst(currentWfm);
  return {
    ...currentWfm,
    ast: {
      ...ast,
      nodes: ast.nodes.filter((node) => node.id !== nodeId),
      transitions: ast.transitions.filter(
        (transition) => transition.from !== nodeId && transition.to !== nodeId,
      ),
    },
  };
}

export function connectWfmNodes(
  currentWfm: WfmDocument,
  connection: {
    source?: string | null;
    target?: string | null;
  },
  semantic: WfmTransitionSemantic = DEFAULT_EDGE_SEMANTIC,
): WfmDocument {
  if (!connection.source || !connection.target || connection.source === connection.target) {
    return currentWfm;
  }

  const ast = normalizeAst(currentWfm);
  const nodeIds = new Set(ast.nodes.map((node) => node.id));
  if (!nodeIds.has(connection.source) || !nodeIds.has(connection.target)) {
    return currentWfm;
  }

  const exists = ast.transitions.some(
    (transition) => transition.from === connection.source && transition.to === connection.target,
  );
  if (exists) {
    return currentWfm;
  }

  const transition: WfmTransition = {
    from: connection.source,
    id: createNextId(
      ast.transitions.map((item) => item.id),
      "T",
    ),
    semantic,
    to: connection.target,
  };

  return {
    ...currentWfm,
    ast: {
      ...ast,
      transitions: [...ast.transitions, transition],
    },
  };
}

export function deleteWfmTransition(currentWfm: WfmDocument, transitionId: string): WfmDocument {
  const ast = normalizeAst(currentWfm);
  return {
    ...currentWfm,
    ast: {
      ...ast,
      transitions: ast.transitions.filter((transition) => transition.id !== transitionId),
    },
  };
}

function toWfmNode(node: FlowNodeDTO, existing?: WfmNode): WfmNode {
  const role = getRoleFromFlowNode(node, existing);
  const kind = role === existing?.role ? existing.kind : role;

  return {
    actorId: existing?.actorId,
    data: existing?.data,
    description: node.description ?? existing?.description,
    id: node.id,
    kind,
    role,
    tags: existing?.tags ?? [],
    title: getNodeTitle(node).trim() || existing?.title || "Untitled node",
  };
}

function toWfmTransition(edge: FlowEdgeDTO, existing?: WfmTransition): WfmTransition {
  return {
    data: existing?.data,
    description: existing?.description,
    from: edge.source,
    id: edge.id,
    kind: existing?.kind,
    semantic: toWfmSemantic(edge.type ?? existing?.semantic),
    condition: getEdgeLabel(edge) || existing?.condition,
    to: edge.target,
  };
}

function getRoleFromFlowNode(node: FlowNodeDTO, existing?: WfmNode): WfmNodeRole {
  if (node.type === "START") {
    return "START";
  }
  if (node.type === "END") {
    return "END";
  }
  if (node.type === "DECISION") {
    return "DECISION";
  }

  switch (node.nodeKind ?? DEFAULT_NODE_KIND) {
    case "ERROR":
      return "ERROR";
    case "INPUT_OUTPUT":
      return existing?.role === "OUTPUT" ? "OUTPUT" : "INPUT";
    case "START_END":
      return existing?.role === "END" ? "END" : "START";
    case "DECISION":
      return "DECISION";
    case "ACTION":
      return existing?.role === "SUBPROCESS" ? "SUBPROCESS" : "ACTION";
  }
}

function toWfmSemantic(value?: WfmTransitionSemantic): WfmTransitionSemantic {
  return value ?? DEFAULT_EDGE_SEMANTIC;
}

export function nodeKindToWfmRole(nodeKind: FlowNodeKind): WfmNodeRole {
  switch (nodeKind) {
    case "ERROR":
      return "ERROR";
    case "INPUT_OUTPUT":
      return "INPUT";
    case "START_END":
      return "START";
    case "DECISION":
      return "DECISION";
    case "ACTION":
      return "ACTION";
  }
}

export function wfmRoleToFlowNodeKind(role: WfmNodeRole): FlowNodeKind {
  return NODE_RENDER_REGISTRY[role].flowNodeKind;
}

function updateWfmNode(
  currentWfm: WfmDocument,
  nodeId: string,
  updater: (node: WfmNode) => WfmNode,
): WfmDocument {
  const ast = normalizeAst(currentWfm);
  return {
    ...currentWfm,
    ast: {
      ...ast,
      nodes: ast.nodes.map((node) => (node.id === nodeId ? updater(node) : node)),
    },
  };
}

function updateWfmTransition(
  currentWfm: WfmDocument,
  transitionId: string,
  updater: (transition: WfmTransition) => WfmTransition,
): WfmDocument {
  const ast = normalizeAst(currentWfm);
  return {
    ...currentWfm,
    ast: {
      ...ast,
      transitions: ast.transitions.map((transition) =>
        transition.id === transitionId ? updater(transition) : transition,
      ),
    },
  };
}

function normalizeAst(currentWfm: WfmDocument): WfmDocument["ast"] {
  return {
    actors: currentWfm.ast.actors ?? [],
    annotations: currentWfm.ast.annotations ?? [],
    nodes: currentWfm.ast.nodes,
    transitions: currentWfm.ast.transitions,
    variables: currentWfm.ast.variables ?? [],
  };
}

function createNextId(existingIds: string[], prefix: "N" | "T"): string {
  const numericIds = existingIds
    .map((id) => (id.startsWith(prefix) ? Number(id.slice(prefix.length)) : Number.NaN))
    .filter(Number.isFinite);
  let index = Math.max(0, ...numericIds, existingIds.length) + 1;
  while (existingIds.includes(`${prefix}${index}`)) {
    index++;
  }
  return `${prefix}${index}`;
}

function normalizeText(value: string | undefined, fallback: string): string {
  return value?.trim() || fallback;
}

function normalizeOptionalText(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed || undefined;
}
