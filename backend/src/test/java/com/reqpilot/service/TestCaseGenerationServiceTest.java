package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.ai.AiProvider;
import com.reqpilot.dto.TestCase;
import com.reqpilot.dto.TestCaseSuite;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import com.reqpilot.wfm.WfmValidationException;
import com.reqpilot.wfm.WfmValidator;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestCaseGenerationServiceTest {

  private final TestCaseGenerationService service =
      new TestCaseGenerationService(new ObjectMapper(), new WfmValidator());

  @Test
  void generatesSuiteFromValidWfm() {
    TestCaseSuite suite = service.generateFromWfm(purchaseRequestWfm());

    assertThat(suite.suiteName()).isEqualTo("Purchase Request Approval Test Suite");
    assertThat(suite.version()).isEqualTo("1.0");
    assertThat(suite.sourceWorkflowName()).isEqualTo("Purchase Request Approval");
    assertThat(suite.testCases()).extracting(TestCase::type)
        .contains(
            "HAPPY_PATH",
            "APPROVAL_APPROVE",
            "APPROVAL_REJECT",
            "DECISION_BRANCH",
            "VALIDATION",
            "EDGE_CASE");
    assertThat(suite.warnings())
        .contains("Open question requires clarification: Is there a time limit for approvals?");
  }

  @Test
  void generatesStableIdsAndPriorityByType() {
    TestCaseSuite suite = service.generateFromWfm(purchaseRequestWfm());

    assertThat(suite.testCases()).extracting(TestCase::id)
        .startsWith("TC-001", "TC-002", "TC-003");
    assertThat(suite.testCases()).extracting(TestCase::type)
        .startsWith(
            "HAPPY_PATH",
            "APPROVAL_APPROVE",
            "APPROVAL_APPROVE",
            "APPROVAL_REJECT",
            "APPROVAL_REJECT",
            "DECISION_BRANCH");
    assertThat(caseByType(suite, "HAPPY_PATH").priority()).isEqualTo("P0");
    assertThat(caseByType(suite, "APPROVAL_APPROVE").priority()).isEqualTo("P1");
    assertThat(caseByType(suite, "APPROVAL_REJECT").priority()).isEqualTo("P1");
    assertThat(caseByType(suite, "DECISION_BRANCH").priority()).isEqualTo("P1");
    assertThat(caseByType(suite, "VALIDATION").priority()).isEqualTo("P1");
    assertThat(caseByType(suite, "EDGE_CASE").priority()).isEqualTo("P2");
  }

  @Test
  void happyPathUsesPathFromStartToPositiveEnd() {
    TestCase happyPath = caseByType(service.generateFromWfm(purchaseRequestWfm()), "HAPPY_PATH");

    assertThat(happyPath.sourceNodeIds())
        .contains("start", "create_purchase_request", "manager_approval", "check_amount", "approved");
    assertThat(happyPath.sourceEdgeIds())
        .contains(
            "edge_start_create_purchase_request",
            "edge_create_purchase_request_manager_approval",
            "edge_manager_approval_check_amount",
            "edge_check_amount_approved");
    assertThat(happyPath.steps()).extracting("action")
        .contains("Create purchase request", "Manager approval", "Proceed with branch: amount <= 5000");
    assertThat(happyPath.expectedResults())
        .containsExactly("Workflow reaches the successful end state: Request approved.");
    assertThat(happyPath.preconditions())
        .containsExactly(
            "User is logged in.",
            "Required roles and permissions are configured.",
            "Workflow configuration is available.");
  }

  @Test
  void approvalCasesIncludeApproveAndReject() {
    TestCaseSuite suite = service.generateFromWfm(purchaseRequestWfm());

    assertThat(suite.testCases()).filteredOn((testCase) -> testCase.type().equals("APPROVAL_APPROVE"))
        .extracting(TestCase::title)
        .contains("Approval approve - Manager approval", "Approval approve - Finance approval");
    assertThat(suite.testCases()).filteredOn((testCase) -> testCase.type().equals("APPROVAL_REJECT"))
        .extracting(TestCase::title)
        .contains("Approval reject - Manager approval", "Approval reject - Finance approval");
    assertThat(caseByType(suite, "APPROVAL_APPROVE").preconditions())
        .contains("The request is submitted and waiting for approval.");
    assertThat(caseByType(suite, "APPROVAL_REJECT").expectedResults())
        .containsExactly("The request is rejected and the workflow reaches the rejection end state.");
  }

  @Test
  void approvalApproveCaseIsGeneratedWithoutExplicitApproveEdge() {
    TestCaseSuite suite = service.generateFromWfm(approvalWithoutOutgoingEdgeWfm());

    TestCase approveCase = findCaseByTitle(suite, "Approval approve - Manual review");

    assertThat(approveCase.type()).isEqualTo("APPROVAL_APPROVE");
    assertThat(approveCase.sourceNodeIds()).containsExactly("manual_review");
    assertThat(approveCase.sourceEdgeIds()).isEmpty();
  }

  @Test
  void decisionBranchCasesInferGenericNumericInputData() {
    TestCaseSuite suite = service.generateFromWfm(purchaseRequestWfm());

    assertThat(findCaseByTitle(suite, "Decision branch - amount > 5000")
            .steps().get(0).inputData())
        .containsEntry("amount", 5001);
    assertThat(findCaseByTitle(suite, "Decision branch - amount <= 5000")
            .steps().get(0).inputData())
        .containsEntry("amount", 5000);
    assertThat(findCaseByTitle(suite, "Decision branch - totalAmount >= 10000")
            .steps().get(0).inputData())
        .containsEntry("totalAmount", 10000);
    assertThat(findCaseByTitle(suite, "Decision branch - quantity < 1")
            .steps().get(0).inputData())
        .containsEntry("quantity", 0);
    assertThat(findCaseByTitle(suite, "Decision branch - amount == 5000")
            .steps().get(0).inputData())
        .containsEntry("amount", 5000);
  }

  @Test
  void validationAndEdgeCaseCasesAreGenerated() {
    TestCaseSuite suite = service.generateFromWfm(purchaseRequestWfm());

    assertThat(suite.testCases()).filteredOn((testCase) -> testCase.type().equals("VALIDATION"))
        .extracting(TestCase::title)
        .contains("Validation - Amount must be a positive number.");
    assertThat(caseByType(suite, "VALIDATION").preconditions())
        .contains("User is on the relevant input screen.");
    assertThat(caseByType(suite, "VALIDATION").expectedResults())
        .containsExactly("The system rejects invalid input and displays an appropriate validation message.");
    assertThat(suite.testCases()).filteredOn((testCase) -> testCase.type().equals("EDGE_CASE"))
        .extracting(TestCase::title)
        .contains("Edge case - What happens if the manager rejects the request?");
    assertThat(caseByType(suite, "EDGE_CASE").expectedResults())
        .containsExactly("The system handles the edge case safely without data corruption or unexpected workflow state.");
  }

  @Test
  void calculatesNodeAndEdgeCoverage() {
    TestCaseSuite suite = service.generateFromWfm(purchaseRequestWfm());

    assertThat(suite.coverage().nodeCount()).isEqualTo(7);
    assertThat(suite.coverage().edgeCount()).isEqualTo(11);
    assertThat(suite.coverage().coveredNodeIds())
        .containsExactly(
            "start",
            "create_purchase_request",
            "manager_approval",
            "check_amount",
            "finance_approval",
            "approved",
            "rejected");
    assertThat(suite.coverage().coveredEdgeIds())
        .contains(
            "edge_check_amount_finance_approval",
            "edge_check_amount_approved",
            "edge_check_amount_manual_review",
            "edge_check_amount_cancelled",
            "edge_check_amount_pending");
    assertThat(suite.coverage().uncoveredNodeIds()).isEmpty();
    assertThat(suite.coverage().uncoveredEdgeIds()).isEmpty();
  }

  @Test
  void generatesStableFallbackEdgeIdWhenWfmEdgeIdIsBlank() {
    TestCaseSuite suite = service.generateFromWfm(wfmWithBlankEdgeId());

    assertThat(suite.coverage().coveredEdgeIds()).contains("edge_start_done");
    assertThat(caseByType(suite, "HAPPY_PATH").sourceEdgeIds()).containsExactly("edge_start_done");
  }

  @Test
  void addsWarningsForUnreachableNodesAndEdges() {
    TestCaseSuite suite = service.generateFromWfm(wfmWithUnreachableBranch());

    assertThat(suite.warnings())
        .contains(
            "Unreachable node from start: orphan_action",
            "Unreachable edge from start: edge_orphan_done");
  }

  @Test
  void doesNotDependOnAiProvider() {
    assertThat(Arrays.stream(TestCaseGenerationService.class.getDeclaredFields())
            .map((field) -> field.getType())
            .filter(AiProvider.class::isAssignableFrom))
        .isEmpty();
  }

  @Test
  void supportsRawJsonInput() {
    String json =
        """
        {
          "workflowName": "Tiny Flow",
          "version": "1.0",
          "summary": "summary",
          "actors": ["User"],
          "nodes": [
            {"id": "start", "kind": "start", "label": "Start", "metadata": {}},
            {"id": "done", "kind": "end", "label": "Done", "metadata": {}}
          ],
          "edges": [
            {"id": "edge_1", "from": "start", "to": "done", "metadata": {}}
          ],
          "businessRules": [],
          "validations": [],
          "assumptions": [],
          "edgeCases": [],
          "openQuestions": [],
          "riskLevel": "LOW"
        }
        """;

    TestCaseSuite suite = service.generateFromWfmJson(json);

    assertThat(suite.testCases()).extracting(TestCase::type).contains("HAPPY_PATH");
  }

  @Test
  void rejectsNullWfm() {
    assertThatThrownBy(() -> service.generateFromWfm(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WFM is required");
  }

  @Test
  void rejectsInvalidWfmThroughValidator() {
    WfmDefinition invalid =
        new WfmDefinition(
            "Invalid",
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

  private TestCase caseByType(TestCaseSuite suite, String type) {
    return suite.testCases().stream()
        .filter((testCase) -> testCase.type().equals(type))
        .findFirst()
        .orElseThrow();
  }

  private TestCase findCaseByTitle(TestCaseSuite suite, String title) {
    return suite.testCases().stream()
        .filter((testCase) -> testCase.title().equals(title))
        .findFirst()
        .orElseThrow();
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
            edge("edge_check_amount_manual_review", "check_amount", "approved", "Review", "totalAmount >= 10000"),
            edge("edge_check_amount_cancelled", "check_amount", "rejected", "Cancel", "quantity < 1"),
            edge("edge_check_amount_pending", "check_amount", "finance_approval", "Exact amount", "amount == 5000"),
            edge("edge_finance_approval_approved", "finance_approval", "approved", "Approve", "Finance approves"),
            edge("edge_finance_approval_rejected", "finance_approval", "rejected", "Reject", "Finance rejects")),
        List.of("Manager approval is required for all purchase requests."),
        List.of("Amount must be a positive number."),
        List.of("Role-based access control exists."),
        List.of("What happens if the manager rejects the request?"),
        List.of("Is there a time limit for approvals?"),
        "MEDIUM");
  }

  private WfmDefinition approvalWithoutOutgoingEdgeWfm() {
    return new WfmDefinition(
        "Manual Review",
        "1.0",
        "A submitted item waits for manual approval.",
        List.of("Reviewer"),
        List.of(
            node("start", "start", "Start", null),
            node("manual_review", "approval", "Manual review", "Reviewer"),
            node("done", "end", "Done", null)),
        List.of(edge("edge_start_manual_review", "start", "manual_review", null, null)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "LOW");
  }

  private WfmDefinition wfmWithBlankEdgeId() {
    return new WfmDefinition(
        "Blank Edge Id Flow",
        "1.0",
        "A tiny flow with an omitted edge id.",
        List.of("User"),
        List.of(node("start", "start", "Start", null), node("done", "end", "Done", null)),
        List.of(edge(null, "start", "done", null, null)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "LOW");
  }

  private WfmDefinition wfmWithUnreachableBranch() {
    return new WfmDefinition(
        "Unreachable Flow",
        "1.0",
        "A tiny flow with an unreachable action.",
        List.of("User"),
        List.of(
            node("start", "start", "Start", null),
            node("done", "end", "Done", null),
            node("orphan_action", "action", "Orphan action", "User")),
        List.of(
            edge("edge_start_done", "start", "done", null, null),
            edge("edge_orphan_done", "orphan_action", "done", null, null)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "LOW");
  }

  private WfmNode node(String id, String kind, String label, String actor) {
    return new WfmNode(id, kind, label, actor, null, Map.of());
  }

  private WfmEdge edge(String id, String from, String to, String label, String condition) {
    return new WfmEdge(id, from, to, label, condition, Map.of());
  }
}
