import type {
  EDGE_REGISTRY,
  FLOW_HANDLE_REGISTRY,
  NODE_KIND_REGISTRY,
  NODE_TYPE_REGISTRY,
} from "@/constants";

export type FlowNodeType = keyof typeof NODE_TYPE_REGISTRY;
export type FlowNodeKind = keyof typeof NODE_KIND_REGISTRY;
export type FlowEdgeSemantic = keyof typeof EDGE_REGISTRY;
export type FlowHandleId = keyof typeof FLOW_HANDLE_REGISTRY;

export type FlowNodePosition = {
  x: number;
  y: number;
};

export type FlowNodeDTO = {
  id: string;
  label: string;
  title?: string;
  type: FlowNodeType;
  nodeKind?: FlowNodeKind;
  description?: string;
  precondition?: string;
  expectedResult?: string;
  position?: FlowNodePosition;
};

export type FlowEdgeDTO = {
  data?: Record<string, unknown> & {
    condition?: string | null;
    label?: string | null;
  };
  id: string;
  source: string;
  target: string;
  label?: string;
  type?: FlowEdgeSemantic;
};

export type FlowchartDTO = {
  nodes: FlowNodeDTO[];
  edges: FlowEdgeDTO[];
  mermaid?: string;
};
