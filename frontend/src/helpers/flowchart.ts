import {
  MarkerType,
  Position,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
  type NodeHandle,
  type XYPosition,
  applyEdgeChanges,
  applyNodeChanges,
} from "@xyflow/react";

import {
  EDGE_BRANCH_LAYOUT_ORDER,
  DEFAULT_EDGE_SEMANTIC,
  DEFAULT_NODE_TYPE,
  EDGE_KEYWORD_REGISTRY,
  EDGE_REGISTRY,
  EDGE_SEMANTIC_DETECTION_ORDER,
  FLOW_LAYOUT_CONFIG,
  FLOW_HANDLE_REGISTRY,
  FLOW_HANDLES,
  NODE_KIND_REGISTRY,
  NODE_TYPE_REGISTRY,
  type MermaidNodeShape,
} from "@/constants";
import type {
  FlowEdgeDTO,
  FlowEdgeSemantic,
  FlowHandleId,
  FlowNodeDTO,
  FlowNodeKind,
  FlowNodeType,
  FlowchartDTO,
} from "@/types/requirement";

import { calculateCycleSafeNodeDepths } from "./flow-layout-depth";

export type RequirementFlowNodeData = Record<string, unknown> & {
  canvasSize: FlowNodeCanvasSize;
  description?: string;
  expectedResult?: string;
  label: string;
  labelRows: number;
  nodeKind: FlowNodeKind;
  nodeType: FlowNodeType;
  onLabelChange?: (nodeId: string, label: string) => void;
  precondition?: string;
  title?: string;
};

export type FlowNodeCanvasSize = {
  height: number;
  width: number;
};

export type RequirementFlowNode = Node<RequirementFlowNodeData, "requirementNode">;
export type RequirementFlowEdgeData = Record<string, unknown> & {
  label?: string;
  semantic: FlowEdgeSemantic;
};
export type RequirementFlowEdge = Edge<RequirementFlowEdgeData, "smoothstep">;

const COLUMN_SPACING = FLOW_LAYOUT_CONFIG.columnSpacing;
const ROW_SPACING = FLOW_LAYOUT_CONFIG.branchRowSpacing;
const START_X = FLOW_LAYOUT_CONFIG.startX;
const START_Y = FLOW_LAYOUT_CONFIG.startY;
const HANDLE_SIZE = 12;
const EDGE_LABEL_BACKGROUND_COLOR = "#ffffff";
const EDGE_LABEL_TEXT_COLOR = "#0f172a";

export function createReactFlowNodes(
  flowchart: FlowchartDTO,
  onLabelChange?: (nodeId: string, label: string) => void,
): RequirementFlowNode[] {
  const layout = calculateNodeLayout(flowchart.nodes, flowchart.edges);

  return flowchart.nodes.map((node) => {
    const title = getNodeTitle(node);
    const nodeKind = node.nodeKind ?? detectNodeKind(title, node.type);
    const labelRows = getNodeLabelRows(title, nodeKind);
    const canvasSize = getNodeCanvasSize(nodeKind, labelRows);

    return {
      id: node.id,
      handles: createNodeHandles(node.type, nodeKind, canvasSize),
      height: canvasSize.height,
      initialHeight: canvasSize.height,
      initialWidth: canvasSize.width,
      measured: {
        height: canvasSize.height,
        width: canvasSize.width,
      },
      type: "requirementNode",
      width: canvasSize.width,
      position: node.position ?? layout[node.id] ?? { x: START_X, y: START_Y },
      data: {
        description: node.description,
        expectedResult: node.expectedResult,
        label: title,
        labelRows,
        canvasSize,
        nodeKind,
        nodeType: node.type,
        onLabelChange,
        precondition: node.precondition,
        title,
      },
    };
  });
}

export function createReactFlowEdges(
  flowchart: FlowchartDTO,
  selectedEdgeId?: string,
): RequirementFlowEdge[] {
  const nodesById = new Map(flowchart.nodes.map((node) => [node.id, node]));

  return flowchart.edges.map((edge) => {
    const semantic = resolveEdgeSemantic(edge);
    const color = EDGE_REGISTRY[semantic].color;
    const selected = edge.id === selectedEdgeId;
    const sourceNode = nodesById.get(edge.source);
    const label = getVisibleEdgeLabel(edge, semantic);

    return {
      id: edge.id,
      source: edge.source,
      sourceHandle: getSourceHandleForEdge(semantic, sourceNode),
      target: edge.target,
      targetHandle: FLOW_HANDLES.INPUT,
      type: "smoothstep",
      animated: selected,
      label,
      labelBgBorderRadius: 6,
      labelBgPadding: [8, 4],
      labelBgStyle: {
        fill: EDGE_LABEL_BACKGROUND_COLOR,
        fillOpacity: 0.96,
        stroke: `${color}55`,
        strokeWidth: 1,
      },
      labelShowBg: Boolean(label),
      labelStyle: {
        fill: EDGE_LABEL_TEXT_COLOR,
        fontSize: 12,
        fontWeight: 700,
      },
      data: {
        label,
        semantic,
      },
      markerEnd: {
        type: MarkerType.ArrowClosed,
        color,
      },
      style: {
        stroke: color,
        strokeWidth: selected ? 3 : 2.75,
      },
    };
  });
}

