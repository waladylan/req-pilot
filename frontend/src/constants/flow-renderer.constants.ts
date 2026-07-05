import { EDGE_REGISTRY } from "./edge.constants";
import {
  NODE_KIND_REGISTRY,
  NODE_TYPE_REGISTRY,
  type MermaidNodeShape,
  type NodeShape,
} from "./flow.constants";
import { WFM_NODE_ROLE_REGISTRY, WFM_TRANSITION_SEMANTIC_REGISTRY } from "./wfm.constants";

type NodeRenderRegistryItem = {
  className: string;
  defaultTitle: string;
  description: string;
  flowNodeKind: keyof typeof NODE_KIND_REGISTRY;
  flowNodeType: keyof typeof NODE_TYPE_REGISTRY;
  label: string;
  mermaidShape: MermaidNodeShape;
  miniMapColor: string;
  shape: NodeShape;
};

type EdgeRenderRegistryItem = {
  color: string;
  description: string;
  label: string;
};

export const NODE_RENDER_REGISTRY = {
  START: {
    className: NODE_KIND_REGISTRY.START_END.className,
    defaultTitle: "Start",
    description: WFM_NODE_ROLE_REGISTRY.START.description,
    flowNodeKind: "START_END",
    flowNodeType: "START",
    label: WFM_NODE_ROLE_REGISTRY.START.label,
    mermaidShape: "terminal",
    miniMapColor: NODE_KIND_REGISTRY.START_END.miniMapColor,
    shape: "pill",
  },
  ACTION: {
    className: NODE_KIND_REGISTRY.ACTION.className,
    defaultTitle: NODE_KIND_REGISTRY.ACTION.defaultLabel,
    description: WFM_NODE_ROLE_REGISTRY.ACTION.description,
    flowNodeKind: "ACTION",
    flowNodeType: "ACTION",
    label: WFM_NODE_ROLE_REGISTRY.ACTION.label,
    mermaidShape: "process",
    miniMapColor: NODE_KIND_REGISTRY.ACTION.miniMapColor,
    shape: "rectangle",
  },
  DECISION: {
    className: NODE_KIND_REGISTRY.DECISION.className,
    defaultTitle: NODE_KIND_REGISTRY.DECISION.defaultLabel,
    description: WFM_NODE_ROLE_REGISTRY.DECISION.description,
    flowNodeKind: "DECISION",
    flowNodeType: "DECISION",
    label: WFM_NODE_ROLE_REGISTRY.DECISION.label,
    mermaidShape: "decision",
    miniMapColor: NODE_KIND_REGISTRY.DECISION.miniMapColor,
    shape: "diamond",
  },
  INPUT: {
    className: NODE_KIND_REGISTRY.INPUT_OUTPUT.className,
    defaultTitle: "Input Data",
    description: WFM_NODE_ROLE_REGISTRY.INPUT.description,
    flowNodeKind: "INPUT_OUTPUT",
    flowNodeType: "ACTION",
    label: WFM_NODE_ROLE_REGISTRY.INPUT.label,
    mermaidShape: "process",
    miniMapColor: NODE_KIND_REGISTRY.INPUT_OUTPUT.miniMapColor,
    shape: "parallelogram",
  },
  OUTPUT: {
    className: NODE_KIND_REGISTRY.INPUT_OUTPUT.className,
    defaultTitle: "Show Output",
    description: WFM_NODE_ROLE_REGISTRY.OUTPUT.description,
    flowNodeKind: "INPUT_OUTPUT",
    flowNodeType: "ACTION",
    label: WFM_NODE_ROLE_REGISTRY.OUTPUT.label,
    mermaidShape: "process",
    miniMapColor: NODE_KIND_REGISTRY.INPUT_OUTPUT.miniMapColor,
    shape: "parallelogram",
  },
  ERROR: {
    className: NODE_KIND_REGISTRY.ERROR.className,
    defaultTitle: NODE_KIND_REGISTRY.ERROR.defaultLabel,
    description: WFM_NODE_ROLE_REGISTRY.ERROR.description,
    flowNodeKind: "ERROR",
    flowNodeType: "ACTION",
    label: WFM_NODE_ROLE_REGISTRY.ERROR.label,
    mermaidShape: "process",
    miniMapColor: NODE_KIND_REGISTRY.ERROR.miniMapColor,
    shape: "error",
  },
  END: {
    className: NODE_KIND_REGISTRY.START_END.className,
    defaultTitle: "End",
    description: WFM_NODE_ROLE_REGISTRY.END.description,
    flowNodeKind: "START_END",
    flowNodeType: "END",
    label: WFM_NODE_ROLE_REGISTRY.END.label,
    mermaidShape: "terminal",
    miniMapColor: NODE_KIND_REGISTRY.START_END.miniMapColor,
    shape: "pill",
  },
  SUBPROCESS: {
    className: NODE_KIND_REGISTRY.ACTION.className,
    defaultTitle: "Run Subprocess",
    description: WFM_NODE_ROLE_REGISTRY.SUBPROCESS.description,
    flowNodeKind: "ACTION",
    flowNodeType: "ACTION",
    label: WFM_NODE_ROLE_REGISTRY.SUBPROCESS.label,
    mermaidShape: "process",
    miniMapColor: NODE_KIND_REGISTRY.ACTION.miniMapColor,
    shape: "rectangle",
  },
} as const satisfies Record<keyof typeof WFM_NODE_ROLE_REGISTRY, NodeRenderRegistryItem>;

