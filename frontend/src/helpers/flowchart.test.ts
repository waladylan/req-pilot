import { describe, expect, expectTypeOf, it } from "vitest";

import {
  DEFAULT_EDGE_SEMANTIC,
  DEFAULT_NODE_TYPE,
  EDGE_REGISTRY,
  FLOW_LAYOUT_CONFIG,
  FLOW_HANDLES,
  NODE_KIND_REGISTRY,
  NODE_TYPE_REGISTRY,
  SAMPLE_REQUIREMENTS,
} from "@/constants";
import {
  addFlowNode,
  applyFlowNodeChanges,
  buildRequirementTextFromFlowchart,
  connectFlowNodes,
  createFlowchartFromReactFlow,
  createReactFlowEdges,
  createReactFlowNodes,
  deleteFlowEdge,
  deleteFlowNode,
  detectEdgeSemantic,
  detectNodeKind,
  ensureFlowchartNodePositions,
  getEdgeLabel,
  getEdgeSemanticFromSourceHandle,
  getNodeTitle,
  updateFlowEdgeLabel,
  updateFlowNodeLabel,
  updateFlowNodeMetadata,
} from "@/helpers/flowchart";
import { applyAutoLayout } from "@/utils/flowLayout";
import type {
  FlowEdgeSemantic,
  FlowNodeKind,
  FlowNodeType,
  FlowchartDTO,
} from "@/types/requirement";

const sampleFlowchart: FlowchartDTO = {
  nodes: [
    { id: "start_node", label: "Start", type: "START" },
    { id: "entry_action", label: "Click Delete", type: "ACTION" },
    { id: "decision_1", label: "Confirm Dialog", type: "DECISION" },
    { id: "branch_action_1", label: "Delete Product", type: "ACTION" },
    { id: "branch_success_1", label: "Show Success", type: "ACTION" },
    { id: "branch_action_2", label: "Do Nothing", type: "ACTION" },
    { id: "end_node", label: "End", type: "END" },
  ],
  edges: [
    { id: "edge_1", source: "start_node", target: "entry_action" },
    { id: "edge_2", source: "entry_action", target: "decision_1" },
    { id: "edge_3", source: "decision_1", target: "branch_action_1", label: "Confirm" },
    { id: "edge_4", source: "branch_action_1", target: "branch_success_1" },
    { id: "edge_5", source: "branch_success_1", target: "end_node" },
    { id: "edge_6", source: "decision_1", target: "branch_action_2", label: "Cancel" },
    { id: "edge_7", source: "branch_action_2", target: "end_node" },
  ],
};

const cyclicRetryFlowchart: FlowchartDTO = {
  nodes: [
    { id: "start", label: "Start Login", type: "START" },
    { id: "login_screen", label: "Show Login Screen", type: "ACTION" },
    { id: "validate", label: "Validate Credentials", type: "DECISION" },
    { id: "show_error", label: "Show Invalid Credentials", type: "ACTION" },
    { id: "prompt_retry", label: "Prompt for Retry", type: "DECISION" },
    { id: "dashboard", label: "Redirect to Dashboard", type: "ACTION" },
    { id: "end", label: "End", type: "END" },
  ],
  edges: [
    { id: "retry_edge_1", source: "start", target: "login_screen" },
    { id: "retry_edge_2", source: "login_screen", target: "validate" },
    { id: "retry_edge_3", source: "validate", target: "show_error", type: "NO" },
    { id: "retry_edge_4", source: "show_error", target: "prompt_retry" },
    { id: "retry_edge_5", source: "prompt_retry", target: "login_screen", type: "RETRY" },
    { id: "retry_edge_6", source: "validate", target: "dashboard", type: "YES" },
    { id: "retry_edge_7", source: "dashboard", target: "end" },
  ],
};