export function createFlowchartFromReactFlow(
  nodes: RequirementFlowNode[],
  edges: RequirementFlowEdge[],
  mermaid?: string,
): FlowchartDTO {
  return {
    nodes: nodes.map((node) => ({
      id: node.id,
      label: getReactFlowNodeTitle(node),
      nodeKind: node.data.nodeKind,
      title: getReactFlowNodeTitle(node),
      type: node.data.nodeType,
      description: node.data.description,
      precondition: node.data.precondition,
      expectedResult: node.data.expectedResult,
      position: roundPosition(node.position),
    })),
    edges: edges.map((edge) => {
      const visualLabel = typeof edge.label === "string" ? edge.label : undefined;
      const label =
        typeof edge.data?.label === "string" && edge.data.label.trim()
          ? edge.data.label
          : visualLabel?.trim()
            ? visualLabel
            : undefined;

      return {
        id: edge.id,
        source: edge.source,
        target: edge.target,
        label,
        data: {
          ...edge.data,
          label: label ?? "",
        },
        type: edge.data?.semantic ?? resolveEdgeSemantic({ label }),
      };
    }),
    mermaid,
  };
}

export function ensureFlowchartNodePositions(flowchart: FlowchartDTO): FlowchartDTO {
  if (
    flowchart.nodes.every((node) => node.position) &&
    !hasUnreadableNodeSpacing(flowchart.nodes, flowchart.edges)
  ) {
    return flowchart;
  }

  return applyCalculatedNodeLayout(flowchart);
}

export function applyCalculatedNodeLayout(flowchart: FlowchartDTO): FlowchartDTO {
  const layout = calculateNodeLayout(flowchart.nodes, flowchart.edges);

  return {
    ...flowchart,
    nodes: flowchart.nodes.map((node) => ({
      ...node,
      nodeKind: node.nodeKind ?? detectNodeKind(node.label, node.type),
      position: roundPosition(layout[node.id] ?? node.position ?? { x: START_X, y: START_Y }),
    })),
  };
}

export function applyFlowNodeChanges(
  flowchart: FlowchartDTO,
  changes: NodeChange<RequirementFlowNode>[],
): FlowchartDTO {
  const nodes = applyNodeChanges(changes, createReactFlowNodes(flowchart));
  const edges = createReactFlowEdges(flowchart);
  return createFlowchartFromReactFlow(nodes, edges, flowchart.mermaid);
}

export function applyFlowEdgeChanges(
  flowchart: FlowchartDTO,
  changes: EdgeChange<RequirementFlowEdge>[],
): FlowchartDTO {
  const nodes = createReactFlowNodes(flowchart);
  const edges = applyEdgeChanges(changes, createReactFlowEdges(flowchart));
  return createFlowchartFromReactFlow(nodes, edges, flowchart.mermaid);
}

export function updateFlowNodeLabel(
  flowchart: FlowchartDTO,
  nodeId: string,
  label: string,
): FlowchartDTO {
  const nextLabel = label;

  return {
    ...flowchart,
    nodes: flowchart.nodes.map((node) =>
      node.id === nodeId
        ? {
            ...node,
            label: nextLabel,
            title: nextLabel,
            nodeKind: detectNodeKind(nextLabel, node.type),
          }
        : node,
    ),
  };
}

export function updateFlowNodeKind(
  flowchart: FlowchartDTO,
  nodeId: string,
  nodeKind: FlowNodeKind,
): FlowchartDTO {
  return {
    ...flowchart,
    nodes: flowchart.nodes.map((node) => (node.id === nodeId ? { ...node, nodeKind } : node)),
  };
}

export function updateFlowNodeMetadata(
  flowchart: FlowchartDTO,
  nodeId: string,
  metadata: Pick<FlowNodeDTO, "description" | "precondition" | "expectedResult">,
): FlowchartDTO {
  return {
    ...flowchart,
    nodes: flowchart.nodes.map((node) =>
      node.id === nodeId
        ? {
            ...node,
            description: metadata.description,
            precondition: metadata.precondition,
            expectedResult: metadata.expectedResult,
          }
        : node,
    ),
  };
}

export function updateFlowEdgeLabel(
  flowchart: FlowchartDTO,
  edgeId: string,
  label: string,
): FlowchartDTO {
  const nextLabel = label;
  return {
    ...flowchart,
    edges: flowchart.edges.map((edge) =>
      edge.id === edgeId
        ? {
            ...edge,
            data: {
              ...edge.data,
              label: nextLabel,
            },
            label: nextLabel,
            type: edge.type,
          }
        : edge,
    ),
  };
}

