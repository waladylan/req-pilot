import { describe, expect, it } from "vitest";

import type {
  RequirementResourceDTO,
  RequirementTestCasesResponseDTO,
} from "@/types/project.types";
import type {
  WfmTestCaseGenerationMetadataDTO,
  WfmTestCaseSetDTO,
} from "@/types/test-case.types";

import {
  applyRequirementTestCaseResult,
  selectRequirementTestCaseSet,
} from "./requirement-test-cases";

describe("requirement test case state helpers", () => {
  it("updates only the selected requirement with generated test cases", () => {
    const firstRequirement = requirement({ id: "requirement-1" });
    const siblingRequirement = requirement({
      id: "requirement-2",
      testCaseSet: testCaseSet("existing-sibling"),
    });

    const nextRequirements = applyRequirementTestCaseResult(
      [firstRequirement, siblingRequirement],
      testCaseResponse(firstRequirement, testCaseSet("generated")),
    );

    expect(nextRequirements[0]?.testCaseSet?.workflowId).toBe("generated");
    expect(nextRequirements[1]?.testCaseSet?.workflowId).toBe("existing-sibling");
  });

  it("preserves requirement text, WFM, and flowchart when applying test cases", () => {
    const originalRequirement = requirement({
      flowchart: { edges: [], mermaid: "flowchart LR\n", nodes: [] },
      requirementText: "Original requirement text",
      wfm: { wfmVersion: "2.0", workflowId: "original-wfm" },
    });
    const responseRequirement = {
      ...originalRequirement,
      flowchart: undefined,
      metadata: undefined,
      requirementText: undefined,
      wfm: undefined,
    } as unknown as RequirementResourceDTO;

    const nextRequirements = applyRequirementTestCaseResult(
      [originalRequirement],
      testCaseResponse(responseRequirement, testCaseSet("generated")),
    );

    expect(nextRequirements[0]?.requirementText).toBe("Original requirement text");
    expect(nextRequirements[0]?.wfm).toEqual({
      wfmVersion: "2.0",
      workflowId: "original-wfm",
    });
    expect(nextRequirements[0]?.flowchart).toEqual({
      edges: [],
      mermaid: "flowchart LR\n",
      nodes: [],
    });
  });

  it("selects test cases only for the active requirement", () => {
    const requirements = [
      requirement({ id: "requirement-1", testCaseSet: testCaseSet("first") }),
      requirement({ id: "requirement-2", testCaseSet: testCaseSet("second") }),
    ];

    expect(selectRequirementTestCaseSet(requirements, "requirement-2")?.workflowId).toBe(
      "second",
    );
    expect(selectRequirementTestCaseSet(requirements, null)).toBeNull();
    expect(selectRequirementTestCaseSet(requirements, "missing")).toBeNull();
  });
});

function testCaseResponse(
  responseRequirement: RequirementResourceDTO,
  generatedTestCaseSet: WfmTestCaseSetDTO,
): RequirementTestCasesResponseDTO {
  return {
    metadata: testCaseMetadata(),
    requirement: responseRequirement,
    testCaseSet: generatedTestCaseSet,
  };
}

function requirement(overrides: Partial<RequirementResourceDTO> = {}): RequirementResourceDTO {
  return {
    createdAt: "2026-07-05T00:00:00Z",
    flowchart: { edges: [], mermaid: "", nodes: [] },
    id: "requirement-1",
    metadata: { generationMode: "AI" },
    orderIndex: 0,
    projectId: "project-1",
    requirementText: "User can create a purchase request.",
    status: "GENERATED",
    testCaseMetadata: null,
    testCaseSet: null,
    testCasesGeneratedAt: null,
    testCasesUpdatedAt: null,
    title: "Purchase Request",
    updatedAt: "2026-07-05T00:00:00Z",
    wfm: { wfmVersion: "2.0" },
    wfmVersion: "2.0",
    ...overrides,
  };
}

function testCaseSet(workflowId: string): WfmTestCaseSetDTO {
  return {
    coverage: {
      branchCoverage: [],
      coveredNodeIds: [],
      coveredTransitionIds: [],
      nodeCount: 1,
      transitionCount: 1,
      uncoveredNodeIds: [],
      uncoveredTransitionIds: [],
      warnings: [],
    },
    metadata: {
      generationMode: "RULE_BASED",
      generator: "python-wfm-v2-test-case-generator",
      strategy: "PATH_COVERAGE",
      warnings: [],
    },
    sourceWfmVersion: "2.0",
    testCaseVersion: "1.0",
    testCases: [
      {
        expectedResult: "The workflow reaches an end state.",
        id: "TC-001",
        preconditions: ["Workflow data is available."],
        priority: "HIGH",
        sourcePath: {
          nodeIds: ["start", "end"],
          transitionIds: ["t1"],
        },
        steps: [],
        tags: ["success"],
        testData: {},
        title: "Generated path",
        type: "FUNCTIONAL",
      },
    ],
    workflowId,
    workflowName: "Purchase Request Approval",
  };
}

function testCaseMetadata(): WfmTestCaseGenerationMetadataDTO {
  return {
    generationStatus: "PASSED",
    generator: "python-wfm-v2-test-case-generator",
    sourceWfmVersion: "2.0",
    strategy: "PATH_COVERAGE",
    warnings: [],
  };
}