const purchaseRequestFlowchart: FlowchartDTO = {
  nodes: [
    { id: "start", label: "Start", type: "START", position: { x: 0, y: 0 } },
    {
      id: "create_request",
      label: "Create Purchase Request",
      type: "ACTION",
      position: { x: 300, y: 0 },
    },
    {
      id: "manager_approval",
      label: "Manager Approval",
      type: "ACTION",
      position: { x: 600, y: 0 },
    },
    {
      id: "check_amount",
      label: "Check Amount",
      type: "DECISION",
      position: { x: 900, y: 0 },
    },
    {
      id: "finance_approval",
      label: "Finance Approval",
      type: "ACTION",
      position: { x: 1200, y: 0 },
    },
    {
      id: "request_rejected",
      label: "Request Rejected",
      type: "ACTION",
      position: { x: 1200, y: 0 },
    },
    {
      id: "request_approved",
      label: "Request Approved",
      type: "ACTION",
      position: { x: 1200, y: 0 },
    },
    { id: "end", label: "End", type: "END", position: { x: 1500, y: 0 } },
  ],
  edges: [
    { id: "e1", source: "start", target: "create_request" },
    { id: "e2", source: "create_request", target: "manager_approval" },
    { id: "e3", source: "manager_approval", target: "check_amount" },
    {
      id: "e4",
      source: "check_amount",
      target: "finance_approval",
      label: "Approved and amount > 1000",
      type: "YES",
    },
    {
      id: "e5",
      source: "check_amount",
      target: "request_rejected",
      label: "Rejected",
      type: "NO",
    },
    {
      id: "e6",
      source: "check_amount",
      target: "request_approved",
      label: "Amount <= 1000",
      type: "DEFAULT",
    },
    {
      id: "e7",
      source: "finance_approval",
      target: "request_approved",
      label: "Finance approves",
      type: "SUCCESS",
    },
    { id: "e8", source: "request_rejected", target: "end", type: "FAILURE" },
    { id: "e9", source: "request_approved", target: "end", type: "SUCCESS" },
  ],
};