export function updateFlowEdgeSemantic(
  flowchart: FlowchartDTO,
  edgeId: string,
  semantic: FlowEdgeSemantic,
): FlowchartDTO {
  return {
    ...flowchart,
    edges: flowchart.edges.map((edge) => (edge.id === edgeId ? { ...edge, type: semantic } : edge)),
  };
}

export function addFlowNode(
  flowchart: FlowchartDTO,
  type: FlowNodeType = DEFAULT_NODE_TYPE,
  label = "New Action",
  options: {
    nodeKind?: FlowNodeKind;
    position?: XYPosition;
  } = {},
): FlowchartDTO {
  const id = createNextNodeId(flowchart.nodes);
  const position = options.position ?? findNewNodePosition(flowchart.nodes);
  const nodeKind = options.nodeKind ?? detectNodeKind(label, type);

  return {
    ...flowchart,
    nodes: [
      ...flowchart.nodes,
      {
        id,
        label,
        nodeKind,
        title: label,
        type,
        position,
      },
    ],
  };
}

export function addFlowNodeByKind(
  flowchart: FlowchartDTO,
  nodeKind: FlowNodeKind,
  position?: XYPosition,
): FlowchartDTO {
  return addFlowNode(flowchart, defaultTypeByNodeKind(nodeKind), defaultLabelByNodeKind(nodeKind), {
    nodeKind,
    position,
  });
}

export function duplicateFlowNode(flowchart: FlowchartDTO, nodeId: string): FlowchartDTO {
  const source = flowchart.nodes.find((node) => node.id === nodeId);
  if (!source) {
    return flowchart;
  }

  const id = createNextNodeId(flowchart.nodes);
  return {
    ...flowchart,
    nodes: [
      ...flowchart.nodes,
      {
        ...source,
        id,
        label: `${source.label} copy`,
        title: `${getNodeTitle(source)} copy`,
        position: {
          x: (source.position?.x ?? START_X) + 48,
          y: (source.position?.y ?? START_Y) + 48,
        },
      },
    ],
  };
}

export function deleteFlowNode(flowchart: FlowchartDTO, nodeId: string): FlowchartDTO {
  return {
    ...flowchart,
    nodes: flowchart.nodes.filter((node) => node.id !== nodeId),
    edges: flowchart.edges.filter((edge) => edge.source !== nodeId && edge.target !== nodeId),
  };
}

export function connectFlowNodes(
  flowchart: FlowchartDTO,
  connection: Connection,
  semantic: FlowEdgeSemantic = DEFAULT_EDGE_SEMANTIC,
): FlowchartDTO {
  if (!connection.source || !connection.target || connection.source === connection.target) {
    return flowchart;
  }

  const exists = flowchart.edges.some(
    (edge) => edge.source === connection.source && edge.target === connection.target,
  );
  if (exists) {
    return flowchart;
  }

  return {
    ...flowchart,
    edges: [
      ...flowchart.edges,
      {
        id: createNextEdgeId(flowchart.edges),
        source: connection.source,
        target: connection.target,
        type: semantic,
      },
    ],
  };
}

export function deleteFlowEdge(flowchart: FlowchartDTO, edgeId: string): FlowchartDTO {
  return {
    ...flowchart,
    edges: flowchart.edges.filter((edge) => edge.id !== edgeId),
  };
}

export function shouldPersistNodeChanges(changes: NodeChange<RequirementFlowNode>[]) {
  return changes.some((change) => change.type !== "select" && change.type !== "dimensions");
}

export function shouldResetTestsForNodeChanges(changes: NodeChange<RequirementFlowNode>[]) {
  return changes.some((change) => !["select", "dimensions", "position"].includes(change.type));
}

export function shouldPersistEdgeChanges(changes: EdgeChange<RequirementFlowEdge>[]) {
  return changes.some((change) => change.type !== "select");
}

export function shouldResetTestsForEdgeChanges(changes: EdgeChange<RequirementFlowEdge>[]) {
  return changes.some((change) => change.type !== "select");
}

export function buildRequirementTextFromFlowchart(flowchart: FlowchartDTO): string {
  const paths = enumeratePaths(flowchart);
  const pathNodeIds = new Set(paths.flatMap((path) => path.map((step) => step.node.id)));
  const pathLines = buildPathRequirementLines(paths);
  const disconnectedLines = flowchart.nodes
    .filter((node) => !pathNodeIds.has(node.id))
    .filter((node) => !NODE_TYPE_REGISTRY[node.type].isTerminal)
    .map((node) => `Unconnected node: ${node.label}`);
  const lines = pathLines.length > 0 ? pathLines : ["Review the flowchart nodes and connections."];

  return [
    "Feature edited flow:",
    "",
    ...[...lines, ...disconnectedLines].map((line) => `* ${line}`),
  ].join("\n");
}

