type WfmRegistryItem = {
  description: string;
  label: string;
};

export const WFM_NODE_ROLE_REGISTRY = {
  START: {
    description: "Entry point of the workflow",
    label: "Start",
  },
  END: {
    description: "Terminal point of the workflow",
    label: "End",
  },
  ACTION: {
    description: "Business action or processing step",
    label: "Action",
  },
  DECISION: {
    description: "Conditional branching point",
    label: "Decision",
  },
  INPUT: {
    description: "User or system input",
    label: "Input",
  },
  OUTPUT: {
    description: "Visible output, message, result, or response",
    label: "Output",
  },
  ERROR: {
    description: "Error, failure, or exceptional state",
    label: "Error",
  },
  SUBPROCESS: {
    description: "Nested or reusable workflow",
    label: "Subprocess",
  },
} as const satisfies Record<string, WfmRegistryItem>;

export const WFM_TRANSITION_SEMANTIC_REGISTRY = {
  DEFAULT: {
    description: "Normal sequence flow",
    label: "Default",
  },
  YES: {
    description: "Positive branch",
    label: "Yes",
  },
  NO: {
    description: "Negative branch",
    label: "No",
  },
  SUCCESS: {
    description: "Successful result",
    label: "Success",
  },
  FAILURE: {
    description: "Failed result",
    label: "Failure",
  },
  CANCEL: {
    description: "User cancels the flow",
    label: "Cancel",
  },
  RETRY: {
    description: "Retry branch",
    label: "Retry",
  },
  TIMEOUT: {
    description: "Timeout branch",
    label: "Timeout",
  },
} as const satisfies Record<string, WfmRegistryItem>;

export const WFM_ACTOR_TYPE_REGISTRY = {
  USER: { description: "End user participant", label: "User" },
  SYSTEM: { description: "Application or backend system", label: "System" },
  EXTERNAL_SYSTEM: { description: "External integration or service", label: "External System" },
  ADMIN: { description: "Administrator participant", label: "Admin" },
  GUEST: { description: "Unauthenticated participant", label: "Guest" },
} as const satisfies Record<string, WfmRegistryItem>;

export const WFM_VARIABLE_TYPE_REGISTRY = {
  STRING: { description: "String value", label: "String" },
  NUMBER: { description: "Numeric value", label: "Number" },
  BOOLEAN: { description: "True or false value", label: "Boolean" },
  DATE: { description: "Date or date-time value", label: "Date" },
  OBJECT: { description: "Object value", label: "Object" },
  ARRAY: { description: "Array value", label: "Array" },
  UNKNOWN: { description: "Unknown value type", label: "Unknown" },
} as const satisfies Record<string, WfmRegistryItem>;

export const WFM_ANNOTATION_TARGET_REGISTRY = {
  WORKFLOW: { description: "Workflow-level annotation", label: "Workflow" },
  NODE: { description: "Node-level annotation", label: "Node" },
  TRANSITION: { description: "Transition-level annotation", label: "Transition" },
} as const satisfies Record<string, WfmRegistryItem>;

export const WFM_ANNOTATION_SEVERITY_REGISTRY = {
  INFO: { description: "Informational annotation", label: "Info" },
  WARNING: { description: "Warning annotation", label: "Warning" },
  ERROR: { description: "Error annotation", label: "Error" },
} as const satisfies Record<string, WfmRegistryItem>;

export const WFM_SCHEMA_VERSION = "1.0";
export const WFM_MODEL_TYPE = "WORKFLOW_AST";
