package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.ai.AiProvider;
import com.reqpilot.dto.ReactFlowDefinition;
import com.reqpilot.dto.ReactFlowEdge;
import com.reqpilot.dto.ReactFlowNode;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import com.reqpilot.wfm.WfmValidationException;
import com.reqpilot.wfm.WfmValidator;
import com.reqpilot.wfm.WfmDefinitionNormalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReactFlowGenerationServiceTest {

  private final ReactFlowGenerationService service =
      new ReactFlowGenerationService(new ObjectMapper(), new WfmValidator());
  private final WfmDefinitionNormalizer wfmDefinitionNormalizer = new WfmDefinitionNormalizer();

  @Test
  void generatesReactFlowGraphFromValidWfm() {
    ReactFlowDefinition graph = service.generateFromWfm(purchaseRequestWfm());

    assertThat(graph.workflowName()).isEqualTo("Purchase Request Approval");
    assertThat(graph.version()).isEqualTo("1.0");
    assertThat(graph.format()).isEqualTo("REACT_FLOW");
    assertThat(graph.direction()).isEqualTo("LR");
    assertThat(graph.nodes()).hasSize(7);
    assertThat(graph.edges()).hasSize(8);
  }

  @Test
  void mapsWfmKindsToReactFlowNodeTypes() {
    ReactFlowDefinition graph = service.generateFromWfm(purchaseRequestWfm());

    assertThat(node(graph, "start").type()).isEqualTo("start");
    assertThat(node(graph, "create_purchase_request").type()).isEqualTo("action");
    assertThat(node(graph, "manager_approval").type()).isEqualTo("approval");
    assertThat(node(graph, "check_amount").type()).isEqualTo("decision");
    assertThat(node(graph, "approved").type()).isEqualTo("end");
    assertThat(node(graph, "manager_approval").data())
        .containsEntry("label", "Manager approval")
        .containsEntry("kind", "approval")
        .containsEntry("actor", "Manager")
        .containsEntry("sourceWfmNodeId", "manager_approval");
  }

  @Test
  void mapsEdgesToSmoothstepEdgesWithConditionLabels() {
    ReactFlowDefinition graph = service.generateFromWfm(purchaseRequestWfm());
    ReactFlowEdge edge = edge(graph, "edge_manager_approval_rejected");

    assertThat(edge.source()).isEqualTo("manager_approval");
    assertThat(edge.target()).isEqualTo("rejected");
    assertThat(edge.type()).isEqualTo("smoothstep");
    assertThat(edge.label()).isEqualTo("Manager rejects");
    assertThat(edge.data())
        .containsEntry("condition", "Manager rejects")
        .containsEntry("sourceWfmEdgeId", "edge_manager_approval_rejected");
  }

  @Test
  void positionsAreDeterministic() {
    ReactFlowDefinition graph = service.generateFromWfm(purchaseRequestWfm());

    assertThat(node(graph, "start").position().x()).isEqualTo(0.0);
    assertThat(node(graph, "start").position().y()).isEqualTo(0.0);
    assertThat(node(graph, "create_purchase_request").position().x()).isEqualTo(300.0);
    assertThat(node(graph, "manager_approval").position().x()).isEqualTo(600.0);
    assertThat(node(graph, "check_amount").position().x()).isEqualTo(900.0);
    assertThat(node(graph, "check_amount").position().y()).isEqualTo(-80.0);
    assertThat(node(graph, "rejected").position().x()).isEqualTo(900.0);
    assertThat(node(graph, "rejected").position().y()).isEqualTo(80.0);
    assertThat(node(graph, "finance_approval").position().x()).isEqualTo(1200.0);
    assertThat(node(graph, "finance_approval").position().y()).isEqualTo(-80.0);
    assertThat(node(graph, "approved").position().x()).isEqualTo(1200.0);
    assertThat(node(graph, "approved").position().y()).isEqualTo(80.0);
  }

  @Test
  void unknownKindIsRenderedAsCustomNode() {
    WfmDefinition wfm =
        new WfmDefinition(
            "Custom Flow",
            "1.0",
            "summary",
            List.of(),
            List.of(
                node("start", "start", "Start", null),
                node("risk gate", "custom_risk_gate", "Custom risk gate", null),
                node("end", "end", "End", null)),
            List.of(edge("edge_1", "start", "risk gate", null, null), edge("edge_2", "risk gate", "end", null, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "LOW");

    ReactFlowDefinition graph = service.generateFromWfm(wfm);

    assertThat(node(graph, "risk_gate").type()).isEqualTo("custom");
    assertThat(node(graph, "risk_gate").data()).containsEntry("sourceWfmNodeId", "risk gate");
    assertThat(edge(graph, "edge_1").target()).isEqualTo("risk_gate");
  }

  @Test
  void unreachableNodesGenerateWarningAndArePositionedAfterReachableNodes() {
    WfmDefinition wfm =
        new WfmDefinition(
            "Unreachable Flow",
            "1.0",
            "summary",
            List.of(),
            List.of(
                node("start", "start", "Start", null),
                node("end", "end", "End", null),
                node("manual_review", "action", "Manual review", null)),
            List.of(edge("edge_1", "start", "end", null, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "LOW");

    ReactFlowDefinition graph = service.generateFromWfm(wfm);

    assertThat(graph.warnings()).contains("Unreachable node placed separately: manual_review");
    assertThat(node(graph, "manual_review").position().x()).isEqualTo(600.0);
    assertThat(node(graph, "manual_review").position().y()).isEqualTo(0.0);
  }

  @Test
  void rejectsNullWfm() {
    assertThatThrownBy(() -> service.generateFromWfm(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WFM is required");
  }

  @Test
  void rejectsInvalidWfmByUsingValidator() {
    WfmDefinition invalid =
        new WfmDefinition(
            "Invalid Flow",
            "1.0",
            "summary",
            List.of(),
            List.of(node("start", "start", "Start", null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "LOW");

    assertThatThrownBy(() -> service.generateFromWfm(invalid)).isInstanceOf(WfmValidationException.class);
  }

  @Test
  void parsesRawWfmJson() {
    String json =
        """
        {
          "workflowName": "Tiny Flow",
          "version": "1.0",
          "summary": "summary",
          "actors": [],
          "nodes": [
            {"id": "start", "kind": "start", "label": "Start", "metadata": {}},
            {"id": "end", "kind": "end", "label": "End", "metadata": {}}
          ],
          "edges": [
            {"id": "edge_1", "from": "start", "to": "end", "metadata": {}}
          ],
          "businessRules": [],
          "validations": [],
          "assumptions": [],
          "edgeCases": [],
          "openQuestions": [],
          "riskLevel": "LOW"
        }
        """;

    ReactFlowDefinition graph = service.generateFromWfmJson(json);

    assertThat(graph.format()).isEqualTo("REACT_FLOW");
    assertThat(graph.edges()).extracting(ReactFlowEdge::source).contains("start");
  }

  @Test
  void normalizedWfmKeepsBusinessConditionsOnDecisionNodeForReactFlow() {
    WfmDefinition normalized = wfmDefinitionNormalizer.normalize(badPurchaseApprovalWfm()).wfm();

    ReactFlowDefinition graph = service.generateFromWfm(normalized);

    assertThat(graph.nodes()).extracting(ReactFlowNode::id).contains("check_amount");
    assertThat(edge(graph, "manager_to_finance").source()).isEqualTo("check_amount");
    assertThat(edge(graph, "manager_to_finance").target()).isEqualTo("finance_approval");
    assertThat(edge(graph, "manager_to_finance").label()).isEqualTo("amount > 5000");
    assertThat(edge(graph, "manager_to_end_approved").source()).isEqualTo("check_amount");
    assertThat(edge(graph, "manager_to_end_approved").target()).isEqualTo("end_approved");
    assertThat(graph.edges())
        .noneMatch(
            (edge) ->
                edge.source().equals("manager_approval")
                    && List.of("amount > 5000", "amount <= 5000").contains(edge.label()));
  }

  @Test
  void doesNotDependOnAiProvider() {
    assertThat(Arrays.stream(ReactFlowGenerationService.class.getDeclaredFields())
            .map((field) -> field.getType())
            .filter(AiProvider.class::isAssignableFrom))
        .isEmpty();
  }

  private ReactFlowNode node(ReactFlowDefinition graph, String id) {
    return graph.nodes().stream().filter((node) -> node.id().equals(id)).findFirst().orElseThrow();
  }

  private ReactFlowEdge edge(ReactFlowDefinition graph, String id) {
    return graph.edges().stream().filter((edge) -> edge.id().equals(id)).findFirst().orElseThrow();
  }

  private WfmDefinition purchaseRequestWfm() {
    return new WfmDefinition(
        "Purchase Request Approval",
        "1.0",
        "User creates a purchase request. Manager and Finance approvals may be required.",
        List.of("User", "Manager", "Finance"),
        List.of(
            node("start", "start", "Start", null),
            node("create_purchase_request", "action", "Create purchase request", "User"),
            node("manager_approval", "approval", "Manager approval", "Manager"),
            node("check_amount", "decision", "Is amount greater than 5000?", null),
            node("finance_approval", "approval", "Finance approval", "Finance"),
            node("approved", "end", "Request approved", null),
            node("rejected", "end", "Request rejected", null)),
        List.of(
            edge("edge_start_create_purchase_request", "start", "create_purchase_request", null, null),
            edge("edge_create_purchase_request_manager_approval", "create_purchase_request", "manager_approval", null, null),
            edge("edge_manager_approval_rejected", "manager_approval", "rejected", "Reject", "Manager rejects"),
            edge("edge_manager_approval_check_amount", "manager_approval", "check_amount", "Approve", "Manager approves"),
            edge("edge_check_amount_finance_approval", "check_amount", "finance_approval", "Yes", "amount > 5000"),
            edge("edge_check_amount_approved", "check_amount", "approved", "No", "amount <= 5000"),
            edge("edge_finance_approval_approved", "finance_approval", "approved", "Approve", "Finance approves"),
            edge("edge_finance_approval_rejected", "finance_approval", "rejected", "Reject", "Finance rejects")),
        List.of("Manager approval is required for all purchase requests."),
        List.of("Amount must be positive."),
        List.of("Role-based access control exists."),
        List.of("Manager rejects request."),
        List.of("Is there an approval time limit?"),
        "MEDIUM");
  }

  private WfmDefinition badPurchaseApprovalWfm() {
    return new WfmDefinition(
        "PurchaseRequestApproval",
        "1.0",
        "Purchase request approval workflow.",
        List.of("User", "Manager", "Finance"),
        List.of(
            node("start", "start", "Start", null),
            node("create_purchase_request", "action", "Create Purchase Request", "User"),
            node("manager_approval", "approval", "Manager Approval", "Manager"),
            node("finance_approval", "approval", "Finance Approval", "Finance"),
            node("end_approved", "end", "End Approved", null),
            node("end_rejected", "end", "End Rejected", null)),
        List.of(
            edge("start_to_create", "start", "create_purchase_request", null, null),
            edge("create_to_manager", "create_purchase_request", "manager_approval", null, null),
            edge("manager_to_finance", "manager_approval", "finance_approval", "amount > 5000", "amount > 5000"),
            edge("manager_to_end_approved", "manager_approval", "end_approved", "amount <= 5000", "amount <= 5000"),
            edge("manager_to_end_rejected", "manager_approval", "end_rejected", "Rejected", null),
            edge("finance_to_end_approved", "finance_approval", "end_approved", "Approved", null),
            edge("finance_to_end_rejected", "finance_approval", "end_rejected", "Rejected", null)),
        List.of("Manager approval is required for all purchase requests."),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "MEDIUM");
  }

  private WfmNode node(String id, String kind, String label, String actor) {
    return new WfmNode(id, kind, label, actor, null, Map.of());
  }

  private WfmEdge edge(String id, String from, String to, String label, String condition) {
    return new WfmEdge(id, from, to, label, condition, Map.of());
  }
}
