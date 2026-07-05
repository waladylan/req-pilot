package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reqpilot.ai.AiErrorType;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.model.WfmGenerationResult;
import com.reqpilot.wfm.WfmAst;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmExtensions;
import com.reqpilot.wfm.WfmNode;
import com.reqpilot.wfm.WfmNodeRole;
import com.reqpilot.wfm.WfmTransition;
import com.reqpilot.wfm.WfmTransitionSemantic;
import com.reqpilot.wfm.WfmWorkflow;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurableWfmGeneratorTest {

  @Test
  void delegatesToPythonWorkflowEngine() {
    StubGenerator pythonGenerator = StubGenerator.success(workflowEngineResult("AI"));
    ConfigurableWfmGenerator generator = new ConfigurableWfmGenerator(pythonGenerator);

    WfmGenerationResult result = generator.generate("Feature: Login");

    assertThat(result.metadata().generationMode()).isEqualTo("AI");
    assertThat(result.metadata().wfmSource()).isEqualTo("python-ai-generator");
    assertThat(result.metadata().flowchartSource()).isEqualTo("python-flowchart-mapper");
    assertThat(result.flowchart().nodes()).hasSize(2);
    assertThat(pythonGenerator.calls).isEqualTo(1);
  }

  @Test
  void pythonFailurePropagatesWithoutSpringFallback() {
    StubGenerator pythonGenerator =
        StubGenerator.failure(
            new AiProviderException(
                AiErrorType.PROVIDER_UNAVAILABLE,
                "PYTHON_WFM_SERVICE",
                "Python workflow engine is unavailable"));
    ConfigurableWfmGenerator generator = new ConfigurableWfmGenerator(pythonGenerator);

    assertThatThrownBy(() -> generator.generate("Feature: Login")).isInstanceOf(AiProviderException.class);
    assertThat(pythonGenerator.calls).isEqualTo(1);
  }

  private static WfmGenerationResult workflowEngineResult(String mode) {
    return new WfmGenerationResult(
        minimalWfm("login"),
        minimalFlowchart(),
        GenerationMetadata.workflowEngine(
            mode,
            "OPENROUTER",
            "deepseek/deepseek-chat",
            "wfm-v1-python-001",
            "python-ai-generator",
            "python-flowchart-mapper",
            "PASSED",
            "PASSED",
            "PASSED",
            "PASSED",
            List.of(),
            10L));
  }

  private static WfmDocument minimalWfm(String id) {
    return new WfmDocument(
        "1.0",
        "WORKFLOW_AST",
        new WfmWorkflow(id, "Login", null, "en", null, null),
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

  private static Flowchart minimalFlowchart() {
    return new Flowchart(
        List.of(new FlowNode("N1", "Start", FlowNodeType.START), new FlowNode("N2", "End", FlowNodeType.END)),
        List.of(new FlowEdge("T1", "N1", "N2", null, FlowEdgeType.DEFAULT)),
        "flowchart LR\n");
  }

  private static final class StubGenerator implements WfmGenerator {

    private final RuntimeException exception;
    private final WfmGenerationResult result;
    private int calls;

    private StubGenerator(WfmGenerationResult result, RuntimeException exception) {
      this.result = result;
      this.exception = exception;
    }

    private static StubGenerator success(WfmGenerationResult result) {
      return new StubGenerator(result, null);
    }

    private static StubGenerator failure(RuntimeException exception) {
      return new StubGenerator(null, exception);
    }

    @Override
    public WfmGenerationResult generate(String requirement) {
      calls++;
      if (exception != null) {
        throw exception;
      }
      return result;
    }
  }
}
