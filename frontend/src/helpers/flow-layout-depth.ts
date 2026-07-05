import type { FlowEdgeDTO, FlowNodeDTO } from "@/types/requirement";

type DepthQueueItem = {
  nodeId: string;
  path: ReadonlySet<string>;
};

type CycleSafeDepthOptions = {
  rootIds?: string[];
};

export function calculateCycleSafeNodeDepths(
  nodes: FlowNodeDTO[],
  edges: FlowEdgeDTO[],
  options: CycleSafeDepthOptions = {},
): Map<string, number> {
  const nodeIds = new Set(nodes.map((node) => node.id));
  const outgoing = createOutgoingEdgeMap(edges, nodeIds);
  const depthById = new Map(nodes.map((node) => [node.id, 0]));
  const settledNodeIds = new Set<string>();
  const rootIds = normalizeRootIds(options.rootIds, nodes, edges, nodeIds);
  const queue: DepthQueueItem[] = rootIds.map((nodeId) => ({
    nodeId,
    path: new Set<string>(),
  }));
  const maxTraversalSteps = Math.max(1, nodes.length * Math.max(1, edges.length) * 2);
  let traversalSteps = 0;

  while (queue.length > 0 && traversalSteps < maxTraversalSteps) {
    traversalSteps += 1;
    const current = queue.shift();
    if (!current || settledNodeIds.has(current.nodeId)) {
      continue;
    }

    settledNodeIds.add(current.nodeId);
    const currentDepth = depthById.get(current.nodeId) ?? 0;
    const nextPath = new Set(current.path);
    nextPath.add(current.nodeId);

    for (const edge of outgoing.get(current.nodeId) ?? []) {
      if (nextPath.has(edge.target) || settledNodeIds.has(edge.target)) {
        continue;
      }

      depthById.set(edge.target, Math.max(depthById.get(edge.target) ?? 0, currentDepth + 1));
      queue.push({
        nodeId: edge.target,
        path: nextPath,
      });
    }
  }

  return depthById;
}

function normalizeRootIds(
  rootIds: string[] | undefined,
  nodes: FlowNodeDTO[],
  edges: FlowEdgeDTO[],
  nodeIds: Set<string>,
): string[] {
  const explicitRoots = rootIds?.filter((nodeId) => nodeIds.has(nodeId)) ?? [];
  if (explicitRoots.length > 0) {
    return explicitRoots;
  }

  const incomingCount = new Map(nodes.map((node) => [node.id, 0]));
  for (const edge of edges) {
    if (nodeIds.has(edge.source) && nodeIds.has(edge.target)) {
      incomingCount.set(edge.target, (incomingCount.get(edge.target) ?? 0) + 1);
    }
  }

  const rootNodeIds = nodes
    .filter((node) => (incomingCount.get(node.id) ?? 0) === 0)
    .map((node) => node.id);

  return rootNodeIds.length > 0 ? rootNodeIds : nodes[0] ? [nodes[0].id] : [];
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