export function buildMermaidFromFlowchart(flowchart: FlowchartDTO): string {
  const nodeLines = flowchart.nodes.map((node) => `  ${node.id}${formatMermaidNode(node)}`);
  const edgeLines = flowchart.edges.map((edge) => {
    const condition = getEdgeConditionText(edge);
    const label = condition ? `|${escapeMermaid(condition)}| ` : "";
    return `  ${edge.source} --> ${label}${edge.target}`;
  });

  return ["flowchart LR", ...nodeLines, ...edgeLines].join("\n");
}

export function getNodeTitle(node: Pick<FlowNodeDTO, "label" | "title">): string {
  return node.title ?? node.label ?? "Untitled node";
}

export function resolveEdgeSemantic(edge: Pick<FlowEdgeDTO, "label" | "type">): FlowEdgeSemantic {
  return edge.type ?? detectEdgeSemantic(edge.label ?? "");
}

export function getEdgeLabel(edge: Pick<FlowEdgeDTO, "data" | "label">): string {
  return edge.label ?? firstText(edge.data?.label) ?? firstText(edge.data?.condition) ?? "";
}

export function getVisibleEdgeLabel(
  edge: Pick<FlowEdgeDTO, "data" | "label" | "type">,
  semantic: FlowEdgeSemantic = resolveEdgeSemantic(edge),
): string | undefined {
  const explicitLabel = getEdgeLabel(edge).trim();

  if (explicitLabel) {
    return explicitLabel;
  }

  return semantic === DEFAULT_EDGE_SEMANTIC ? undefined : EDGE_REGISTRY[semantic].label;
}

export function detectEdgeSemantic(value: string): FlowEdgeSemantic {
  const normalized = normalizeForMatch(value);

  for (const semantic of EDGE_SEMANTIC_DETECTION_ORDER) {
    if (containsAny(normalized, ...(EDGE_KEYWORD_REGISTRY[semantic] ?? []))) {
      return semantic;
    }
  }

  return DEFAULT_EDGE_SEMANTIC;
}

export function getEdgeSemanticFromSourceHandle(
  sourceHandle?: string | null,
): FlowEdgeSemantic | undefined {
  if (!isFlowHandleId(sourceHandle)) {
    return undefined;
  }

  const handle = FLOW_HANDLE_REGISTRY[sourceHandle];
  return "semantic" in handle ? handle.semantic : undefined;
}

export function getEdgeConditionText(edge: Pick<FlowEdgeDTO, "data" | "label" | "type">): string {
  const semantic = resolveEdgeSemantic(edge);
  if (semantic !== DEFAULT_EDGE_SEMANTIC) {
    return getVisibleEdgeLabel(edge, semantic) ?? EDGE_REGISTRY[semantic].label;
  }

  return getEdgeLabel(edge).trim();
}

export function detectNodeKind(label: string, type?: FlowNodeType): FlowNodeKind {
  const normalized = normalizeForMatch(label);

  if (type && NODE_TYPE_REGISTRY[type].isTerminal) {
    return NODE_TYPE_REGISTRY[type].nodeKind;
  }

  if (containsAny(normalized, "if", "neu", "confirm", "cancel", "yes/no", "yes no")) {
    return NODE_TYPE_REGISTRY.DECISION.nodeKind;
  }

  if (containsAny(normalized, "start", "end", "bat dau", "ket thuc")) {
    return "START_END";
  }

  if (containsAny(normalized, "input", "upload", "enter", "nhap")) {
    return "INPUT_OUTPUT";
  }

  if (containsAny(normalized, "error", "fail", "invalid", "loi")) {
    return "ERROR";
  }

  if (type && NODE_TYPE_REGISTRY[type].shape === "diamond") {
    return NODE_TYPE_REGISTRY[type].nodeKind;
  }

  return DEFAULT_NODE_TYPE;
}

