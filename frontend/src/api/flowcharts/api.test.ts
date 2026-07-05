import { beforeEach, describe, expect, it, vi, type Mock } from "vitest";

import { axiosClient } from "@/lib/axios";
import type { GenerateFlowResponseDTO } from "@/types/requirement";

import {
  assertReactFlowResponse,
  generateFlowchartFromRequirement,
  reactFlowDefinitionToFlowchartDTO,
} from "./api";

vi.mock("@/lib/axios", () => ({
  axiosClient: {
    post: vi.fn(),
  },
}));

const reactFlowResponse = {
  flowchart: {
    direction: "LR",
    edges: [
      {
        data: {
          condition: "Manager approves",
          sourceWfmEdgeId: "edge_approval_approved",
        },
        id: "edge_approval_approved",
        label: "Manager approves",
        source: "manager_approval",
        target: "approved",
        type: "smoothstep",
      },
    ],
    format: "REACT_FLOW",
    nodes: [
      {
        data: {
          kind: "start",
          label: "Start",
          sourceWfmNodeId: "start",
        },
        id: "start",
        position: { x: 0, y: 0 },
        type: "start",
      },
      {
        data: {
          actor: "Manager",
          kind: "approval",
          label: "Manager approval",
          sourceWfmNodeId: "manager_approval",
        },
        id: "manager_approval",
        position: { x: 0, y: 140 },
        type: "approval",
      },
      {
        data: {
          kind: "end",
          label: "Request approved",
          sourceWfmNodeId: "approved",
        },
        id: "approved",
        position: { x: 0, y: 280 },
        type: "end",
      },
    ],
    version: "1.0",
    warnings: [],
    workflowName: "Purchase Request Approval",
  },
  format: "REACT_FLOW",
  warnings: [],
  workflowName: "Purchase Request Approval",
} satisfies GenerateFlowResponseDTO;

describe("flowchart public API client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls only the public flowchart generation endpoint", async () => {
    postMock().mockResolvedValueOnce({ data: reactFlowResponse });

    await generateFlowchartFromRequirement("User can create a purchase request.");

    expect(postMock()).toHaveBeenCalledTimes(1);
    expect(postMock()).toHaveBeenCalledWith(
      "/api/flowcharts/generate",
      {
        requirement: "User can create a purchase request.",
      },
      {
        timeout: 180000,
      },
    );
    expect(postMock().mock.calls[0]?.[0]).not.toContain("/api/ai/");
    expect(postMock().mock.calls[0]?.[0]).not.toContain("generate-test");
  });

  it("does not call the API for blank requirements", async () => {
    await expect(generateFlowchartFromRequirement("   ")).rejects.toThrow(
      "Please enter a requirement.",
    );

    expect(postMock()).not.toHaveBeenCalled();
  });

  it("rejects unsupported response formats", () => {
    expect(() =>
      assertReactFlowResponse({
        ...reactFlowResponse,
        format: "MERMAID",
      }),
    ).toThrow("Unsupported response format.");

    expect(() =>
      assertReactFlowResponse({
        ...reactFlowResponse,
        flowchart: {
          ...reactFlowResponse.flowchart,
          format: "MERMAID",
        },
      }),
    ).toThrow("Backend returned invalid flowchart response.");
  });

  it("adapts backend React Flow JSON into the existing canvas DTO", () => {
    const flowchart = reactFlowDefinitionToFlowchartDTO(reactFlowResponse.flowchart);

    expect(flowchart.nodes).toHaveLength(3);
    expect(flowchart.nodes[0]).toMatchObject({
      id: "start",
      label: "Start",
      nodeKind: "START_END",
      title: "Start",
      type: "START",
    });
    expect(flowchart.nodes[1]).toMatchObject({
      id: "manager_approval",
      label: "Manager approval",
      nodeKind: "ACTION",
      type: "ACTION",
    });
    expect(flowchart.edges[0]).toMatchObject({
      id: "edge_approval_approved",
      label: "Manager approves",
      source: "manager_approval",
      target: "approved",
      type: "YES",
    });
  });

  it("preserves API edge labels from data.label when edge.label is absent", () => {
    const flowchart = reactFlowDefinitionToFlowchartDTO({
      ...reactFlowResponse.flowchart,
      edges: [
        {
          ...reactFlowResponse.flowchart.edges[0],
          data: {
            label: "Amount <= 1000",
            sourceWfmEdgeId: "edge_direct_approval",
          },
          label: null,
        },
      ],
    });

    expect(flowchart.edges[0]).toMatchObject({
      label: "Amount <= 1000",
      type: undefined,
    });
  });
});

function postMock() {
  return axiosClient.post as Mock;
}
