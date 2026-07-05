package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GeneratedFlow;
import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.model.WfmGenerationResult;
import com.reqpilot.wfm.WfmAst;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmExtensions;
import com.reqpilot.wfm.WfmNode;
import com.reqpilot.wfm.WfmNodeRole;
import com.reqpilot.wfm.WfmNormalizer;
import com.reqpilot.wfm.WfmToFlowchartMapper;
import com.reqpilot.wfm.WfmToMermaidGenerator;
import com.reqpilot.wfm.WfmToTestCaseGenerator;
import com.reqpilot.wfm.WfmTransition;
import com.reqpilot.wfm.WfmTransitionSemantic;
import com.reqpilot.wfm.WfmValidationException;
import com.reqpilot.wfm.WfmValidator;
import com.reqpilot.wfm.WfmWorkflow;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequirementGenerationServiceTest {

  @Test
  void generateFlowUsesWfmAsSourceAndKeepsFlowchartCompatibility() {
    WfmDocument wfm = minimalWfm();
    RequirementGenerationService service =
        new RequirementGenerationService(
            new StubAnalyzer(wfm),
            new RuleBasedTestCaseGenerator(),
            new WfmToFlowchartMapper(new WfmToMermaidGenerator()),
            new WfmToTestCaseGenerator(),
            new WfmNormalizer(),
            new WfmValidator(),
            null,
            new ObjectMapper());

    GeneratedFlow generated = service.generateFlow("Feature: Login");

    assertThat(generated.wfm().path("schemaVersion").asText()).isEqualTo("1.0");
    assertThat(generated.wfmDocument().workflow().id()).isEqualTo("login");
    assertThat(generated.wfmDocument().ast().actors()).isEmpty();
    assertThat(generated.wfmDocument().ast().nodes()).extracting(WfmNode::id).containsExactly("N1", "N2");
    assertThat(generated.flowchart().nodes()).hasSize(2);
    assertThat(generated.flowchart().edges()).hasSize(1);
    assertThat(generated.flowchart().mermaid()).contains("flowchart LR");
    assertThat(generated.metadata().source()).isEqualTo("AI");
    assertThat(generated.metadata().generationMode()).isEqualTo("AI");
    assertThat(generated.metadata().wfmSource()).isEqualTo("python-ai-generator");
    assertThat(generated.metadata().flowchartSource()).isEqualTo("python-flowchart-mapper");
    assertThat(generated.metadata().validationStatus()).isEqualTo("PASSED");
    assertThat(generated.metadata().normalizationStatus()).isEqualTo("PASSED");
    assertThat(generated.metadata().provider()).isEqualTo("OPENROUTER");
    assertThat(generated.metadata().fallbackUsed()).isFalse();
    assertThat(generated.metadata().validationErrors()).isEmpty();
  }

  @Test
  void generateFlowRejectsInvalidWfmBeforeMappingFlowchart() {
    CountingFlowchartMapper flowchartMapper = new CountingFlowchartMapper();
    RequirementGenerationService service =
        new RequirementGenerationService(
            new StubAnalyzer(invalidWfmWithoutEnd()),
            new RuleBasedTestCaseGenerator(),
            flowchartMapper,
            new WfmToTestCaseGenerator(),
            new WfmNormalizer(),
            new WfmValidator(),
            null,
            new ObjectMapper());

    assertThatThrownBy(() -> service.generateFlow("Feature: Broken"))
        .isInstanceOf(WfmValidationException.class);
    assertThat(flowchartMapper.calls).isZero();
  }

  private WfmDocument minimalWfm() {
    return new WfmDocument(
        "1.0",
        "WORKFLOW_AST",
        new WfmWorkflow("login", "Login", null, "en", null, null),
        new WfmExtensions(List.of(), List.of()),
        new WfmAst(
            List.of(),
            List.of(),
            List.of(
                new WfmNode("N1", WfmNodeRole.START, "START", "Start", null, null, List.of(), null),
                new WfmNode("N2", WfmNodeRole.END, "END", "End", null, null, List.of(), null)),
            List.of(new WfmTransition("T1", "N1", "N2", WfmTransitionSemantic.DEFAULT, null, null, null, null)),
            List.of()));
  }

  private WfmDocument invalidWfmWithoutEnd() {
    return new WfmDocument(
        "1.0",
        "WORKFLOW_AST",
        new WfmWorkflow("broken", "Broken", null, "en", null, null),
        new WfmExtensions(List.of(), List.of()),
        new WfmAst(
            List.of(),
            List.of(),
            List.of(new WfmNode("N1", WfmNodeRole.START, "START", "Start", null, null, List.of(), null)),
            List.of(),
            List.of()));
  }

  private static final class CountingFlowchartMapper extends WfmToFlowchartMapper {

    private int calls;

    private CountingFlowchartMapper() {
      super(new WfmToMermaidGenerator());
    }

    @Override
    public Flowchart toFlowchart(WfmDocument wfm) {
      calls++;
      return super.toFlowchart(wfm);
    }
  }

  private static final class StubAnalyzer implements WfmGenerator {

    private final WfmDocument wfm;

    private StubAnalyzer(WfmDocument wfm) {
      this.wfm = wfm;
    }

    @Override
    public WfmGenerationResult generate(String requirement) {
      return new WfmGenerationResult(
          wfm,
          minimalFlowchart(),
          GenerationMetadata.workflowEngine(
              "AI",
              "OPENROUTER",
              "deepseek/deepseek-chat",
              "requirement-to-wfm-v1",
            "python-ai-generator",
            "python-flowchart-mapper",
            "PASSED",
            "PASSED",
            "PASSED",
            "PASSED",
              List.of(),
              10L));
    }
  }

  private static Flowchart minimalFlowchart() {
    return new Flowchart(
        List.of(new FlowNode("N1", "Start", FlowNodeType.START), new FlowNode("N2", "End", FlowNodeType.END)),
        List.of(new FlowEdge("T1", "N1", "N2", null, FlowEdgeType.DEFAULT)),
        "flowchart LR\n");
  }
}