export function calculateNodeLayout(
  nodes: FlowNodeDTO[],
  edges: FlowEdgeDTO[],
): Record<string, XYPosition> {
  const depthById = calculateCycleSafeNodeDepths(nodes, edges);
  const nodeIds = new Set(nodes.map((node) => node.id));
  const outgoing = createOutgoingEdgeMap(edges, nodeIds);
  const incoming = createIncomingEdgeMap(edges, nodeIds);
  const spanByNodeId = new Map<string, number>();
  const laneSuggestionsByNodeId = new Map<string, number[]>();
  const maxTraversalSteps = Math.max(1, nodes.length * Math.max(1, edges.length) * 2);
  const rootIds = findLayoutRootIds(nodes, incoming);
  let traversalSteps = 0;
  let nextRootLane = 0;

  const measureSpan = (nodeId: string, path: ReadonlySet<string>): number => {
    const cachedSpan = spanByNodeId.get(nodeId);
    if (cachedSpan !== undefined) {
      return cachedSpan;
    }

    if (path.has(nodeId) || traversalSteps > maxTraversalSteps) {
      return 1;
    }

    traversalSteps += 1;
    const nextPath = new Set(path);
    nextPath.add(nodeId);
    const forwardEdges = getForwardLayoutEdges(nodeId, outgoing, depthById, nextPath);
    if (forwardEdges.length === 0) {
      spanByNodeId.set(nodeId, 1);
      return 1;
    }

    const span =
      forwardEdges.length === 1
        ? measureSpan(forwardEdges[0].target, nextPath)
        : forwardEdges.reduce((total, edge) => total + measureSpan(edge.target, nextPath), 0);
    const normalizedSpan = Math.max(1, span);
    spanByNodeId.set(nodeId, normalizedSpan);
    return normalizedSpan;
  };

  const suggestLane = (nodeId: string, lane: number) => {
    laneSuggestionsByNodeId.set(nodeId, [...(laneSuggestionsByNodeId.get(nodeId) ?? []), lane]);
  };

  const assignLanes = (nodeId: string, topLane: number, path: ReadonlySet<string>) => {
    if (path.has(nodeId) || traversalSteps > maxTraversalSteps) {
      return;
    }

    traversalSteps += 1;
    const span = measureSpan(nodeId, path);
    suggestLane(nodeId, topLane + (span - 1) / 2);

    const nextPath = new Set(path);
    nextPath.add(nodeId);
    const forwardEdges = getForwardLayoutEdges(nodeId, outgoing, depthById, nextPath);
    if (forwardEdges.length === 0) {
      return;
    }

    if (forwardEdges.length === 1) {
      assignLanes(forwardEdges[0].target, topLane, nextPath);
      return;
    }

    let childTopLane = topLane;
    for (const edge of sortEdgesForLayout(forwardEdges)) {
      assignLanes(edge.target, childTopLane, nextPath);
      childTopLane += measureSpan(edge.target, nextPath);
    }
  };

  for (const rootId of rootIds) {
    const rootSpan = measureSpan(rootId, new Set());
    assignLanes(rootId, nextRootLane, new Set());
    nextRootLane += rootSpan + 1;
  }

  for (const node of nodes) {
    if (laneSuggestionsByNodeId.has(node.id)) {
      continue;
    }

    suggestLane(node.id, nextRootLane);
    nextRootLane += 1;
  }

  const layout = Object.fromEntries(
    nodes.map((node) => {
      const depth = depthById.get(node.id) ?? 0;
      const lanes = laneSuggestionsByNodeId.get(node.id) ?? [0];
      const lane = average(lanes);

      return [
        node.id,
        {
          x: START_X + depth * COLUMN_SPACING,
          y: START_Y + lane * ROW_SPACING,
        },
      ];
    }),
  );

  return resolveColumnOverlaps(nodes, layout, depthById);
}

function createOutgoingEdgeMap(
  edges: FlowEdgeDTO[],
  nodeIds: Set<string>,
): Map<string, FlowEdgeDTO[]> {
  const outgoing = new Map<string, FlowEdgeDTO[]>();

  for (const edge of edges) {
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) {
      continue;
    }

    outgoing.set(edge.source, [...(outgoing.get(edge.source) ?? []), edge]);
  }

  return outgoing;
}

function createIncomingEdgeMap(
  edges: FlowEdgeDTO[],
  nodeIds: Set<string>,
): Map<string, FlowEdgeDTO[]> {
  const incoming = new Map<string, FlowEdgeDTO[]>();

  for (const edge of edges) {
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) {
      continue;
    }

    incoming.set(edge.target, [...(incoming.get(edge.target) ?? []), edge]);
  }

  return incoming;
}

function findLayoutRootIds(nodes: FlowNodeDTO[], incoming: Map<string, FlowEdgeDTO[]>): string[] {
  const startNodeIds = nodes
    .filter((node) => !NODE_TYPE_REGISTRY[node.type].canReceiveInput)
    .map((node) => node.id);
  if (startNodeIds.length > 0) {
    return startNodeIds;
  }

  const roots = nodes.filter((node) => (incoming.get(node.id) ?? []).length === 0);
  return roots.length > 0 ? roots.map((node) => node.id) : nodes[0] ? [nodes[0].id] : [];
}

function getForwardLayoutEdges(
  nodeId: string,
  outgoing: Map<string, FlowEdgeDTO[]>,
  depthById: Map<string, number>,
  path: ReadonlySet<string>,
): FlowEdgeDTO[] {
  const currentDepth = depthById.get(nodeId) ?? 0;
  return sortEdgesForLayout(
    (outgoing.get(nodeId) ?? []).filter((edge) => {
      const targetDepth = depthById.get(edge.target) ?? 0;
      return targetDepth > currentDepth && !path.has(edge.target);
    }),
  );
}

