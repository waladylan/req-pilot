export type NodeShape = "pill" | "rectangle" | "diamond" | "parallelogram" | "error";
export type MermaidNodeShape = "terminal" | "process" | "decision";

type NodeTypeRegistryItem = {
  canCreateOutput: boolean;
  canReceiveInput: boolean;
  defaultLabel: string;
  isAction: boolean;
  isTerminal: boolean;
  label: string;
  mermaidShape: MermaidNodeShape;
  nodeKind: string;
  shape: NodeShape;
};

type NodeKindRegistryItem = {
  canvasSize: {
    height: number;
    width: number;
  };
  className: string;
  defaultLabel: string;
  defaultType: string;
  description: string;
  label: string;
  labelLayout: {
    charsPerLine: number;
    maxRows: number;
    rowHeight: number;
  };
  miniMapColor: string;
  shape: NodeShape;
  showWarningIcon?: boolean;
};

export const NODE_TYPE_REGISTRY = {
  START: {
    canCreateOutput: true,
    canReceiveInput: false,
    defaultLabel: "Start",
    isAction: false,
    isTerminal: true,
    label: "Start",
    mermaidShape: "terminal",
    nodeKind: "START_END",
    shape: "pill",
  },
  ACTION: {
    canCreateOutput: true,
    canReceiveInput: true,
    defaultLabel: "New Action",
    isAction: true,
    isTerminal: false,
    label: "Action",
    mermaidShape: "process",
    nodeKind: "ACTION",
    shape: "rectangle",
  },
  DECISION: {
    canCreateOutput: true,
    canReceiveInput: true,
    defaultLabel: "If condition",
    isAction: false,
    isTerminal: false,
    label: "Decision",
    mermaidShape: "decision",
    nodeKind: "DECISION",
    shape: "diamond",
  },
  END: {
    canCreateOutput: false,
    canReceiveInput: true,
    defaultLabel: "End",
    isAction: false,
    isTerminal: true,
    label: "End",
    mermaidShape: "terminal",
    nodeKind: "START_END",
    shape: "pill",
  },
} as const satisfies Record<string, NodeTypeRegistryItem>;

export const NODE_KIND_REGISTRY = {
  START_END: {
    canvasSize: {
      height: 52,
      width: 176,
    },
    className: "border-teal-300 bg-teal-50 text-teal-900",
    defaultLabel: "Start / End",
    defaultType: "ACTION",
    description: "Pill / rounded rectangle",
    label: "Start / End",
    labelLayout: {
      charsPerLine: 22,
      maxRows: 3,
      rowHeight: 18,
    },
    miniMapColor: "#ccfbf1",
    shape: "pill",
    showWarningIcon: false,
  },
  ACTION: {
    canvasSize: {
      height: 74,
      width: 240,
    },
    className: "border-slate-300 bg-white text-slate-900",
    defaultLabel: "New Action",
    defaultType: "ACTION",
    description: "Rectangle",
    label: "Action",
    labelLayout: {
      charsPerLine: 28,
      maxRows: 5,
      rowHeight: 18,
    },
    miniMapColor: "#ffffff",
    shape: "rectangle",
    showWarningIcon: false,
  },
  DECISION: {
    canvasSize: {
      height: 180,
      width: 180,
    },
    className: "border-amber-300 bg-amber-50 text-amber-900",
    defaultLabel: "If condition",
    defaultType: "DECISION",
    description: "Diamond",
    label: "Decision",
    labelLayout: {
      charsPerLine: 18,
      maxRows: 5,
      rowHeight: 18,
    },
    miniMapColor: "#fef3c7",
    shape: "diamond",
    showWarningIcon: false,
  },
  INPUT_OUTPUT: {
    canvasSize: {
      height: 74,
      width: 260,
    },
    className: "border-sky-300 bg-sky-50 text-sky-900",
    defaultLabel: "Input Data",
    defaultType: "ACTION",
    description: "Parallelogram",
    label: "Input / Output",
    labelLayout: {
      charsPerLine: 30,
      maxRows: 5,
      rowHeight: 18,
    },
    miniMapColor: "#e0f2fe",
    shape: "parallelogram",
    showWarningIcon: false,
  },
  ERROR: {
    canvasSize: {
      height: 92,
      width: 240,
    },
    className: "border-red-300 bg-red-50 text-red-900",
    defaultLabel: "Show Error",
    defaultType: "ACTION",
    description: "Warning rectangle",
    label: "Error / Failure",
    labelLayout: {
      charsPerLine: 28,
      maxRows: 5,
      rowHeight: 18,
    },
    miniMapColor: "#ffe4e6",
    shape: "error",
    showWarningIcon: true,
  },
} as const satisfies Record<string, NodeKindRegistryItem>;

export const NODE_KIND_ORDER = [
  "START_END",
  "ACTION",
  "DECISION",
  "INPUT_OUTPUT",
  "ERROR",
] as const satisfies ReadonlyArray<keyof typeof NODE_KIND_REGISTRY>;

export const NODE_TYPE_ORDER = [
  "START",
  "ACTION",
  "DECISION",
  "END",
] as const satisfies ReadonlyArray<keyof typeof NODE_TYPE_REGISTRY>;

export const FLOW_LAYOUT_CONFIG = {
  branchRowSpacing: 240,
  columnSpacing: 500,
  minColumnGap: 240,
  minRowGap: 120,
  startX: 80,
  startY: 80,
} as const;

export const DEFAULT_NODE_TYPE = "ACTION" satisfies keyof typeof NODE_TYPE_REGISTRY;
export const DEFAULT_NODE_KIND = "ACTION" satisfies keyof typeof NODE_KIND_REGISTRY;
