import type {
  WFM_ACTOR_TYPE_REGISTRY,
  WFM_ANNOTATION_SEVERITY_REGISTRY,
  WFM_ANNOTATION_TARGET_REGISTRY,
  WFM_NODE_ROLE_REGISTRY,
  WFM_TRANSITION_SEMANTIC_REGISTRY,
  WFM_VARIABLE_TYPE_REGISTRY,
} from "@/constants";

export type WfmNodeRole = keyof typeof WFM_NODE_ROLE_REGISTRY;
export type WfmTransitionSemantic = keyof typeof WFM_TRANSITION_SEMANTIC_REGISTRY;
export type WfmActorType = keyof typeof WFM_ACTOR_TYPE_REGISTRY;
export type WfmVariableType = keyof typeof WFM_VARIABLE_TYPE_REGISTRY;
export type WfmAnnotationTarget = keyof typeof WFM_ANNOTATION_TARGET_REGISTRY;
export type WfmAnnotationSeverity = keyof typeof WFM_ANNOTATION_SEVERITY_REGISTRY;

export type WfmWorkflow = {
  id: string;
  title: string;
  description?: string;
  language?: "vi" | "en" | "unknown" | string;
  domain?: string;
  sourceRequirement?: string;
};

export type WfmActor = {
  id: string;
  name: string;
  type: WfmActorType;
};

export type WfmVariable = {
  id: string;
  name: string;
  type: WfmVariableType;
  description?: string;
  required?: boolean;
  defaultValue?: unknown;
};

export type WfmNode = {
  id: string;
  role: WfmNodeRole;
  kind: string;
  title: string;
  description?: string;
  actorId?: string;
  tags?: string[];
  data?: Record<string, unknown>;
};

export type WfmTransition = {
  id: string;
  from: string;
  to: string;
  semantic: WfmTransitionSemantic;
  kind?: string;
  condition?: string;
  description?: string;
  data?: Record<string, unknown>;
};

export type WfmAnnotation = {
  id: string;
  targetType: WfmAnnotationTarget;
  targetId?: string;
  text: string;
  severity?: WfmAnnotationSeverity;
};

export type WfmNodeKindDefinition = {
  kind: string;
  extendsRole: WfmNodeRole;
  label: string;
  description?: string;
  namespace?: string;
};

export type WfmTransitionKindDefinition = {
  kind: string;
  extendsSemantic: WfmTransitionSemantic;
  label: string;
  description?: string;
  namespace?: string;
};

export type WfmExtensions = {
  nodeKinds: WfmNodeKindDefinition[];
  transitionKinds: WfmTransitionKindDefinition[];
};

export type WfmAst = {
  actors: WfmActor[];
  variables: WfmVariable[];
  nodes: WfmNode[];
  transitions: WfmTransition[];
  annotations: WfmAnnotation[];
};

export type WfmDocument = {
  schemaVersion: "1.0";
  modelType: "WORKFLOW_AST";
  workflow: WfmWorkflow;
  extensions?: WfmExtensions;
  ast: WfmAst;
};

export type WorkflowCanvasViewState = {
  workflowId: string;
  nodePositions: Record<string, { x: number; y: number }>;
  viewport?: {
    x: number;
    y: number;
    zoom: number;
  };
};