function sortEdgesForLayout(edges: FlowEdgeDTO[]): FlowEdgeDTO[] {
  return [...edges].sort((first, second) => {
    const semanticDelta =
      branchOrderIndex(resolveEdgeSemantic(first)) - branchOrderIndex(resolveEdgeSemantic(second));
    if (semanticDelta !== 0) {
      return semanticDelta;
    }

    return first.id.localeCompare(second.id);
  });
}

function branchOrderIndex(semantic: FlowEdgeSemantic): number {
  const index = EDGE_BRANCH_LAYOUT_ORDER.indexOf(semantic);
  return index >= 0 ? index : EDGE_BRANCH_LAYOUT_ORDER.length;
}

function resolveColumnOverlaps(
  nodes: FlowNodeDTO[],
  layout: Record<string, XYPosition>,
  depthById: Map<string, number>,
): Record<string, XYPosition> {
  const nodesByDepth = new Map<number, FlowNodeDTO[]>();
  for (const node of nodes) {
    const depth = depthById.get(node.id) ?? 0;
    nodesByDepth.set(depth, [...(nodesByDepth.get(depth) ?? []), node]);
  }

  const resolvedLayout = { ...layout };
  for (const depthNodes of nodesByDepth.values()) {
    const orderedNodes = [...depthNodes].sort(
      (first, second) =>
        (resolvedLayout[first.id]?.y ?? START_Y) - (resolvedLayout[second.id]?.y ?? START_Y),
    );
    let nextY = Number.NEGATIVE_INFINITY;

    for (const node of orderedNodes) {
      const size = getFlowNodeLayoutSize(node);
      const current = resolvedLayout[node.id] ?? { x: START_X, y: START_Y };
      const y = Math.max(current.y, nextY);
      resolvedLayout[node.id] = {
        x: current.x,
        y,
      };
      nextY = y + size.height + FLOW_LAYOUT_CONFIG.minRowGap;
    }
  }

  return Object.fromEntries(
    Object.entries(resolvedLayout).map(([nodeId, position]) => [
      nodeId,
      {
        x: Math.round(position.x),
        y: Math.round(position.y),
      },
    ]),
  );
}

function hasUnreadableNodeSpacing(nodes: FlowNodeDTO[], edges: FlowEdgeDTO[]): boolean {
  const nodesById = new Map(nodes.map((node) => [node.id, node]));
  const positionsByNodeId = new Map(
    nodes
      .filter((node): node is FlowNodeDTO & { position: XYPosition } => Boolean(node.position))
      .map((node) => [node.id, node.position]),
  );

  if (positionsByNodeId.size !== nodes.length) {
    return true;
  }

  for (const position of positionsByNodeId.values()) {
    if (!Number.isFinite(position.x) || !Number.isFinite(position.y)) {
      return true;
    }
  }

  for (const edge of edges) {
    const sourceNode = nodesById.get(edge.source);
    const sourcePosition = positionsByNodeId.get(edge.source);
    const targetPosition = positionsByNodeId.get(edge.target);
    if (!sourceNode || !sourcePosition || !targetPosition) {
      continue;
    }

    const sourceSize = getFlowNodeLayoutSize(sourceNode);
    const horizontalGap = targetPosition.x - (sourcePosition.x + sourceSize.width);
    if (targetPosition.x > sourcePosition.x && horizontalGap < FLOW_LAYOUT_CONFIG.minColumnGap) {
      return true;
    }
  }

  return false;
}

function getFlowNodeLayoutSize(node: FlowNodeDTO): FlowNodeCanvasSize {
  const nodeKind = node.nodeKind ?? detectNodeKind(node.label, node.type);
  return getNodeCanvasSize(nodeKind, getNodeLabelRows(node.label, nodeKind));
}

function average(values: number[]): number {
  return values.reduce((total, value) => total + value, 0) / values.length;
}

