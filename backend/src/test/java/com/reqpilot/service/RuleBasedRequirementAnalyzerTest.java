package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import com.reqpilot.wfm.WfmNodeRole;
import com.reqpilot.wfm.WfmTransitionSemantic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleBasedRequirementAnalyzerTest {

  private final RuleBasedRequirementAnalyzer analyzer = new RuleBasedRequirementAnalyzer();

  @Test
  void parsesDeleteProductRequirementIntoDecisionBranches() {
    String requirement =
        """
        Feature delete product:

        * If user confirms, delete product
        * If user cancels, do nothing
        """;

    Flowchart flowchart = analyzer.analyze(requirement);

    assertThat(flowchart.nodes()).extracting("label").contains("Click Delete", "Confirm Dialog", "Delete Product", "Do Nothing");
    assertWellFormed(flowchart);
    assertThat(flowchart.nodes()).anyMatch((node) -> node.type() == FlowNodeType.DECISION);
    assertThat(flowchart.edges()).filteredOn((edge) -> "Confirm".equals(edge.label())).hasSize(1);
    assertThat(flowchart.edges()).filteredOn((edge) -> "Cancel".equals(edge.label())).hasSize(1);
    assertThat(flowchart.edges()).filteredOn((edge) -> edge.type() == FlowEdgeType.YES).hasSize(1);
    assertThat(flowchart.edges()).filteredOn((edge) -> edge.type() == FlowEdgeType.CANCEL).hasSizeGreaterThanOrEqualTo(1);
    assertThat(flowchart.mermaid()).contains("flowchart LR", "Show Success");
  }

  @Test
  void parsesVietnameseRequirement() {
    String requirement =
        """
        Tính năng xóa sản phẩm:

        * Nếu người dùng xác nhận, xóa sản phẩm
        * Nếu người dùng hủy, không làm gì
        """;

    Flowchart flowchart = analyzer.analyze(requirement);

    assertThat(flowchart.nodes()).extracting("label").contains("Nhấn Xóa", "Hộp thoại xác nhận", "Xóa sản phẩm", "Không làm gì");
    assertWellFormed(flowchart);
    assertThat(flowchart.edges()).filteredOn((edge) -> "Xác nhận".equals(edge.label())).hasSize(1);
    assertThat(flowchart.edges()).filteredOn((edge) -> "Hủy".equals(edge.label())).hasSize(1);
    assertThat(flowchart.edges()).filteredOn((edge) -> edge.type() == FlowEdgeType.YES).hasSize(1);
    assertThat(flowchart.edges()).filteredOn((edge) -> edge.type() == FlowEdgeType.CANCEL).hasSizeGreaterThanOrEqualTo(1);
  }

  @Test
  void mapsSuccessAndFailureKeywordsToDedicatedEdgeSemantics() {
    String requirement =
        """
        Feature payment:

        * If payment success, create order
        * If payment fails, show error
        """;

    Flowchart flowchart = analyzer.analyze(requirement);

    assertThat(flowchart.edges()).filteredOn((edge) -> edge.type() == FlowEdgeType.SUCCESS).hasSizeGreaterThanOrEqualTo(1);
    assertThat(flowchart.edges()).filteredOn((edge) -> edge.type() == FlowEdgeType.FAILURE).hasSizeGreaterThanOrEqualTo(1);
  }

  @Test
  void defaultLoginSampleExercisesCoreNodeTypesAndEdgeSemantics() {
    Flowchart flowchart = analyzer.analyze(loginRequirement());

    assertWellFormed(flowchart);
    assertThat(flowchart.nodes()).anyMatch((node) -> node.type() == FlowNodeType.START);
    assertThat(flowchart.nodes()).anyMatch((node) -> node.type() == FlowNodeType.ACTION);
    assertThat(flowchart.nodes()).anyMatch((node) -> node.type() == FlowNodeType.DECISION);
    assertThat(flowchart.nodes()).anyMatch((node) -> node.type() == FlowNodeType.END);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.DEFAULT);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.YES);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.NO);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.SUCCESS);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.FAILURE);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.CANCEL);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.RETRY);
  }

  @Test
  void parsesRegistrationFlowWithInvalidAndFailureBranches() {
    Flowchart flowchart = analyzer.analyze(registrationRequirement());

    assertWellFormed(flowchart);
    assertThat(flowchart.nodes()).extracting("label").contains("Start Registration", "Create User Account", "Show Validation Error");
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.YES);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.NO);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.FAILURE);
  }

  @Test
  void parsesCheckoutFlowWithCancelSuccessAndFailureBranches() {
    Flowchart flowchart = analyzer.analyze(checkoutRequirement());

    assertWellFormed(flowchart);
    assertThat(flowchart.nodes()).extracting("label").contains("Start Checkout", "Proceed To Payment", "Create Order");
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.YES);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.CANCEL);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.SUCCESS);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.FAILURE);
  }

  @Test
  void returnsValidWfmWithBusinessRolesAndSemantics() {
    var wfm = analyzer.analyzeToWfm(loginRequirement());

    assertThat(wfm.schemaVersion()).isEqualTo("1.0");
    assertThat(wfm.modelType()).isEqualTo("WORKFLOW_AST");
    assertThat(wfm.ast().nodes()).filteredOn((node) -> node.role() == WfmNodeRole.START).hasSize(1);
    assertThat(wfm.ast().nodes()).anyMatch((node) -> node.role() == WfmNodeRole.INPUT);
    assertThat(wfm.ast().nodes()).anyMatch((node) -> node.role() == WfmNodeRole.OUTPUT);
    assertThat(wfm.ast().nodes()).anyMatch((node) -> node.role() == WfmNodeRole.ERROR);
    assertThat(wfm.ast().transitions()).anyMatch((transition) -> transition.semantic() == WfmTransitionSemantic.NO);
    assertThat(wfm.ast().transitions()).anyMatch((transition) -> transition.semantic() == WfmTransitionSemantic.CANCEL);
    assertThat(wfm.ast().transitions()).anyMatch((transition) -> transition.semantic() == WfmTransitionSemantic.RETRY);
    assertThat(wfm.ast().nodes()).allSatisfy((node) -> {
      assertThat(node.data()).doesNotContainKeys("x", "y", "position", "color", "shape");
    });
  }

  @Test
  void parsesApprovalFlowWithApproveRejectAndPolicyFailureBranches() {
    Flowchart flowchart = analyzer.analyze(approvalRequirement());

    assertWellFormed(flowchart);
    assertThat(flowchart.nodes()).extracting("label").contains("Open Approval Request", "Mark Expense As Approved", "Return Request To Employee");
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.YES);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.NO);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.SUCCESS);
    assertThat(flowchart.edges()).anyMatch((edge) -> edge.type() == FlowEdgeType.FAILURE);
  }

  private void assertWellFormed(Flowchart flowchart) {
    assertThat(flowchart.nodes()).filteredOn((node) -> node.type() == FlowNodeType.START).hasSize(1);
    assertThat(flowchart.nodes()).anyMatch((node) -> node.type() == FlowNodeType.END);

    Set<String> nodeIds = new HashSet<>();
    flowchart.nodes().forEach((node) -> nodeIds.add(node.id()));
    assertThat(flowchart.edges()).allSatisfy((edge) -> {
      assertThat(nodeIds).contains(edge.source());
      assertThat(nodeIds).contains(edge.target());
    });

    String startId =
        flowchart.nodes().stream()
            .filter((node) -> node.type() == FlowNodeType.START)
            .findFirst()
            .orElseThrow()
            .id();
    Set<String> reachable = reachableNodeIds(startId, flowchart);
    assertThat(reachable).containsAll(nodeIds);
  }

  private Set<String> reachableNodeIds(String startId, Flowchart flowchart) {
    Map<String, List<String>> outgoing = new HashMap<>();
    flowchart.edges().forEach((edge) -> outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge.target()));

    Set<String> visited = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(startId);
    while (!queue.isEmpty()) {
      String nodeId = queue.removeFirst();
      if (!visited.add(nodeId)) {
        continue;
      }
      outgoing.getOrDefault(nodeId, List.of()).forEach(queue::addLast);
    }
    return visited;
  }

  private String loginRequirement() {
    return """
        Feature: User Login

        Start
        User enters username and password.
        System validates the input.

        If credentials are valid:
        - Continue to credential validation.
        - End.

        If authentication succeeds:
        - Authenticate user.
        - Generate access token.
        - Redirect to Dashboard.
        - End.

        If credentials are invalid:
        - Show "Invalid username or password".
        - Allow user to retry.

        If retry:
        - Return to Login screen.

        If user cancels login:
        - Return to Home page.
        - End.

        If system cannot connect to authentication server:
        - Show system error.
        - End.
        """;
  }

  private String registrationRequirement() {
    return """
        Feature: User Registration

        User opens the registration form.
        User enters profile information.
        System validates required fields.

        If account data is valid:
        - Create user account.
        - Send welcome email.

        If required information is missing:
        - Show validation error.
        - End.

        If account creation fails:
        - Show registration failure message.
        - End.
        """;
  }

  private String checkoutRequirement() {
    return """
        Feature: Checkout Order

        User opens cart.
        User reviews order summary.
        System calculates shipping and tax.

        If cart is valid:
        - Proceed to payment.

        If user cancels checkout:
        - Return to cart.

        If payment succeeds:
        - Create order and show receipt.

        If payment fails:
        - Show payment error.
        - Keep order pending.
        """;
  }

  private String approvalRequirement() {
    return """
        Feature: Expense Approval Workflow

        Employee submits expense request.
        Manager reviews request details.
        System checks policy rules.

        If manager approves request:
        - Mark expense as approved.
        - Send approval notification.

        If manager rejects request:
        - Return request to employee.

        If policy validation succeeds:
        - Send approval notification.

        If policy validation fails:
        - Show policy violation error.
        """;
  }
}
