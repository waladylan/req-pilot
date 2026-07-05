import type { FlowchartDTO } from "./flow.types";
import type { TestCaseDTO } from "./test-case.types";
import type { WfmDocument } from "./wfm.types";

export type GenerateFlowchartOptionsDTO = {
  includeDebug?: boolean;
  includeRequirementAnalysis?: boolean;
  includeWfm?: boolean;
};

export type GenerateFlowPayload = {
  requirement: string;
  options?: GenerateFlowchartOptionsDTO;
};

export type ReactFlowPositionDTO = {
  x: number;
  y: number;
};

export type ReactFlowNodeDTO = {
  data?: Record<string, unknown> & {
    actor?: string | null;
    description?: string | null;
    kind?: string | null;
    label?: string | null;
    sourceWfmNodeId?: string | null;
  };
  id: string;
  position?: ReactFlowPositionDTO;
  type?: string;
};

export type ReactFlowEdgeDTO = {
  data?: Record<string, unknown> & {
    condition?: string | null;
    label?: string | null;
    sourceWfmEdgeId?: string | null;
  };
  id: string;
  label?: string | null;
  source: string;
  target: string;
  type?: string;
};

export type ReactFlowDefinitionDTO = {
  direction?: string;
  edges: ReactFlowEdgeDTO[];
  format: "REACT_FLOW" | string;
  nodes: ReactFlowNodeDTO[];
  version?: string;
  warnings?: string[];
  workflowName?: string;
};

export type GenerateFlowResponseDTO = {
  debug?: {
    requirementAnalysis?: unknown;
    wfm?: unknown;
  };
  flowchart: ReactFlowDefinitionDTO;
  format: "REACT_FLOW" | string;
  warnings?: string[];
  workflowName: string;
};

export type GenerateTestCasesPayload = {
  requirement?: string;
  flowchart?: FlowchartDTO;
  wfm?: WfmDocument;
};

export type GenerateTestCasesMetadataDTO = {
  source: "FLOWCHART_FALLBACK" | "WFM" | string;
  workflowId?: string;
  pathCount: number;
  warnings: string[];
};

export type GenerateTestCasesResponseDTO = {
  testCases: TestCaseDTO[];
  metadata?: GenerateTestCasesMetadataDTO;
};

export type ApiErrorDTO = {
  message: string;
  details?: string[];
};