function firstText(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function getReactFlowNodeTitle(node: RequirementFlowNode): string {
  return typeof node.data.label === "string"
    ? node.data.label
    : (node.data.title ?? "Untitled node");
}

function createNodeHandles(
  nodeType: FlowNodeType,
  nodeKind: FlowNodeKind,
  canvasSize: FlowNodeCanvasSize,
): NodeHandle[] {
  const nodeTypeConfig = NODE_TYPE_REGISTRY[nodeType];
  const centerY = canvasSize.height / 2 - HANDLE_SIZE / 2;
  const handles: NodeHandle[] = [];

  if (nodeTypeConfig.canReceiveInput) {
    handles.push({
      id: FLOW_HANDLES.INPUT,
      type: "target",
      position: Position.Left,
      x: -HANDLE_SIZE / 2,
      y: centerY,
      width: HANDLE_SIZE,
      height: HANDLE_SIZE,
    });
  }

  if (!nodeTypeConfig.canCreateOutput) {
    return handles;
  }

  if (nodeKind === NODE_TYPE_REGISTRY.DECISION.nodeKind) {
    handles.push(
      createRightHandle(FLOW_HANDLES.YES, "source", canvasSize, 0.36),
      createRightHandle(FLOW_HANDLES.NO, "source", canvasSize, 0.64),
    );
    return handles;
  }

  handles.push(createRightHandle(FLOW_HANDLES.OUTPUT, "source", canvasSize, 0.5));
  return handles;
}

function createRightHandle(
  id: FlowHandleId,
  type: "source" | "target",
  canvasSize: FlowNodeCanvasSize,
  verticalRatio: number,
): NodeHandle {
  return {
    id,
    type,
    position: Position.Right,
    x: canvasSize.width - HANDLE_SIZE / 2,
    y: canvasSize.height * verticalRatio - HANDLE_SIZE / 2,
    width: HANDLE_SIZE,
    height: HANDLE_SIZE,
  };
}

function getNodeCanvasSize(nodeKind: FlowNodeKind, labelRows: number): FlowNodeCanvasSize {
  const nodeKindConfig = NODE_KIND_REGISTRY[nodeKind];
  const extraHeight = Math.max(0, labelRows - 1) * nodeKindConfig.labelLayout.rowHeight;
  const height = nodeKindConfig.canvasSize.height + extraHeight;

  if (nodeKindConfig.shape === "diamond") {
    return {
      height,
      width: Math.max(nodeKindConfig.canvasSize.width, height),
    };
  }

  return {
    height,
    width: nodeKindConfig.canvasSize.width,
  };
}

function getNodeLabelRows(label: string, nodeKind: FlowNodeKind): number {
  const { charsPerLine, maxRows } = NODE_KIND_REGISTRY[nodeKind].labelLayout;
  const rowCount = label.split(/\r?\n/).reduce((total, line) => {
    const visibleCharacters = Math.max(1, line.trim().length);
    return total + Math.ceil(visibleCharacters / charsPerLine);
  }, 0);

  return Math.min(maxRows, Math.max(1, rowCount));
}

function enumeratePaths(flowchart: FlowchartDTO): FlowPath[] {
  const nodesById = new Map(flowchart.nodes.map((node) => [node.id, node]));
  const outgoing = new Map<string, FlowEdgeDTO[]>();
  for (const edge of flowchart.edges) {
    outgoing.set(edge.source, [...(outgoing.get(edge.source) ?? []), edge]);
  }

  const start =
    flowchart.nodes.find((node) => !NODE_TYPE_REGISTRY[node.type].canReceiveInput) ??
    flowchart.nodes[0];
  if (!start) {
    return [];
  }

  const paths: FlowPath[] = [];
  const maxDepth = flowchart.nodes.length + 5;

  function walk(current: FlowNodeDTO, steps: FlowPathStep[], visited: Set<string>) {
    if (steps.length > maxDepth || !NODE_TYPE_REGISTRY[current.type].canCreateOutput) {
      paths.push(steps);
      return;
    }

    const nextEdges = outgoing.get(current.id) ?? [];
    if (nextEdges.length === 0) {
      paths.push(steps);
      return;
    }

    for (const edge of nextEdges) {
      const nextNode = nodesById.get(edge.target);
      if (!nextNode || visited.has(nextNode.id)) {
        continue;
      }

      walk(
        nextNode,
        [
          ...steps,
          {
            node: nextNode,
            incomingLabel: getEdgeConditionText(edge),
          },
        ],
        new Set([...visited, nextNode.id]),
      );
    }
  }

  walk(start, [{ node: start }], new Set([start.id]));
  return paths;
}

function buildPathRequirementLines(paths: FlowPath[]): string[] {
  const commonPrefix = findCommonPrefix(paths.map(getActionPrefixBeforeFirstBranch));
  const lines = [...commonPrefix];

  for (const path of paths) {
    const firstBranchIndex = path.findIndex((step) => Boolean(step.incomingLabel?.trim()));
    if (firstBranchIndex >= 0) {
      const condition = path[firstBranchIndex].incomingLabel?.trim() ?? "condition";
      const outcomes = path
        .slice(firstBranchIndex)
        .map((step) => step.node)
        .filter((node) => !NODE_TYPE_REGISTRY[node.type].isTerminal)
        .map((node) => node.label)
        .filter((label) => !commonPrefix.includes(label));

      lines.push(`If ${condition}, ${joinActionSequence(outcomes)}`);
      continue;
    }

    const actions = path
      .map((step) => step.node)
      .filter((node) => !NODE_TYPE_REGISTRY[node.type].isTerminal)
      .map((node) => node.label)
      .filter((label) => !commonPrefix.includes(label));

    if (actions.length > 0) {
      lines.push(joinActionSequence(actions));
    }
  }

  return Array.from(new Set(lines.filter(Boolean)));
}

function getActionPrefixBeforeFirstBranch(path: FlowPath): string[] {
  const prefix: string[] = [];
  for (const step of path) {
    if (step.incomingLabel?.trim()) {
      break;
    }

    if (NODE_TYPE_REGISTRY[step.node.type].isAction) {
      prefix.push(step.node.label);
    }
  }
  return prefix;
}

function findCommonPrefix(prefixes: string[][]): string[] {
  if (prefixes.length === 0) {
    return [];
  }

  const [firstPrefix, ...rest] = prefixes;
  return firstPrefix.filter((label, index) => rest.every((prefix) => prefix[index] === label));
}

function joinActionSequence(actions: string[]): string {
  if (actions.length === 0) {
    return "complete the path";
  }

  return actions.join(", then ");
}

function createNextNodeId(nodes: FlowNodeDTO[]): string {
  let index = nodes.length + 1;
  while (nodes.some((node) => node.id === `node_${index}`)) {
    index++;
  }
  return `node_${index}`;
}

function defaultLabelByNodeKind(nodeKind: FlowNodeKind): string {
  return NODE_KIND_REGISTRY[nodeKind].defaultLabel;
}

function defaultTypeByNodeKind(nodeKind: FlowNodeKind): FlowNodeType {
  return NODE_KIND_REGISTRY[nodeKind].defaultType;
}

function createNextEdgeId(edges: FlowEdgeDTO[]): string {
  let index = edges.length + 1;
  while (edges.some((edge) => edge.id === `edge_${index}`)) {
    index++;
  }
  return `edge_${index}`;
}

function findNewNodePosition(nodes: FlowNodeDTO[]): XYPosition {
  const maxX = Math.max(START_X, ...nodes.map((node) => node.position?.x ?? START_X));
  return {
    x: maxX + COLUMN_SPACING,
    y: START_Y + (nodes.length % 4) * ROW_SPACING,
  };
}

function roundPosition(position: XYPosition): XYPosition {
  return {
    x: Math.round(position.x),
    y: Math.round(position.y),
  };
}

function formatMermaidNode(node: FlowNodeDTO): string {
  const label = escapeMermaid(node.label);
  return MERMAID_NODE_FORMATTERS[NODE_TYPE_REGISTRY[node.type].mermaidShape](label);
}

const MERMAID_NODE_FORMATTERS = {
  decision: (label: string) => `{"${label}"}`,
  process: (label: string) => `["${label}"]`,
  terminal: (label: string) => `(["${label}"])`,
} as const satisfies Record<MermaidNodeShape, (label: string) => string>;

function escapeMermaid(value: string): string {
  return value.replaceAll("\\", "\\\\").replaceAll('"', '\\"').replaceAll("|", "\\|");
}

function normalizeForMatch(value: string): string {
  return value
    .normalize("NFD")
    .replaceAll(/\p{M}/gu, "")
    .replaceAll("đ", "d")
    .replaceAll("Đ", "D")
    .toLowerCase();
}

function containsAny(value: string, ...candidates: string[]): boolean {
  return candidates.some((candidate) => value.includes(candidate));
}

function getSourceHandleForEdge(
  semantic: FlowEdgeSemantic,
  sourceNode?: FlowNodeDTO,
): FlowHandleId {
  if (sourceNode && isDecisionFlowNode(sourceNode)) {
    if (
      semantic === FLOW_HANDLE_REGISTRY[FLOW_HANDLES.NO].semantic ||
      semantic === FLOW_HANDLE_REGISTRY[FLOW_HANDLES.FAILURE].semantic ||
      semantic === FLOW_HANDLE_REGISTRY[FLOW_HANDLES.CANCEL].semantic ||
      semantic === FLOW_HANDLE_REGISTRY[FLOW_HANDLES.TIMEOUT].semantic
    ) {
      return FLOW_HANDLES.NO;
    }

    if (
      semantic === FLOW_HANDLE_REGISTRY[FLOW_HANDLES.YES].semantic ||
      semantic === FLOW_HANDLE_REGISTRY[FLOW_HANDLES.SUCCESS].semantic
    ) {
      return FLOW_HANDLES.YES;
    }

    return FLOW_HANDLES.YES;
  }

  return FLOW_HANDLES.OUTPUT;
}

function isDecisionFlowNode(node: Pick<FlowNodeDTO, "nodeKind" | "type">): boolean {
  return (
    node.type === NODE_KIND_REGISTRY.DECISION.defaultType ||
    node.nodeKind === NODE_TYPE_REGISTRY.DECISION.nodeKind
  );
}

function isFlowHandleId(value?: string | null): value is FlowHandleId {
  return Boolean(value && value in FLOW_HANDLE_REGISTRY);
}

type FlowPathStep = {
  node: FlowNodeDTO;
  incomingLabel?: string;
};

type FlowPath = FlowPathStep[];