describe("flowchart helpers", () => {
  it("derives node and edge types from registries", () => {
    expectTypeOf<FlowNodeType>().toEqualTypeOf<keyof typeof NODE_TYPE_REGISTRY>();
    expectTypeOf<FlowNodeKind>().toEqualTypeOf<keyof typeof NODE_KIND_REGISTRY>();
    expectTypeOf<FlowEdgeSemantic>().toEqualTypeOf<keyof typeof EDGE_REGISTRY>();

    expect(NODE_KIND_REGISTRY.DECISION.shape).toBe("diamond");
    expect(NODE_TYPE_REGISTRY.START.nodeKind).toBe("START_END");
    expect(EDGE_REGISTRY.YES.color).toBe("#22C55E");
  });

  it("creates React Flow nodes with deterministic positions", () => {
    const positionedFlowchart = ensureFlowchartNodePositions(sampleFlowchart);

    expect(positionedFlowchart.nodes.every((node) => node.position)).toBe(true);
    expect(positionedFlowchart.nodes.find((node) => node.id === "decision_1")?.nodeKind).toBe(
      "DECISION",
    );
    const startPosition = positionedFlowchart.nodes.find(
      (node) => node.id === "start_node",
    )?.position;
    const actionPosition = positionedFlowchart.nodes.find(
      (node) => node.id === "entry_action",
    )?.position;

    expect(startPosition?.x).toBe(FLOW_LAYOUT_CONFIG.startX);
    expect(actionPosition?.x).toBeGreaterThan(startPosition?.x ?? 0);
  });

  it("recalculates cramped generated positions into readable purchase request branches", () => {
    const positionedFlowchart = ensureFlowchartNodePositions(purchaseRequestFlowchart);
    const checkAmount = getRequiredNode(positionedFlowchart, "check_amount");
    const financeApproval = getRequiredNode(positionedFlowchart, "finance_approval");
    const rejected = getRequiredNode(positionedFlowchart, "request_rejected");
    const approved = getRequiredNode(positionedFlowchart, "request_approved");
    const reactEdges = createReactFlowEdges(positionedFlowchart);

    expectAllNodesToHaveFinitePositions(positionedFlowchart);
    expect(financeApproval.position?.x).toBeGreaterThan(checkAmount.position?.x ?? 0);
    expect(approved.position?.x).toBeGreaterThan(checkAmount.position?.x ?? 0);
    expect(rejected.position?.x).toBeGreaterThan(checkAmount.position?.x ?? 0);
    expect(
      new Set([financeApproval.position?.y, rejected.position?.y, approved.position?.y]).size,
    ).toBe(3);
    expect(
      Math.abs((financeApproval.position?.y ?? 0) - (approved.position?.y ?? 0)),
    ).toBeGreaterThan(100);
    expect(reactEdges.find((edge) => edge.id === "e4")?.label).toBe("Approved and amount > 1000");
    expect(reactEdges.find((edge) => edge.id === "e5")?.label).toBe("Rejected");
    expect(reactEdges.find((edge) => edge.id === "e6")?.label).toBe("Amount <= 1000");
  });

  it("lays out retry cycles without letting back edges move base nodes", () => {
    const positionedFlowchart = ensureFlowchartNodePositions(cyclicRetryFlowchart);
    const loginNode = positionedFlowchart.nodes.find((node) => node.id === "login_screen");
    const retryNode = positionedFlowchart.nodes.find((node) => node.id === "prompt_retry");
    const reactNodes = createReactFlowNodes(positionedFlowchart);
    const reactEdges = createReactFlowEdges(positionedFlowchart);
    const retryEdge = reactEdges.find((edge) => edge.id === "retry_edge_5");
    const layoutedFlowchart = applyAutoLayout(cyclicRetryFlowchart);
    const layoutedLoginNode = layoutedFlowchart.nodes.find((node) => node.id === "login_screen");
    const layoutedRetryNode = layoutedFlowchart.nodes.find((node) => node.id === "prompt_retry");

    expectAllNodesToHaveFinitePositions(positionedFlowchart);
    expectAllNodesToHaveFinitePositions(layoutedFlowchart);
    expect(loginNode?.position?.x).toBeLessThan(retryNode?.position?.x ?? 0);
    expect(layoutedLoginNode?.position?.x).toBeLessThan(layoutedRetryNode?.position?.x ?? 0);
    expect(reactNodes).toHaveLength(cyclicRetryFlowchart.nodes.length);
    expect(reactEdges).toHaveLength(cyclicRetryFlowchart.edges.length);
    expect(retryEdge?.target).toBe("login_screen");
    expect(retryEdge?.data?.semantic).toBe("RETRY");
  });

  it("sizes React Flow nodes so longer labels wrap visibly on the canvas", () => {
    const [reactNode] = createReactFlowNodes({
      nodes: [
        {
          id: "long_action",
          label: "User opens the login screen and enters username and password",
          type: "ACTION",
        },
      ],
      edges: [],
    });

    expect(reactNode.data.labelRows).toBeGreaterThan(1);
    expect(reactNode.initialWidth).toBe(
      NODE_KIND_REGISTRY[reactNode.data.nodeKind].canvasSize.width,
    );
    expect(reactNode.initialHeight).toBeGreaterThan(
      NODE_KIND_REGISTRY[reactNode.data.nodeKind].canvasSize.height,
    );
    expect(reactNode.measured?.width).toBe(reactNode.initialWidth);
    expect(reactNode.measured?.height).toBe(reactNode.initialHeight);
    expect(reactNode.width).toBe(reactNode.initialWidth);
    expect(reactNode.height).toBe(reactNode.initialHeight);
    expect(reactNode.handles?.find((handle) => handle.id === FLOW_HANDLES.INPUT)?.position).toBe(
      "left",
    );
    expect(reactNode.handles?.find((handle) => handle.id === FLOW_HANDLES.OUTPUT)?.position).toBe(
      "right",
    );
  });

  it("persists dragged node positions back into the flowchart DTO", () => {
    const positionedFlowchart = ensureFlowchartNodePositions(sampleFlowchart);
    const movedFlowchart = applyFlowNodeChanges(positionedFlowchart, [
      {
        id: "entry_action",
        type: "position",
        position: { x: 321.4, y: 88.6 },
        dragging: false,
      },
    ]);

    expect(movedFlowchart.nodes.find((node) => node.id === "entry_action")?.position).toEqual({
      x: 321,
      y: 89,
    });
  });

  it("builds readable requirement text only when explicitly requested", () => {
    const editedFlowchart = updateFlowNodeLabel(
      sampleFlowchart,
      "branch_action_1",
      "Archive Product",
    );
    const requirement = buildRequirementTextFromFlowchart(editedFlowchart);

    expect(requirement).toContain("Feature edited flow:");
    expect(requirement).toContain("* Click Delete");
    expect(requirement).toContain("* If Confirm, Archive Product, then Show Success");
    expect(requirement).toContain("* If Cancel, Do Nothing");
  });

  it("updates node title fields used by the canvas without forcing an Untitled fallback", () => {
    const renamedFlowchart = updateFlowNodeLabel(sampleFlowchart, "entry_action", "Tap Delete");
    const renamedNode = renamedFlowchart.nodes.find((node) => node.id === "entry_action");
    const [reactNode] = createReactFlowNodes({
      nodes: [renamedNode ?? sampleFlowchart.nodes[1]],
      edges: [],
    });

    expect(renamedNode).toMatchObject({
      label: "Tap Delete",
      title: "Tap Delete",
    });
    expect(getNodeTitle(renamedNode ?? sampleFlowchart.nodes[1])).toBe("Tap Delete");
    expect(reactNode.data.label).toBe("Tap Delete");
    expect(reactNode.data.title).toBe("Tap Delete");

    const clearedFlowchart = updateFlowNodeLabel(renamedFlowchart, "entry_action", "");
    const clearedNode = clearedFlowchart.nodes.find((node) => node.id === "entry_action");

    expect(clearedNode).toMatchObject({
      label: "",
      title: "",
    });
    expect(getNodeTitle(clearedNode ?? sampleFlowchart.nodes[1])).toBe("");
  });

  it("updates node content metadata without mutating the requirement text", () => {
    const requirementText = SAMPLE_REQUIREMENTS[1].value;
    const updatedFlowchart = updateFlowNodeMetadata(sampleFlowchart, "entry_action", {
      description: "Open the product actions menu",
      expectedResult: "Delete confirmation is visible",
      precondition: "Product exists",
    });
    const node = updatedFlowchart.nodes.find((item) => item.id === "entry_action");

    expect(node).toMatchObject({
      description: "Open the product actions menu",
      expectedResult: "Delete confirmation is visible",
      precondition: "Product exists",
    });
    expect(requirementText).toBe(SAMPLE_REQUIREMENTS[1].value);
  });

  it("updates edge labels in both compatibility fields and keeps semantic separate", () => {
    const requirementText = SAMPLE_REQUIREMENTS[1].value;
    const updatedFlowchart = updateFlowEdgeLabel(
      purchaseRequestFlowchart,
      "e7",
      "Approved by finance",
    );
    const updatedEdge = updatedFlowchart.edges.find((edge) => edge.id === "e7");
    const reactEdge = createReactFlowEdges(updatedFlowchart).find((edge) => edge.id === "e7");

    expect(updatedEdge).toMatchObject({
      data: {
        label: "Approved by finance",
      },
      label: "Approved by finance",
      type: "SUCCESS",
    });
    expect(getEdgeLabel(updatedEdge ?? purchaseRequestFlowchart.edges[6])).toBe(
      "Approved by finance",
    );
    expect(reactEdge?.label).toBe("Approved by finance");
    expect(reactEdge?.data?.label).toBe("Approved by finance");
    expect(reactEdge?.data?.semantic).toBe("SUCCESS");
    expect(requirementText).toBe(SAMPLE_REQUIREMENTS[1].value);
  });

  it("renders labels from edge data when the top-level edge label is absent", () => {
    const reactEdges = createReactFlowEdges({
      nodes: [
        { id: "source", label: "Source", type: "ACTION" },
        { id: "target", label: "Target", type: "ACTION" },
      ],
      edges: [
        {
          data: {
            label: "Stored in data",
          },
          id: "edge_data_label",
          source: "source",
          target: "target",
          type: "DEFAULT",
        },
      ],
    });

    expect(reactEdges[0].label).toBe("Stored in data");
    expect(reactEdges[0].data?.label).toBe("Stored in data");
  });

  it("renders edge semantics as colored lines with readable labels", () => {
    const reactEdges = createReactFlowEdges(sampleFlowchart, "edge_3");
    const yesEdge = reactEdges.find((edge) => edge.id === "edge_3");
    const cancelEdge = reactEdges.find((edge) => edge.id === "edge_6");

    expect(yesEdge?.label).toBe("Confirm");
    expect(yesEdge?.data?.label).toBe("Confirm");
    expect(yesEdge?.data?.semantic).toBe("YES");
    expect(yesEdge?.labelShowBg).toBe(true);
    expect(yesEdge?.sourceHandle).toBe(FLOW_HANDLES.YES);
    expect(yesEdge?.targetHandle).toBe(FLOW_HANDLES.INPUT);
    expect(yesEdge?.style?.stroke).toBe(EDGE_REGISTRY.YES.color);
    expect(yesEdge?.animated).toBe(true);

    expect(cancelEdge?.label).toBe("Cancel");
    expect(cancelEdge?.data?.semantic).toBe("CANCEL");
    expect(cancelEdge?.sourceHandle).toBe(FLOW_HANDLES.NO);
    expect(cancelEdge?.targetHandle).toBe(FLOW_HANDLES.INPUT);
    expect(cancelEdge?.style?.stroke).toBe(EDGE_REGISTRY.CANCEL.color);
    expect(cancelEdge?.animated).toBe(false);
  });

  it("maps branch keywords to requested edge semantics", () => {
    expect(detectEdgeSemantic("success")).toBe("SUCCESS");
    expect(detectEdgeSemantic("if pass")).toBe("YES");
    expect(detectEdgeSemantic("invalid")).toBe("NO");
    expect(detectEdgeSemantic("if not approved")).toBe("NO");
    expect(detectEdgeSemantic("manager rejects request")).toBe("NO");
    expect(detectEdgeSemantic("người dùng hủy")).toBe("CANCEL");
    expect(detectEdgeSemantic("try again")).toBe("RETRY");
    expect(detectEdgeSemantic("timeout")).toBe("TIMEOUT");
    expect(detectEdgeSemantic("system cannot connect")).toBe("FAILURE");
  });

  it("ships a default sample that exercises every edge semantic keyword family", () => {
    const detectedSemantics = new Set(
      SAMPLE_REQUIREMENTS[0].value.split("\n").map((line) => detectEdgeSemantic(line)),
    );

    expect(detectedSemantics.has(DEFAULT_EDGE_SEMANTIC)).toBe(true);
    expect(detectedSemantics.has("YES")).toBe(true);
    expect(detectedSemantics.has("NO")).toBe(true);
    expect(detectedSemantics.has("SUCCESS")).toBe(true);
    expect(detectedSemantics.has("FAILURE")).toBe(true);
    expect(detectedSemantics.has("CANCEL")).toBe(true);
    expect(detectedSemantics.has("RETRY")).toBe(true);
  });

  it("round-trips React Flow state into the frontend flowchart DTO", () => {
    const reactNodes = createReactFlowNodes(ensureFlowchartNodePositions(sampleFlowchart));
    const reactEdges = createReactFlowEdges(sampleFlowchart);
    const editedNodes = reactNodes.map((node) =>
      node.id === "entry_action"
        ? {
            ...node,
            data: {
              ...node.data,
              label: "Tap Delete",
            },
            position: { x: 128.2, y: 256.7 },
          }
        : node,
    );

    const nextFlowchart = createFlowchartFromReactFlow(editedNodes, reactEdges);

    expect(nextFlowchart.nodes.find((node) => node.id === "entry_action")).toMatchObject({
      label: "Tap Delete",
      position: { x: 128, y: 257 },
    });
  });

  it("supports adding nodes, connecting nodes, deleting edges, and deleting nodes", () => {
    const withNode = addFlowNode(sampleFlowchart, DEFAULT_NODE_TYPE, "Notify User");
    const addedNode = withNode.nodes.find((node) => node.label === "Notify User");

    expect(addedNode).toBeDefined();
    expect(buildRequirementTextFromFlowchart(withNode)).toContain(
      "* Unconnected node: Notify User",
    );
    if (!addedNode) {
      throw new Error("Expected added node");
    }

    const withConnection = connectFlowNodes(
      withNode,
      {
        source: "branch_success_1",
        target: addedNode.id,
        sourceHandle: FLOW_HANDLES.OUTPUT,
        targetHandle: FLOW_HANDLES.INPUT,
      },
      DEFAULT_EDGE_SEMANTIC,
    );
    const addedEdge = withConnection.edges.find((edge) => edge.target === addedNode.id);

    expect(addedEdge).toBeDefined();
    expect(addedEdge?.type).toBe(DEFAULT_EDGE_SEMANTIC);
    if (!addedEdge) {
      throw new Error("Expected added edge");
    }

    const withoutEdge = deleteFlowEdge(withConnection, addedEdge.id);
    expect(withoutEdge.edges.some((edge) => edge.id === addedEdge?.id)).toBe(false);

    const withoutNode = deleteFlowNode(withoutEdge, addedNode.id);
    expect(withoutNode.nodes.some((node) => node.id === addedNode.id)).toBe(false);
  });

  it("derives edge semantics from React Flow source handles", () => {
    expect(getEdgeSemanticFromSourceHandle(FLOW_HANDLES.YES)).toBe("YES");
    expect(getEdgeSemanticFromSourceHandle(FLOW_HANDLES.NO)).toBe("NO");
    expect(getEdgeSemanticFromSourceHandle(FLOW_HANDLES.OUTPUT)).toBe(DEFAULT_EDGE_SEMANTIC);
    expect(getEdgeSemanticFromSourceHandle(FLOW_HANDLES.INPUT)).toBeUndefined();
  });

  it("classifies frontend node kinds from labels", () => {
    expect(detectNodeKind("If user confirms")).toBe("DECISION");
    expect(detectNodeKind("Nếu người dùng hủy")).toBe("DECISION");
    expect(detectNodeKind("Start")).toBe("START_END");
    expect(detectNodeKind("Kết thúc")).toBe("START_END");
    expect(detectNodeKind("Upload file")).toBe("INPUT_OUTPUT");
    expect(detectNodeKind("Nhập email")).toBe("INPUT_OUTPUT");
    expect(detectNodeKind("Show payment error")).toBe("ERROR");
    expect(detectNodeKind("Invalid password")).toBe("ERROR");
    expect(detectNodeKind("Delete Product")).toBe("ACTION");
  });

  it("auto-layout places start left of end with readable branch spacing", () => {
    const layouted = applyAutoLayout(sampleFlowchart);
    const start = layouted.nodes.find((node) => node.id === "start_node");
    const decision = layouted.nodes.find((node) => node.id === "decision_1");
    const yesBranch = layouted.nodes.find((node) => node.id === "branch_action_1");
    const noBranch = layouted.nodes.find((node) => node.id === "branch_action_2");
    const end = layouted.nodes.find((node) => node.id === "end_node");

    expect(start?.position?.x).toBeLessThan(end?.position?.x ?? 0);
    expect(decision?.position?.x).toBeLessThan(yesBranch?.position?.x ?? 0);
    expect(decision?.position?.x).toBeLessThan(noBranch?.position?.x ?? 0);
    expect(yesBranch?.position?.y).not.toBe(noBranch?.position?.y);
    expect(layouted.nodes.every((node) => node.position)).toBe(true);
  });
});

function expectAllNodesToHaveFinitePositions(flowchart: FlowchartDTO) {
  for (const node of flowchart.nodes) {
    expect(Number.isFinite(node.position?.x)).toBe(true);
    expect(Number.isFinite(node.position?.y)).toBe(true);
  }
}

function getRequiredNode(flowchart: FlowchartDTO, nodeId: string) {
  const node = flowchart.nodes.find((item) => item.id === nodeId);
  if (!node) {
    throw new Error(`Expected node ${nodeId}`);
  }

  return node;
}
