package com.reqpilot.wfm;

import static org.assertj.core.api.Assertions.assertThat;

import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.GeneratedTestCase;
import com.reqpilot.service.RuleBasedRequirementAnalyzer;
import java.util.List;
import org.junit.jupiter.api.Test;

class WfmServicesTest {

  private final WfmValidator validator = new WfmValidator();
  private final WfmNormalizer normalizer = new WfmNormalizer();
  private final WfmToMermaidGenerator mermaidGenerator = new WfmToMermaidGenerator();
  private final WfmToFlowchartMapper flowchartMapper = new WfmToFlowchartMapper(mermaidGenerator);
  private final WfmPathExtractor pathExtractor = new WfmPathExtractor();
  private final WfmToTestCaseGenerator testCaseGenerator =
      new WfmToTestCaseGenerator(pathExtractor);

  @Test
  void validatorAcceptsValidMinimalWfm() {
    assertThat(validator.validate(minimalWfm()).valid()).isTrue();
  }

  @Test
  void validatorRejectsMissingStart() {
    WfmDocument invalid =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            null,
            new WfmAst(
                List.of(),
                List.of(),
                List.of(node("N2", WfmNodeRole.END, "End")),
                List.of(),
                List.of()));

    assertThat(validator.validate(invalid).errors()).anyMatch((error) -> error.code().equals("START_COUNT"));
  }

  @Test
  void validatorRejectsMissingEnd() {
    WfmDocument invalid =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            null,
            new WfmAst(
                List.of(),
                List.of(),
                List.of(node("N1", WfmNodeRole.START, "Start")),
                List.of(),
                List.of()));

    assertThat(validator.validate(invalid).errors()).anyMatch((error) -> error.code().equals("END_REQUIRED"));
  }

  @Test
  void validatorRejectsDuplicateNodeIdsAndInvalidTransitionSource() {
    WfmDocument invalid =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            null,
            new WfmAst(
                List.of(),
                List.of(),
                List.of(
                    node("N1", WfmNodeRole.START, "Start"),
                    node("N1", WfmNodeRole.END, "End")),
                List.of(transition("T1", "NX", "N1", WfmTransitionSemantic.DEFAULT)),
                List.of()));

    assertThat(validator.validate(invalid).errors())
        .extracting(WfmValidationError::code)
        .contains("DUPLICATE_NODE_ID", "INVALID_FROM");
  }

  @Test
  void validatorRejectsInvalidTransitionTarget() {
    WfmDocument invalid =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            null,
            new WfmAst(
                List.of(),
                List.of(),
                List.of(node("N1", WfmNodeRole.START, "Start"), node("N2", WfmNodeRole.END, "End")),
                List.of(transition("T1", "N1", "NX", WfmTransitionSemantic.DEFAULT)),
                List.of()));

    assertThat(validator.validate(invalid).errors())
        .extracting(WfmValidationError::code)
        .contains("INVALID_TO");
  }

  @Test
  void validatorRejectsStartIncomingAndEndOutgoingTransitions() {
    WfmDocument invalid =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            null,
            new WfmAst(
                List.of(),
                List.of(),
                List.of(node("N1", WfmNodeRole.START, "Start"), node("N2", WfmNodeRole.END, "End")),
                List.of(transition("T1", "N2", "N1", WfmTransitionSemantic.DEFAULT)),
                List.of()));

    assertThat(validator.validate(invalid).errors())
        .extracting(WfmValidationError::code)
        .contains("START_INCOMING", "END_OUTGOING");
  }

  @Test
  void validatorReturnsWarningsSeparatelyFromErrors() {
    WfmDocument warningOnly =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            null,
            new WfmAst(
                List.of(),
                List.of(),
                List.of(
                    node("N1", WfmNodeRole.START, "Start"),
                    node("N2", WfmNodeRole.DECISION, "User is eligible?"),
                    node("N3", WfmNodeRole.END, "End")),
                List.of(
                    transition("T1", "N1", "N2", WfmTransitionSemantic.DEFAULT),
                    transition("T2", "N2", "N3", WfmTransitionSemantic.YES)),
                List.of()));

    WfmValidationResult result = validator.validate(warningOnly);

    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
    assertThat(result.warnings())
        .extracting(WfmValidationError::code)
        .contains("DECISION_BRANCH_COUNT");
  }

  @Test
  void validatorRejectsUiOnlyFieldsInBusinessData() {
    WfmNode invalidNode =
        new WfmNode(
            "N1",
            WfmNodeRole.START,
            "START",
            "Start",
            null,
            null,
            List.of(),
            java.util.Map.of("position", java.util.Map.of("x", 10)));
    WfmDocument invalid =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            null,
            new WfmAst(
                List.of(),
                List.of(),
                List.of(invalidNode, node("N2", WfmNodeRole.END, "End")),
                List.of(transition("T1", "N1", "N2", WfmTransitionSemantic.DEFAULT)),
                List.of()));

    assertThat(validator.validate(invalid).errors()).anyMatch((error) -> error.code().equals("UI_FIELD"));
  }

  @Test
  void normalizerFillsOptionalArraysDefaultsAndIds() {
    WfmDocument raw =
        new WfmDocument(
            null,
            null,
            new WfmWorkflow(null, "Minimal Flow", null, null, null, null),
            null,
            new WfmAst(
                List.of(new WfmActor(null, "Customer", null)),
                List.of(new WfmVariable(null, "email", null, null, null, null)),
                List.of(
                    new WfmNode(null, WfmNodeRole.START, null, "Start", null, null, null, null),
                    new WfmNode(null, WfmNodeRole.END, null, "End", null, null, null, null)),
                List.of(new WfmTransition(null, null, null, null, null, null, null, null)),
                null));

    WfmDocument normalized = normalizer.normalize(raw);

    assertThat(normalized.schemaVersion()).isEqualTo("1.0");
    assertThat(normalized.modelType()).isEqualTo("WORKFLOW_AST");
    assertThat(normalized.workflow().language()).isEqualTo("unknown");
    assertThat(normalized.ast().actors()).extracting(WfmActor::id).containsExactly("A1");
    assertThat(normalized.ast().actors()).extracting(WfmActor::type).containsExactly(WfmActorType.USER);
    assertThat(normalized.ast().variables()).extracting(WfmVariable::id).containsExactly("V1");
    assertThat(normalized.ast().variables()).extracting(WfmVariable::type).containsExactly(WfmVariableType.UNKNOWN);
    assertThat(normalized.ast().nodes()).extracting(WfmNode::id).containsExactly("N1", "N2");
    assertThat(normalized.ast().nodes()).extracting(WfmNode::kind).containsExactly("START", "END");
    assertThat(normalized.ast().transitions().getFirst().id()).isEqualTo("T1");
    assertThat(normalized.ast().transitions().getFirst().semantic()).isEqualTo(WfmTransitionSemantic.DEFAULT);
  }

  @Test
  void flowchartMapperCreatesCompatibilityModel() {
    var flowchart = flowchartMapper.toFlowchart(minimalWfm());

    assertThat(flowchart.nodes()).extracting("type").containsExactly(FlowNodeType.START, FlowNodeType.END);
    assertThat(flowchart.edges()).extracting("type").containsExactly(FlowEdgeType.DEFAULT);
    assertThat(flowchart.mermaid()).contains("flowchart LR");
  }

  @Test
  void ruleBasedAnalyzerReturnsValidWfm() {
    RuleBasedRequirementAnalyzer analyzer = new RuleBasedRequirementAnalyzer();
    WfmDocument wfm = analyzer.analyzeToWfm(loginRequirement());

    assertThat(validator.validate(wfm).valid()).isTrue();
    assertThat(wfm.ast().transitions())
        .extracting(WfmTransition::semantic)
        .contains(
            WfmTransitionSemantic.YES,
            WfmTransitionSemantic.NO,
            WfmTransitionSemantic.SUCCESS,
            WfmTransitionSemantic.FAILURE,
            WfmTransitionSemantic.CANCEL,
            WfmTransitionSemantic.RETRY);
  }

  @Test
  void testCaseGeneratorCreatesCasesFromWfmPaths() {
    RuleBasedRequirementAnalyzer analyzer = new RuleBasedRequirementAnalyzer();
    WfmDocument wfm = analyzer.analyzeToWfm(loginRequirement());
    List<GeneratedTestCase> testCases = testCaseGenerator.generate(loginRequirement(), wfm);

    assertThat(testCases).hasSizeGreaterThanOrEqualTo(6);
    assertThat(testCases).extracting(GeneratedTestCase::title)
        .contains(
            "Login successfully",
            "Show invalid credentials message",
            "Cancel login",
            "Retry login after invalid credentials",
            "Show system error");
    assertThat(TestCaseQualityValidator.validate(testCases, wfm)).isEmpty();
  }

  @Test
  void pathExtractorPreservesRetryLoopPathWithoutInfiniteTraversal() {
    WfmDocument wfm =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            new WfmExtensions(List.of(), List.of()),
            new WfmAst(
                List.of(),
                List.of(),
                List.of(
                    node("N1", WfmNodeRole.START, "Start"),
                    node("N2", WfmNodeRole.INPUT, "Enter credentials"),
                    node("N3", WfmNodeRole.DECISION, "Credentials valid?"),
                    node("N4", WfmNodeRole.OUTPUT, "Redirect to Dashboard"),
                    node("N5", WfmNodeRole.END, "End")),
                List.of(
                    transition("T1", "N1", "N2", WfmTransitionSemantic.DEFAULT),
                    transition("T2", "N2", "N3", WfmTransitionSemantic.DEFAULT),
                    transition("T3", "N3", "N4", WfmTransitionSemantic.SUCCESS),
                    transition("T4", "N4", "N5", WfmTransitionSemantic.DEFAULT),
                    transition("T5", "N3", "N2", WfmTransitionSemantic.RETRY)),
                List.of()));

    List<WfmPath> paths = pathExtractor.extract(wfm);

    assertThat(paths).anyMatch(WfmPath::happyPath);
    assertThat(paths).anyMatch(WfmPath::retryPath);
    assertThat(paths).anyMatch(WfmPath::containsLoop);
  }

  private WfmDocument minimalWfm() {
    return new WfmDocument(
        "1.0",
        "WORKFLOW_AST",
        workflow(),
        new WfmExtensions(List.of(), List.of()),
        new WfmAst(
            List.of(),
            List.of(),
            List.of(node("N1", WfmNodeRole.START, "Start"), node("N2", WfmNodeRole.END, "End")),
            List.of(transition("T1", "N1", "N2", WfmTransitionSemantic.DEFAULT)),
            List.of()));
  }

  private WfmWorkflow workflow() {
    return new WfmWorkflow("minimal-flow", "Minimal Flow", null, "en", null, null);
  }

  private WfmNode node(String id, WfmNodeRole role, String title) {
    return new WfmNode(id, role, role.name(), title, null, null, List.of(), null);
  }

  private WfmTransition transition(String id, String from, String to, WfmTransitionSemantic semantic) {
    return new WfmTransition(id, from, to, semantic, null, null, null, null);
  }

  private String loginRequirement() {
    return """
        Feature: User Login

        User enters username and password.
        System validates the input.

        If credentials are valid:
        - Authenticate user.
        - Generate access token.
        - Redirect to Dashboard.

        If credentials are invalid:
        - Show "Invalid username or password".
        - Allow user to retry.

        If retry:
        - Return to Login screen.

        If user cancels login:
        - Return to Home page.

        If authentication succeeds:
        - Show login success message.

        If system cannot connect to authentication server:
        - Show system error.
        """;
  }
}
