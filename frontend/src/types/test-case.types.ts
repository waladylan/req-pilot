import type { TEST_CASE_PRIORITY_REGISTRY } from "@/constants";

export type TestCasePriority = keyof typeof TEST_CASE_PRIORITY_REGISTRY;

export type TestCaseDTO = {
  id: string;
  title: string;
  preconditions: string;
  steps: string[];
  expectedResult: string;
  priority: TestCasePriority;
};

export type WfmTestCaseStepDTO = {
  stepNo: number;
  action: string;
  expectedResult: string;
};

export type WfmTestCaseSourcePathDTO = {
  nodeIds: string[];
  transitionIds: string[];
};

export type WfmGeneratedTestCaseDTO = {
  id: string;
  title: string;
  type: string;
  priority: TestCasePriority | string;
  sourcePath: WfmTestCaseSourcePathDTO;
  preconditions: string[];
  steps: WfmTestCaseStepDTO[];
  expectedResult: string;
  testData: Record<string, unknown>;
  tags: string[];
};

export type WfmTestCaseCoverageDTO = {
  nodeCount: number;
  transitionCount: number;
  coveredNodeIds: string[];
  coveredTransitionIds: string[];
  uncoveredNodeIds: string[];
  uncoveredTransitionIds: string[];
  branchCoverage: Array<Record<string, unknown>>;
  warnings: string[];
};

export type WfmTestCaseSetMetadataDTO = {
  generator: string;
  strategy: string;
  generationMode: string;
  warnings: string[];
};

export type WfmTestCaseSetDTO = {
  testCaseVersion: string;
  sourceWfmVersion: string;
  workflowId: string;
  workflowName: string;
  testCases: WfmGeneratedTestCaseDTO[];
  coverage: WfmTestCaseCoverageDTO;
  metadata: WfmTestCaseSetMetadataDTO;
};

export type WfmTestCaseGenerationMetadataDTO = {
  sourceWfmVersion: string;
  generator: string;
  strategy: string;
  generationStatus: string;
  warnings: string[];
};