export const EDGE_RENDER_REGISTRY = {
  DEFAULT: {
    color: EDGE_REGISTRY.DEFAULT.color,
    description: WFM_TRANSITION_SEMANTIC_REGISTRY.DEFAULT.description,
    label: WFM_TRANSITION_SEMANTIC_REGISTRY.DEFAULT.label,
  },
  YES: {
    color: EDGE_REGISTRY.YES.color,
    description: WFM_TRANSITION_SEMANTIC_REGISTRY.YES.description,
    label: WFM_TRANSITION_SEMANTIC_REGISTRY.YES.label,
  },
  NO: {
    color: EDGE_REGISTRY.NO.color,
    description: WFM_TRANSITION_SEMANTIC_REGISTRY.NO.description,
    label: WFM_TRANSITION_SEMANTIC_REGISTRY.NO.label,
  },
  SUCCESS: {
    color: EDGE_REGISTRY.SUCCESS.color,
    description: WFM_TRANSITION_SEMANTIC_REGISTRY.SUCCESS.description,
    label: WFM_TRANSITION_SEMANTIC_REGISTRY.SUCCESS.label,
  },
  FAILURE: {
    color: EDGE_REGISTRY.FAILURE.color,
    description: WFM_TRANSITION_SEMANTIC_REGISTRY.FAILURE.description,
    label: WFM_TRANSITION_SEMANTIC_REGISTRY.FAILURE.label,
  },
  CANCEL: {
    color: EDGE_REGISTRY.CANCEL.color,
    description: WFM_TRANSITION_SEMANTIC_REGISTRY.CANCEL.description,
    label: WFM_TRANSITION_SEMANTIC_REGISTRY.CANCEL.label,
  },
  RETRY: {
    color: EDGE_REGISTRY.RETRY.color,
    description: WFM_TRANSITION_SEMANTIC_REGISTRY.RETRY.description,
    label: WFM_TRANSITION_SEMANTIC_REGISTRY.RETRY.label,
  },
  TIMEOUT: {
    color: EDGE_REGISTRY.TIMEOUT.color,
    description: WFM_TRANSITION_SEMANTIC_REGISTRY.TIMEOUT.description,
    label: WFM_TRANSITION_SEMANTIC_REGISTRY.TIMEOUT.label,
  },
} as const satisfies Record<keyof typeof WFM_TRANSITION_SEMANTIC_REGISTRY, EdgeRenderRegistryItem>;

export const NODE_RENDER_ORDER = [
  "START",
  "ACTION",
  "DECISION",
  "INPUT",
  "OUTPUT",
  "ERROR",
  "END",
  "SUBPROCESS",
] as const satisfies ReadonlyArray<keyof typeof NODE_RENDER_REGISTRY>;

export const EDGE_RENDER_ORDER = [
  "YES",
  "NO",
  "SUCCESS",
  "FAILURE",
  "CANCEL",
  "RETRY",
  "TIMEOUT",
  "DEFAULT",
] as const satisfies ReadonlyArray<keyof typeof EDGE_RENDER_REGISTRY>;
