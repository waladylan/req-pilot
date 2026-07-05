import {
  DEFAULT_EDGE_SEMANTIC,
  EDGE_REGISTRY,
  NODE_RENDER_REGISTRY,
  type MermaidNodeShape,
} from "@/constants";
import type {
  FlowEdgeSemantic,
  FlowchartDTO,
  WfmDocument,
  WfmNodeRole,
  WorkflowCanvasViewState,
} from "@/types/requirement";

export function wfmToFlowchartDTO(
  wfm: WfmDocument,
  canvasViewState?: WorkflowCanvasViewState,
): FlowchartDTO {
  return {
    nodes: wfm.ast.nodes.map((node) => {
      const renderer = NODE_RENDER_REGISTRY[node.role];
      return {
        id: node.id,
        label: node.title,
        title: node.title,
        nodeKind: renderer.flowNodeKind,
        type: renderer.flowNodeType,
        description: node.description,
        position: canvasViewState?.nodePositions[node.id],
      };
    }),
    edges: wfm.ast.transitions.map((transition) => ({
      data: {
        condition: transition.condition,
        label: transition.condition,
      },
      id: transition.id,
      source: transition.from,
      target: transition.to,
      label: transition.condition,
      type: isFlowEdgeSemantic(transition.semantic) ? transition.semantic : DEFAULT_EDGE_SEMANTIC,
    })),
    mermaid: buildMermaidFromWfm(wfm),
  };
}

export function createCanvasViewStateFromFlowchart(
  workflowId: string,
  flowchart: FlowchartDTO,
): WorkflowCanvasViewState {
  return {
    workflowId,
    nodePositions: Object.fromEntries(
      flowchart.nodes
        .filter((node) => node.position)
        .map((node) => [node.id, { x: node.position?.x ?? 0, y: node.position?.y ?? 0 }]),
    ),
  };
}

export function mergeCanvasViewStateFromFlowchart(
  current: WorkflowCanvasViewState | undefined,
  workflowId: string,
  flowchart: FlowchartDTO,
): WorkflowCanvasViewState {
  return {
    workflowId,
    viewport: current?.workflowId === workflowId ? current.viewport : undefined,
    nodePositions: {
      ...(current?.workflowId === workflowId ? current.nodePositions : {}),
      ...createCanvasViewStateFromFlowchart(workflowId, flowchart).nodePositions,
    },
  };
}

export function buildMermaidFromWfm(wfm: WfmDocument): string {
  const nodeLines = wfm.ast.nodes.map((node) => {
    const label = escapeMermaid(node.title);
    const shape = roleToMermaidShape(node.role, label);
    return `  ${node.id}${shape}`;
  });
  const edgeLines = wfm.ast.transitions.map((transition) => {
    const condition = transition.condition?.trim();
    const label = condition ? `|${escapeMermaid(condition)}| ` : "";
    return `  ${transition.from} --> ${label}${transition.to}`;
  });

  return ["flowchart LR", ...nodeLines, ...edgeLines].join("\n");
}

function roleToMermaidShape(role: WfmNodeRole, label: string) {
  return MERMAID_NODE_FORMATTERS[NODE_RENDER_REGISTRY[role].mermaidShape](label);
}

function isFlowEdgeSemantic(value: string): value is FlowEdgeSemantic {
  return value in EDGE_REGISTRY;
}

function escapeMermaid(value: string) {
  return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\|/g, "\\|");
}

const MERMAID_NODE_FORMATTERS = {
  decision: (label: string) => `{"${label}"}`,
  process: (label: string) => `["${label}"]`,
  terminal: (label: string) => `(["${label}"])`,
} as const satisfies Record<MermaidNodeShape, (label: string) => string>;
