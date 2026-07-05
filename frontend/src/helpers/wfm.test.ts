import { describe, expect, expectTypeOf, it } from "vitest";

import {
  EDGE_RENDER_REGISTRY,
  EDGE_REGISTRY,
  NODE_RENDER_REGISTRY,
  WFM_ACTOR_TYPE_REGISTRY,
  WFM_NODE_ROLE_REGISTRY,
  WFM_TRANSITION_SEMANTIC_REGISTRY,
} from "@/constants";
import {
  applyFlowNodeChanges,
  createReactFlowEdges,
  ensureFlowchartNodePositions,
  updateFlowEdgeSemantic,
  updateFlowNodeLabel,
} from "@/helpers/flowchart";
import {
  addWfmNode,
  connectWfmNodes,
  deleteWfmNode,
  deleteWfmTransition,
  reactFlowToWfm,
  updateWfmNodeKind,
  updateWfmNodeMetadata,
  updateWfmNodeRole,
  updateWfmNodeTitle,
  updateWfmTransitionCondition,
  updateWfmTransitionDescription,
  updateWfmTransitionSemantic,
} from "@/helpers/react-flow-to-wfm";
import { createGenerateTestCasesPayload } from "@/helpers/test-case-generation";
import { wfmContainsUiOnlyFields } from "@/helpers/wfm-validation";
import { wfmToRequirementText } from "@/helpers/wfm-to-requirement";
import {
  createCanvasViewStateFromFlowchart,
  mergeCanvasViewStateFromFlowchart,
  wfmToFlowchartDTO,
} from "@/helpers/wfm-to-react-flow";
import type {
  WfmActorType,
  WfmDocument,
  WfmNodeRole,
  WfmTransitionSemantic,
} from "@/types/requirement";

const sampleWfm: WfmDocument = {
  schemaVersion: "1.0",
  modelType: "WORKFLOW_AST",
  workflow: {
    id: "login-flow",
    language: "en",
    title: "Login Flow",
  },
  ast: {
    actors: [
      { id: "USER", name: "User", type: "USER" },
      { id: "SYSTEM", name: "System", type: "SYSTEM" },
    ],
    annotations: [],
    nodes: [
      { id: "N1", kind: "START", role: "START", title: "Start" },
      {
        actorId: "USER",
        id: "N2",
        kind: "LOGIN_FORM_INPUT",
        role: "INPUT",
        title: "User enters username and password",
      },
      {
        actorId: "SYSTEM",
        id: "N3",
        kind: "CREDENTIAL_VALIDATION",
        role: "DECISION",
        title: "Credentials are valid?",
      },
      {
        actorId: "SYSTEM",
        id: "N4",
        kind: "REDIRECT_TO_DASHBOARD",
        role: "OUTPUT",
        title: "Redirect to Dashboard",
      },
      {
        actorId: "SYSTEM",
        id: "N5",
        kind: "INVALID_CREDENTIALS",
        role: "ERROR",
        title: "Show invalid username or password",
      },
      { id: "N6", kind: "END", role: "END", title: "End" },
    ],
    transitions: [
      { from: "N1", id: "T1", semantic: "DEFAULT", to: "N2" },
      { from: "N2", id: "T2", semantic: "DEFAULT", to: "N3" },
      { condition: "Credentials are valid", from: "N3", id: "T3", semantic: "YES", to: "N4" },
      { condition: "Credentials are invalid", from: "N3", id: "T4", semantic: "NO", to: "N5" },
      { from: "N4", id: "T5", semantic: "SUCCESS", to: "N6" },
      { from: "N5", id: "T6", semantic: "FAILURE", to: "N6" },
    ],
    variables: [],
  },
  extensions: {
    nodeKinds: [],
    transitionKinds: [],
  },
};

