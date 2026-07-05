package com.reqpilot.wfm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.model.GeneratedFlow;
import com.reqpilot.model.GeneratedTestCases;
import com.reqpilot.service.RequirementGenerationService;
import com.reqpilot.service.RuleBasedRequirementAnalyzer;
import com.reqpilot.service.RuleBasedTestCaseGenerator;
import com.reqpilot.service.RuleBasedWfmGenerator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WfmTestCasePipelineTest {

  private final WfmNormalizer normalizer = new WfmNormalizer();
  private final WfmValidator validator = new WfmValidator();
  private final WfmToMermaidGenerator mermaidGenerator = new WfmToMermaidGenerator();
  private final WfmToFlowchartMapper flowchartMapper = new WfmToFlowchartMapper(mermaidGenerator);
  private final WfmPathExtractor pathExtractor = new WfmPathExtractor();
  private final RuleBasedRequirementAnalyzer analyzer =
      new RuleBasedRequirementAnalyzer(normalizer, validator, flowchartMapper);
  private final WfmToTestCaseGenerator testCaseGenerator = new WfmToTestCaseGenerator(pathExtractor);
  private final RequirementGenerationService generationService =
      new RequirementGenerationService(
          new RuleBasedWfmGenerator(analyzer, flowchartMapper),
          new RuleBasedTestCaseGenerator(),
          flowchartMapper,
          testCaseGenerator,
          normalizer,
          validator,
          null,
          new ObjectMapper());

  @ParameterizedTest(name = "{0}")
  @MethodSource("samples")
  void generateFlowThenGenerateTestCasesFromWfm(Sample sample) {
    GeneratedFlow generatedFlow = generationService.generateFlow(sample.requirement());
    WfmDocument wfm = generatedFlow.wfmDocument();

    assertThat(wfm).isNotNull();
    assertThat(generatedFlow.flowchart()).isNotNull();
    assertThat(wfm.schemaVersion()).isEqualTo("1.0");
    assertThat(wfm.modelType()).isEqualTo("WORKFLOW_AST");
    assertThat(wfm.ast().nodes()).isNotEmpty();
    assertThat(wfm.ast().transitions()).isNotEmpty();
    assertThat(wfm.ast().nodes().stream().filter((node) -> node.role() == WfmNodeRole.START)).hasSize(1);
    assertThat(wfm.ast().nodes().stream().filter((node) -> node.role() == WfmNodeRole.END)).isNotEmpty();
    assertThat(wfm.ast().nodes()).anyMatch((node) -> node.role() == WfmNodeRole.DECISION);
    assertThat(validator.validate(wfm).valid()).isTrue();
    assertThat(containsUiOnlyFields(wfm)).isFalse();

    assertThat(generatedFlow.flowchart().nodes()).isNotEmpty();
    assertThat(generatedFlow.flowchart().edges()).isNotEmpty();
    assertThat(generatedFlow.flowchart().mermaid()).contains("flowchart LR");
    assertThat(wfm.ast().transitions())
        .extracting(WfmTransition::semantic)
        .containsAll(sample.expectedSemantics());

    GeneratedTestCases generatedTestCases =
        generationService.generateTestCases(null, null, generatedFlow.wfmDocument());

    assertThat(generatedTestCases.source()).isEqualTo("WFM");
    assertThat(generatedTestCases.workflowId()).isEqualTo(wfm.workflow().id());
    assertThat(generatedTestCases.pathCount()).isGreaterThanOrEqualTo(sample.minimumTestCases());
    assertThat(generatedTestCases.testCases()).hasSizeGreaterThanOrEqualTo(sample.minimumTestCases());
    assertThat(TestCaseQualityValidator.validate(generatedTestCases.testCases(), wfm)).isEmpty();

    String combinedTitles =
        generatedTestCases.testCases().stream()
            .map((testCase) -> testCase.title().toLowerCase(Locale.ROOT))
            .reduce("", (left, right) -> left + " " + right);
    sample.expectedTitleFragments().forEach((fragment) -> assertThat(combinedTitles).contains(fragment));
  }

  private boolean containsUiOnlyFields(WfmDocument wfm) {
    String serialized = new ObjectMapper().valueToTree(wfm).toString();
    return serialized.contains("\"position\"")
        || serialized.contains("\"sourceHandle\"")
        || serialized.contains("\"targetHandle\"")
        || serialized.contains("\"reactFlowType\"")
        || serialized.contains("\"edgeLabel\"");
  }

  private static Stream<Sample> samples() {
    return Stream.of(
        new Sample(
            "Delete Product",
            """
            Feature: Delete Product

            Start.

            User opens product detail.

            User clicks Delete.

            System shows confirmation dialog.

            If user confirms:
            - Delete product.
            - Show success message.
            - End.

            If user cancels:
            - Keep product unchanged.
            - End.
            """,
            2,
            Set.of(WfmTransitionSemantic.YES, WfmTransitionSemantic.CANCEL),
            List.of("delete product successfully", "cancel delete product")),
        new Sample(
            "User Login",
            """
            Feature: User Login

            Start.

            User enters username and password.

            System checks required information.

            If required information is missing:
            - Show validation error.
            - End.

            If required information is provided:
            - Validate credentials.

            If credentials are valid:
            - Authenticate user.
            - Redirect to Dashboard.
            - End.

            If credentials are invalid:
            - Show invalid username or password.
            - Ask user to retry.

            If user retries:
            - Return to Login screen.

            If user cancels:
            - End.
            """,
            5,
            Set.of(
                WfmTransitionSemantic.YES,
                WfmTransitionSemantic.NO,
                WfmTransitionSemantic.SUCCESS,
                WfmTransitionSemantic.FAILURE,
                WfmTransitionSemantic.RETRY,
                WfmTransitionSemantic.CANCEL),
            List.of(
                "login successfully",
                "validation error",
                "invalid credentials",
                "retry login",
                "cancel login")),
        new Sample(
            "Checkout Order",
            """
            Feature: Checkout Order

            Start.

            User reviews cart.

            User enters shipping address.

            System validates shipping address.

            If shipping address is invalid:
            - Show address validation error.
            - End.

            If shipping address is valid:
            - User selects payment method.
            - System processes payment.

            If payment succeeds:
            - Create order.
            - Show order confirmation.
            - End.

            If payment fails:
            - Show payment failure message.
            - Allow user to retry payment.

            If user retries payment:
            - Return to payment method step.

            If user cancels checkout:
            - Keep cart unchanged.
            - End.
            """,
            5,
            Set.of(
                WfmTransitionSemantic.YES,
                WfmTransitionSemantic.NO,
                WfmTransitionSemantic.SUCCESS,
                WfmTransitionSemantic.FAILURE,
                WfmTransitionSemantic.RETRY,
                WfmTransitionSemantic.CANCEL),
            List.of(
                "checkout order successfully",
                "validation error",
                "payment failure",
                "retry payment",
                "cancel checkout")),
        new Sample(
            "Approval Workflow",
            """
            Feature: Expense Approval

            Start.

            Employee submits expense request.

            System validates required documents.

            If documents are missing:
            - Show missing documents message.
            - End.

            If documents are complete:
            - Manager reviews request.

            If manager approves:
            - Mark request as approved.
            - Notify employee.
            - End.

            If manager rejects:
            - Mark request as rejected.
            - Notify employee.
            - End.
            """,
            3,
            Set.of(
                WfmTransitionSemantic.YES,
                WfmTransitionSemantic.NO,
                WfmTransitionSemantic.SUCCESS,
                WfmTransitionSemantic.FAILURE),
            List.of("approve request", "reject approval request", "missing documents")),
        new Sample(
            "Password Reset",
            """
            Feature: Password Reset

            Start.

            User enters email address.

            System checks whether email exists.

            If email does not exist:
            - Show email not found message.
            - End.

            If email exists:
            - Send reset link.

            If email service succeeds:
            - Show reset link sent message.
            - End.

            If email service fails:
            - Show system error.
            - End.
            """,
            3,
            Set.of(
                WfmTransitionSemantic.YES,
                WfmTransitionSemantic.NO,
                WfmTransitionSemantic.SUCCESS,
                WfmTransitionSemantic.FAILURE),
            List.of("password reset", "email not found", "system error")));
  }

  private record Sample(
      String name,
      String requirement,
      int minimumTestCases,
      Set<WfmTransitionSemantic> expectedSemantics,
      List<String> expectedTitleFragments) {

    @Override
    public String toString() {
      return name;
    }
  }
}
