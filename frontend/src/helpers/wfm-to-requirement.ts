import { EDGE_REGISTRY, WFM_NODE_ROLE_REGISTRY } from "@/constants";
import type { WfmDocument, WfmNode, WfmTransition } from "@/types/requirement";

type WfmPathStep = {
  incoming?: WfmTransition;
  node: WfmNode;
};

export function wfmToRequirementText(wfm: WfmDocument): string {
  const paths = enumerateWfmPaths(wfm);
  const lines = paths.flatMap((path) => pathToRequirementLines(path));
  const uniqueLines = Array.from(new Set(lines.filter(Boolean)));

  return [
    `Feature: ${wfm.workflow.title}`,
    "",
    ...(uniqueLines.length > 0
      ? uniqueLines.map((line, index) => `${index + 1}. ${line}`)
      : ["1. Review the workflow nodes and transitions."]),
  ].join("\n");
}

function enumerateWfmPaths(wfm: WfmDocument): WfmPathStep[][] {
  const nodesById = new Map(wfm.ast.nodes.map((node) => [node.id, node]));
  const outgoing = new Map<string, WfmTransition[]>();
  for (const transition of wfm.ast.transitions) {
    outgoing.set(transition.from, [...(outgoing.get(transition.from) ?? []), transition]);
  }

  const start = wfm.ast.nodes.find((node) => node.role === "START") ?? wfm.ast.nodes[0];
  if (!start) {
    return [];
  }

  const paths: WfmPathStep[][] = [];
  const maxDepth = wfm.ast.nodes.length + 5;

  function walk(current: WfmNode, steps: WfmPathStep[], visited: Set<string>) {
    if (current.role === "END" || steps.length > maxDepth) {
      paths.push(steps);
      return;
    }

    const nextTransitions = outgoing.get(current.id) ?? [];
    if (nextTransitions.length === 0) {
      paths.push(steps);
      return;
    }

    for (const transition of nextTransitions) {
      const nextNode = nodesById.get(transition.to);
      if (!nextNode || visited.has(nextNode.id)) {
        paths.push([...steps, { incoming: transition, node: current }]);
        continue;
      }
      walk(
        nextNode,
        [...steps, { incoming: transition, node: nextNode }],
        new Set([...visited, nextNode.id]),
      );
    }
  }

  walk(start, [{ node: start }], new Set([start.id]));
  return paths;
}

function pathToRequirementLines(path: WfmPathStep[]): string[] {
  const lines: string[] = [];

  for (const step of path) {
    if (step.node.role === "START" || step.node.role === "END") {
      continue;
    }

    const prefix = transitionPrefix(step.incoming);
    const roleLabel = WFM_NODE_ROLE_REGISTRY[step.node.role].label;
    lines.push(prefix ? `${prefix}, ${step.node.title}` : `${roleLabel}: ${step.node.title}`);
  }

  return lines;
}

function transitionPrefix(transition?: WfmTransition) {
  if (!transition || transition.semantic === "DEFAULT") {
    return "";
  }

  const condition = transition.condition?.trim();
  const semanticLabel = EDGE_REGISTRY[transition.semantic].label;
  return condition ? `If ${condition}` : `If ${semanticLabel}`;
}
