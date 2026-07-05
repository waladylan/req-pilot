import type { FlowchartDTO } from "./flow.types";
import type {
  WfmTestCaseGenerationMetadataDTO,
  WfmTestCaseSetDTO,
} from "./test-case.types";

export type ProjectDTO = {
  id: string;
  name: string;
  description?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type RequirementStatus = "DRAFT" | "GENERATED" | "EDITED" | "ARCHIVED" | string;

export type RequirementResourceDTO = {
  id: string;
  projectId: string;
  title: string;
  requirementText: string;
  status: RequirementStatus;
  orderIndex: number;
  wfmVersion: string;
  wfm?: unknown;
  flowchart?: FlowchartDTO;
  metadata?: unknown;
  testCaseSet?: WfmTestCaseSetDTO | null;
  testCaseMetadata?: WfmTestCaseGenerationMetadataDTO | null;
  testCasesGeneratedAt?: string | null;
  testCasesUpdatedAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ProjectCreatePayload = {
  name: string;
  description?: string;
};

export type RequirementCreatePayload = {
  title: string;
  requirementText: string;
};

export type RequirementUpdatePayload = {
  title?: string;
  requirementText?: string;
  status?: RequirementStatus;
  orderIndex?: number;
};

export type SavedRequirementGenerationResponseDTO = {
  requirement: RequirementResourceDTO;
  wfm: unknown;
  flowchart: FlowchartDTO;
  metadata: unknown;
};

export type RequirementTestCasesResponseDTO = {
  requirement: RequirementResourceDTO;
  testCaseSet?: WfmTestCaseSetDTO | null;
  metadata?: WfmTestCaseGenerationMetadataDTO | null;
};