describe("WFM frontend adapters", () => {
  it("derives WFM registry types from registry keys", () => {
    expectTypeOf<WfmNodeRole>().toEqualTypeOf<keyof typeof WFM_NODE_ROLE_REGISTRY>();
    expectTypeOf<WfmTransitionSemantic>().toEqualTypeOf<
      keyof typeof WFM_TRANSITION_SEMANTIC_REGISTRY
    >();
    expectTypeOf<WfmActorType>().toEqualTypeOf<keyof typeof WFM_ACTOR_TYPE_REGISTRY>();

    expect(WFM_NODE_ROLE_REGISTRY.DECISION.label).toBe("Decision");
    expect(WFM_TRANSITION_SEMANTIC_REGISTRY.RETRY.label).toBe("Retry");
  });

  it("derives renderer registries from WFM roles and semantics", () => {
    expect(NODE_RENDER_REGISTRY.INPUT.flowNodeKind).toBe("INPUT_OUTPUT");
    expect(NODE_RENDER_REGISTRY.OUTPUT.flowNodeKind).toBe("INPUT_OUTPUT");
    expect(NODE_RENDER_REGISTRY.END.flowNodeType).toBe("END");
    expect(EDGE_RENDER_REGISTRY.SUCCESS.color).toBe(EDGE_REGISTRY.SUCCESS.color);
    expect(EDGE_RENDER_REGISTRY.RETRY.label).toBe("Retry");
  });

  it("converts WFM to the React Flow renderer DTO without UI fields in WFM", () => {
    const flowchart = wfmToFlowchartDTO(sampleWfm);

    expect(flowchart.nodes.find((node) => node.id === "N2")).toMatchObject({
      nodeKind: "INPUT_OUTPUT",
      type: "ACTION",
    });
    expect(flowchart.nodes.find((node) => node.id === "N3")).toMatchObject({
      nodeKind: "DECISION",
      type: "DECISION",
    });
    expect(flowchart.nodes.find((node) => node.id === "N5")).toMatchObject({
      nodeKind: "ERROR",
      type: "ACTION",
    });
    expect(flowchart.edges.find((edge) => edge.id === "T4")?.type).toBe("NO");
    expect(flowchart.mermaid).toContain("flowchart LR");
    expect(wfmContainsUiOnlyFields(sampleWfm)).toBe(false);
  });

  it("renders edge color by WFM semantic and preserves visible labels", () => {
    const flowchart = wfmToFlowchartDTO(sampleWfm);
    const reactEdges = createReactFlowEdges(flowchart, "T4");
    const noEdge = reactEdges.find((edge) => edge.id === "T4");

    expect(noEdge?.label).toBe("Credentials are invalid");
    expect(noEdge?.data?.label).toBe("Credentials are invalid");
    expect(noEdge?.style?.stroke).toBe(EDGE_REGISTRY.NO.color);
    expect(noEdge?.animated).toBe(true);
  });

  it("dragging nodes updates canvas view state only", () => {
    const positionedFlowchart = ensureFlowchartNodePositions(wfmToFlowchartDTO(sampleWfm));
    const initialViewState = createCanvasViewStateFromFlowchart(
      sampleWfm.workflow.id,
      positionedFlowchart,
    );
    const movedFlowchart = applyFlowNodeChanges(positionedFlowchart, [
      {
        dragging: false,
        id: "N2",
        position: { x: 420.4, y: 111.6 },
        type: "position",
      },
    ]);
    const nextViewState = mergeCanvasViewStateFromFlowchart(
      initialViewState,
      sampleWfm.workflow.id,
      movedFlowchart,
    );

    expect(nextViewState.nodePositions.N2).toEqual({ x: 420, y: 112 });
    expect(sampleWfm.ast.nodes.find((node) => node.id === "N2")).not.toHaveProperty("position");
    expect(wfmContainsUiOnlyFields(sampleWfm)).toBe(false);
  });

  it("builds Generate Test Cases payload from WFM instead of dragged React Flow state", () => {
    const positionedFlowchart = ensureFlowchartNodePositions(wfmToFlowchartDTO(sampleWfm));
    const movedFlowchart = applyFlowNodeChanges(positionedFlowchart, [
      {
        dragging: false,
        id: "N2",
        position: { x: 900, y: 240 },
        type: "position",
      },
    ]);

    const payload = createGenerateTestCasesPayload({
      flowchart: movedFlowchart,
      requirement: "Feature: Login Flow",
      wfm: sampleWfm,
    });

    expect(payload.wfm).toBe(sampleWfm);
    expect(payload.flowchart).toBeUndefined();
    expect(payload.wfm?.ast.nodes.find((node) => node.id === "N2")).not.toHaveProperty("position");
  });

  it("editing node titles and edge semantics updates WFM business fields", () => {
    const flowchart = wfmToFlowchartDTO(sampleWfm);
    const renamedFlowchart = updateFlowNodeLabel(flowchart, "N4", "Open Dashboard");
    const semanticFlowchart = updateFlowEdgeSemantic(renamedFlowchart, "T4", "CANCEL");
    const nextWfm = reactFlowToWfm(sampleWfm, semanticFlowchart);

    expect(nextWfm.ast.nodes.find((node) => node.id === "N4")?.title).toBe("Open Dashboard");
    expect(nextWfm.ast.transitions.find((transition) => transition.id === "T4")?.semantic).toBe(
      "CANCEL",
    );
    expect(wfmContainsUiOnlyFields(nextWfm)).toBe(false);
  });

  it("updates WFM node business fields directly", () => {
    const renamed = updateWfmNodeTitle(sampleWfm, "N4", "Open Dashboard");
    const roleChanged = updateWfmNodeRole(renamed, "N4", "SUBPROCESS");
    const kindChanged = updateWfmNodeKind(roleChanged, "N4", "OPEN_DASHBOARD_FLOW");
    const metadataChanged = updateWfmNodeMetadata(kindChanged, "N4", {
      actorId: "SYSTEM",
      description: "Display the authenticated dashboard",
    });

    const node = metadataChanged.ast.nodes.find((item) => item.id === "N4");
    expect(node).toMatchObject({
      actorId: "SYSTEM",
      description: "Display the authenticated dashboard",
      kind: "OPEN_DASHBOARD_FLOW",
      role: "SUBPROCESS",
      title: "Open Dashboard",
    });
    expect(wfmContainsUiOnlyFields(metadataChanged)).toBe(false);
  });

  it("updates WFM transition business fields directly", () => {
    const semanticChanged = updateWfmTransitionSemantic(sampleWfm, "T4", "CANCEL");
    const conditionChanged = updateWfmTransitionCondition(
      semanticChanged,
      "T4",
      "User cancels login",
    );
    const descriptionChanged = updateWfmTransitionDescription(
      conditionChanged,
      "T4",
      "Cancellation branch",
    );

    expect(descriptionChanged.ast.transitions.find((item) => item.id === "T4")).toMatchObject({
      condition: "User cancels login",
      description: "Cancellation branch",
      semantic: "CANCEL",
    });
    expect(wfmContainsUiOnlyFields(descriptionChanged)).toBe(false);
  });

  it("adds, connects, and deletes WFM nodes and transitions without UI fields", () => {
    const added = addWfmNode(sampleWfm, "OUTPUT", { title: "Show retry guidance" });
    const connected = connectWfmNodes(added.wfm, { source: "N5", target: added.node.id }, "RETRY");
    const transition = connected.ast.transitions.find(
      (item) => item.from === "N5" && item.to === added.node.id,
    );

    expect(added.node).toMatchObject({
      kind: "OUTPUT",
      role: "OUTPUT",
      title: "Show retry guidance",
    });
    expect(transition).toMatchObject({ semantic: "RETRY" });

    const withoutTransition = transition
      ? deleteWfmTransition(connected, transition.id)
      : connected;
    expect(
      withoutTransition.ast.transitions.some(
        (item) => item.from === "N5" && item.to === added.node.id,
      ),
    ).toBe(false);

    const withoutNode = deleteWfmNode(connected, added.node.id);
    expect(withoutNode.ast.nodes.some((item) => item.id === added.node.id)).toBe(false);
    expect(
      withoutNode.ast.transitions.some(
        (item) => item.from === added.node.id || item.to === added.node.id,
      ),
    ).toBe(false);
    expect(wfmContainsUiOnlyFields(withoutNode)).toBe(false);
  });

  it("generates readable requirement text from WFM", () => {
    const requirement = wfmToRequirementText(sampleWfm);

    expect(requirement).toContain("Feature: Login Flow");
    expect(requirement).toContain("Input: User enters username and password");
    expect(requirement).toContain("If Credentials are valid, Redirect to Dashboard");
    expect(requirement).toContain("If Credentials are invalid, Show invalid username or password");
  });
});
