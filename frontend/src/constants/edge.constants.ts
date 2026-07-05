type EdgeRegistryItem = {
  color: string;
  description: string;
  label: string;
};

export const EDGE_REGISTRY = {
  DEFAULT: {
    color: "#6B7280",
    description: "Default path",
    label: "Default",
  },
  YES: {
    color: "#22C55E",
    description: "Positive decision branch",
    label: "Yes",
  },
  NO: {
    color: "#EF4444",
    description: "Negative decision branch",
    label: "No",
  },
  SUCCESS: {
    color: "#3B82F6",
    description: "Successful outcome",
    label: "Success",
  },
  FAILURE: {
    color: "#F97316",
    description: "Failure outcome",
    label: "Failure",
  },
  CANCEL: {
    color: "#EF4444",
    description: "User cancellation path",
    label: "Cancel",
  },
  RETRY: {
    color: "#A855F7",
    description: "Retry path",
    label: "Retry",
  },
  TIMEOUT: {
    color: "#F59E0B",
    description: "Timeout path",
    label: "Timeout",
  },
} as const satisfies Record<string, EdgeRegistryItem>;

type FlowHandleRegistryItem = {
  description: string;
  direction: "input" | "output";
  label: string;
  semantic?: keyof typeof EDGE_REGISTRY;
};

export const EDGE_LEGEND_ORDER = [
  "YES",
  "NO",
  "SUCCESS",
  "FAILURE",
  "CANCEL",
  "RETRY",
  "TIMEOUT",
  "DEFAULT",
] as const satisfies ReadonlyArray<keyof typeof EDGE_REGISTRY>;

export const EDGE_INSPECTOR_ORDER = [
  "DEFAULT",
  "YES",
  "NO",
  "SUCCESS",
  "FAILURE",
  "CANCEL",
  "RETRY",
  "TIMEOUT",
] as const satisfies ReadonlyArray<keyof typeof EDGE_REGISTRY>;

export const DECISION_BRANCH_EDGE_OPTIONS = ["YES", "NO"] as const satisfies ReadonlyArray<
  keyof typeof EDGE_REGISTRY
>;

export const EDGE_SEMANTIC_DETECTION_ORDER = [
  "RETRY",
  "TIMEOUT",
  "FAILURE",
  "SUCCESS",
  "CANCEL",
  "NO",
  "YES",
] as const satisfies ReadonlyArray<keyof typeof EDGE_REGISTRY>;

export const EDGE_BRANCH_LAYOUT_ORDER = [
  "YES",
  "SUCCESS",
  "DEFAULT",
  "RETRY",
  "TIMEOUT",
  "NO",
  "CANCEL",
  "FAILURE",
] as const satisfies ReadonlyArray<keyof typeof EDGE_REGISTRY>;

export const EDGE_KEYWORD_REGISTRY = {
  RETRY: ["retry", "try again", "return to", "thu lai"],
  TIMEOUT: ["timeout", "timed out", "qua han", "het thoi gian"],
  FAILURE: [
    "fail",
    "failure",
    "failed",
    "error",
    "cannot",
    "unable",
    "unavailable",
    "exception",
    "system error",
    "loi",
    "that bai",
  ],
  SUCCESS: ["success", "succeed", "succeeds", "succeeded", "successfully", "thanh cong"],
  CANCEL: ["cancel", "cancels", "cancelled", "canceled", "huy"],
  NO: [
    "no",
    "reject",
    "rejects",
    "rejected",
    "if not",
    "not found",
    "missing",
    "invalid",
    "khong hop le",
    "thieu",
    "khong",
  ],
  YES: [
    "yes",
    "true",
    "confirm",
    "valid",
    "approved",
    "approve",
    "if pass",
    "pass",
    "co",
    "dung",
    "xac nhan",
  ],
} as const satisfies Partial<Record<keyof typeof EDGE_REGISTRY, readonly string[]>>;

export const DEFAULT_EDGE_SEMANTIC = "DEFAULT" satisfies keyof typeof EDGE_REGISTRY;

export const FLOW_HANDLE_REGISTRY = {
  INPUT: {
    description: "Incoming connection handle",
    direction: "input",
    label: "Input",
  },
  OUTPUT: {
    description: "Default outgoing connection handle",
    direction: "output",
    label: "Output",
    semantic: "DEFAULT",
  },
  YES: {
    description: "Positive decision branch handle",
    direction: "output",
    label: EDGE_REGISTRY.YES.label,
    semantic: "YES",
  },
  NO: {
    description: "Negative decision branch handle",
    direction: "output",
    label: EDGE_REGISTRY.NO.label,
    semantic: "NO",
  },
  SUCCESS: {
    description: "Successful outcome handle",
    direction: "output",
    label: EDGE_REGISTRY.SUCCESS.label,
    semantic: "SUCCESS",
  },
  FAILURE: {
    description: "Failure outcome handle",
    direction: "output",
    label: EDGE_REGISTRY.FAILURE.label,
    semantic: "FAILURE",
  },
  CANCEL: {
    description: "Cancellation outcome handle",
    direction: "output",
    label: EDGE_REGISTRY.CANCEL.label,
    semantic: "CANCEL",
  },
  RETRY: {
    description: "Retry outcome handle",
    direction: "output",
    label: EDGE_REGISTRY.RETRY.label,
    semantic: "RETRY",
  },
  TIMEOUT: {
    description: "Timeout outcome handle",
    direction: "output",
    label: EDGE_REGISTRY.TIMEOUT.label,
    semantic: "TIMEOUT",
  },
} as const satisfies Record<string, FlowHandleRegistryItem>;

export const FLOW_HANDLES = {
  INPUT: "INPUT",
  OUTPUT: "OUTPUT",
  YES: "YES",
  NO: "NO",
  SUCCESS: "SUCCESS",
  FAILURE: "FAILURE",
  CANCEL: "CANCEL",
  RETRY: "RETRY",
  TIMEOUT: "TIMEOUT",
} as const satisfies Record<keyof typeof FLOW_HANDLE_REGISTRY, keyof typeof FLOW_HANDLE_REGISTRY>;
