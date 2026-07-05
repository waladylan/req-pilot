package com.reqpilot.wfm;

import static org.assertj.core.api.Assertions.assertThat;

import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WfmDefinitionQualityTest {

  private final WfmValidator structuralValidator = new WfmValidator();
  private final WfmSemanticValidator semanticValidator = new WfmSemanticValidator();
  private final WfmDefinitionNormalizer normalizer = new WfmDefinitionNormalizer();

  @Test
  void semanticValidatorMarksApprovalBusinessConditionBranchesAsRepairable() {
    WfmQualityReport report = semanticValidator.validate(badPurchaseApprovalWfm());

    assertThat(report.valid()).isFalse();
    assertThat(report.repairable()).isTrue();
    assertThat(report.errors())
        .anySatisfy((error) -> assertThat(error).contains("manager_approval").contains("amount > 5000"));
    assertThat(report.warnings())
        .anySatisfy((warning) -> assertThat(warning).contains("should originate from a decision node"));
  }

  @Test
  void normalizerRepairsApprovalBusinessConditionBranches() {
    WfmNormalizationResult result = normalizer.normalize(badPurchaseApprovalWfm());
    WfmDefinition normalized = result.wfm();

    assertThat(result.report().repairs())
        .contains("Moved amount condition branches from approval node 'manager_approval' to decision node 'check_amount'.");
    assertThat(structuralValidator.validateDefinition(normalized).valid()).isTrue();
    assertThat(semanticValidator.validate(normalized).valid()).isTrue();
    assertThat(normalized.workflowName()).isEqualTo("Purchase Request Approval");
    assertThat(normalized.nodes()).extracting(WfmNode::id).contains("check_amount");
    assertThat(node(normalized, "check_amount").kind()).isEqualTo("decision");

    assertThat(edge(normalized, "manager_approval", "finance_approval", "amount > 5000")).isNull();
    assertThat(edge(normalized, "manager_approval", "end_approved", "amount <= 5000")).isNull();
    assertThat(edge(normalized, "manager_approval", "check_amount", "Manager approves").label()).isEqualTo("Approve");
    assertThat(edge(normalized, "manager_approval", "end_rejected", "Manager rejects").label()).isEqualTo("Reject");
    assertThat(edge(normalized, "check_amount", "finance_approval", "amount > 5000").label()).isEqualTo("amount > 5000");
    assertThat(edge(normalized, "check_amount", "end_approved", "amount <= 5000").label()).isEqualTo("amount <= 5000");
    assertThat(edge(normalized, "finance_approval", "end_approved", "Finance approves").label()).isEqualTo("Approve");
    assertThat(edge(normalized, "finance_approval", "end_rejected", "Finance rejects").label()).isEqualTo("Reject");
  }

  @Test
  void canonicalWfmNormalizesWithoutDuplicateDecisionNodesOrEdges() {
    WfmDefinition normalized = normalizer.normalize(canonicalPurchaseApprovalWfm()).wfm();

    assertThat(normalized.nodes()).filteredOn((node) -> node.id().equals("check_amount")).hasSize(1);
    assertThat(normalized.edges()).extracting(WfmEdge::id).doesNotHaveDuplicates();
    assertThat(normalized.nodes()).extracting(WfmNode::id)
        .containsExactly(
            "start",
            "create_purchase_request",
            "manager_approval",
            "check_amount",
            "finance_approval",
            "approved",
            "rejected");
    assertThat(normalized.edges()).extracting(WfmEdge::id)
        .contains(
            "edge_manager_approval_check_amount",
            "edge_check_amount_finance_approval",
            "edge_check_amount_approved");
    assertThat(semanticValidator.validate(normalized).valid()).isTrue();
  }

  @Test
  void unknownNodeKindRemainsAllowedAndPreserved() {
    WfmDefinition wfm =
        new WfmDefinition(
            "Notification Flow",
            "1.0",
            "summary",
            List.of("System"),
            List.of(
                node("start", "start", "Start", null),
                node("send_notification", "notification", "Send Notification", "System"),
                node("end", "end", "End", null)),
            List.of(
                edge("edge_start_notification", "start", "send_notification", null, null),
                edge("edge_notification_end", "send_notification", "end", null, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "LOW");

    WfmDefinition normalized = normalizer.normalize(wfm).wfm();

    assertThat(structuralValidator.validateDefinition(normalized).valid()).isTrue();
    assertThat(semanticValidator.validate(normalized).valid()).isTrue();
    assertThat(node(normalized, "send_notification").kind()).isEqualTo("notification");
  }

  @Test
  void numericConditionDetectionIsNarrow() {
    assertThat(List.of(
            "amount > 5000",
            "amount <= 5000",
            "totalAmount >= 10000",
            "quantity < 1",
            "price == 0",
            "score = 100"))
        .allSatisfy((condition) -> assertThat(semanticValidator.isNumericBusinessCondition(condition)).isTrue());

    assertThat(List.of(
            "Manager approves",
            "Finance rejects",
            "User submits request",
            "Approved",
            "Rejected"))
        .allSatisfy((condition) -> assertThat(semanticValidator.isNumericBusinessCondition(condition)).isFalse());
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
            node("end_approved", "end", "End (Approved)", null),
            node("end_rejected", "end", "End (Rejected)", null)),
        List.of(
            edge("start_to_create", "start", "create_purchase_request", null, null),
            edge("create_to_manager", "create_purchase_request", "manager_approval", null, null),
            edge("manager_to_finance", "manager_approval", "finance_approval", "amount > 5000", "amount > 5000"),
            edge("manager_to_end_approved", "manager_approval", "end_approved", "amount <= 5000", "amount <= 5000"),
            edge("manager_to_end_rejected", "manager_approval", "end_rejected", "Rejected", null),
            edge("finance_to_end_approved", "finance_approval", "end_approved", "Approved", null),
            edge("finance_to_end_rejected", "finance_approval", "end_rejected", "Rejected", null)),
        List.of(
            "Manager approval is required for all purchase requests.",
            "Finance approval is required if amount exceeds 5000."),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "MEDIUM");
  }

  private WfmDefinition canonicalPurchaseApprovalWfm() {
    return new WfmDefinition(
        "Purchase Request Approval",
        "1.0",
        "Purchase request approval workflow.",
        List.of("User", "Manager", "Finance"),
        List.of(
            node("start", "start", "Start", null),
            node("create_purchase_request", "action", "Create Purchase Request", "User"),
            node("manager_approval", "approval", "Manager Approval", "Manager"),
            node("check_amount", "decision", "Check Amount", null),
            node("finance_approval", "approval", "Finance Approval", "Finance"),
            node("approved", "end", "Request Approved", null),
            node("rejected", "end", "Request Rejected", null)),
        List.of(
            edge("edge_start_create_purchase_request", "start", "create_purchase_request", null, null),
            edge("edge_create_purchase_request_manager_approval", "create_purchase_request", "manager_approval", null, null),
            edge("edge_manager_approval_rejected", "manager_approval", "rejected", "Reject", "Manager rejects"),
            edge("edge_manager_approval_check_amount", "manager_approval", "check_amount", "Approve", "Manager approves"),
            edge("edge_check_amount_finance_approval", "check_amount", "finance_approval", "amount > 5000", "amount > 5000"),
            edge("edge_check_amount_approved", "check_amount", "approved", "amount <= 5000", "amount <= 5000"),
            edge("edge_finance_approval_approved", "finance_approval", "approved", "Approve", "Finance approves"),
            edge("edge_finance_approval_rejected", "finance_approval", "rejected", "Reject", "Finance rejects")),
        List.of(),
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

  private WfmNode node(WfmDefinition wfm, String id) {
    return wfm.nodes().stream().filter((node) -> node.id().equals(id)).findFirst().orElseThrow();
  }

  private WfmEdge edge(WfmDefinition wfm, String from, String to, String condition) {
    return wfm.edges().stream()
        .filter((edge) -> edge.from().equals(from))
        .filter((edge) -> edge.to().equals(to))
        .filter((edge) -> condition.equals(edge.condition()))
        .findFirst()
        .orElse(null);
  }
}
