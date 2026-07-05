package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reqpilot.ai.AiErrorType;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.config.AiProperties;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.WfmJson;
import com.reqpilot.wfm.WfmAst;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmExtensions;
import com.reqpilot.wfm.WfmNode;
import com.reqpilot.wfm.WfmNodeRole;
import com.reqpilot.wfm.WfmNormalizer;
import com.reqpilot.wfm.WfmToFlowchartMapper;
import com.reqpilot.wfm.WfmToMermaidGenerator;
import com.reqpilot.wfm.WfmTransition;
import com.reqpilot.wfm.WfmTransitionSemantic;
import com.reqpilot.wfm.WfmValidator;
import com.reqpilot.wfm.WfmWorkflow;
import com.reqpilot.wfmclient.WfmGenerationClient;
import com.reqpilot.wfmclient.WfmServiceGenerateRequest;
import com.reqpilot.wfmclient.WfmServiceGenerateResponse;
import com.reqpilot.wfmclient.WfmServiceMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRequirementAnalyzerTest {

  @Test
  void analyzeRequirementCallsPythonWfmClientAndReturnsValidatedWfm() {
    WfmDocument responseWfm = validWfmWithMissingOptionalFields();
    StubWfmGenerationClient client =
        new StubWfmGenerationClient(
            new WfmServiceGenerateResponse(
                WfmJson.from(responseWfm),
                new WfmToFlowchartMapper(new WfmToMermaidGenerator()).toFlowchart(responseWfm),
                new WfmServiceMetadata(
                    "deepseek/deepseek-chat",
                    "wfm-v1-python-001",
                    null,
                    "1.0",
                    null,
                    null,
                    "PASSED",
                    null,
                    "PASSED",
                    null,
                    List.of("python warning"))));
    AiRequirementAnalyzer analyzer = analyzer(client);

    RequirementAnalysis analysis = analyzer.analyzeRequirement(requirement());

    assertThat(client.calls).isEqualTo(1);
    assertThat(client.request.requirement()).isEqualTo(requirement());
    assertThat(client.request.options().wfmVersion()).isEqualTo("1.0");
    assertThat(client.request.options().model()).isEqualTo("deepseek/deepseek-chat");
    assertThat(client.request.options().temperature()).isEqualTo(0.2);
    assertThat(analysis.wfm().extensions()).isNotNull();
    assertThat(analysis.wfm().ast().nodes()).extracting(WfmNode::kind).containsExactly("START", "END");
    assertThat(analysis.metadata().source()).isEqualTo("AI");
    assertThat(analysis.metadata().provider()).isEqualTo("OPENROUTER");
    assertThat(analysis.metadata().model()).isEqualTo("deepseek/deepseek-chat");
    assertThat(analysis.metadata().promptVersion()).isEqualTo("wfm-v1-python-001");
    assertThat(analysis.metadata().fallbackUsed()).isFalse();
    assertThat(analysis.metadata().warnings()).contains("python warning");
  }

  @Test
  void pythonServiceFailurePropagatesWithoutRuleBasedFallback() {
    StubWfmGenerationClient client =
        new StubWfmGenerationClient(
            new AiProviderException(
                AiErrorType.PROVIDER_UNAVAILABLE,
                "PYTHON_WFM_SERVICE",
                "Python WFM service is unavailable"));
    AiRequirementAnalyzer analyzer = analyzer(client);

    assertThatThrownBy(() -> analyzer.analyzeRequirement(requirement()))
        .isInstanceOf(AiProviderException.class)
        .satisfies(
            (exception) ->
                assertThat(((AiProviderException) exception).errorType())
                    .isEqualTo(AiErrorType.PROVIDER_UNAVAILABLE));
    assertThat(client.calls).isEqualTo(1);
  }

  private AiRequirementAnalyzer analyzer(StubWfmGenerationClient client) {
    return new AiRequirementAnalyzer(
        aiProperties(),
        client,
        new WfmNormalizer(),
        new WfmValidator(),
        new WfmToFlowchartMapper(new WfmToMermaidGenerator()));
  }

  private AiProperties aiProperties() {
    return new AiProperties(
        "openrouter",
        "deepseek/deepseek-chat",
        "",
        60000,
        4096,
        0.2,
        true,
        new AiProperties.Cache(true, 30),
        "requirement-to-wfm-v1",
        new AiProperties.OpenRouter(
            "https://openrouter.ai/api/v1",
            "test-key",
            "deepseek/deepseek-chat",
            List.of("qwen/qwen3-32b:nitro", "deepseek/deepseek-chat-v3-0324"),
            0.2,
            4096,
            60));
  }

  private String requirement() {
    return "Feature: Delete Product\nStart\nIf user confirms, delete product\nEnd";
  }

  private WfmDocument validWfmWithMissingOptionalFields() {
    return new WfmDocument(
        "1.0",
        "WORKFLOW_AST",
        workflow(),
        null,
        new WfmAst(
            null,
            null,
            List.of(node("N1", WfmNodeRole.START, null, "Start"), node("N2", WfmNodeRole.END, null, "End")),
            List.of(transition("T1", "N1", "N2", WfmTransitionSemantic.DEFAULT)),
            null));
  }

  private WfmWorkflow workflow() {
    return new WfmWorkflow("delete-product", "Delete Product", null, "en", null, null);
  }

  private WfmNode node(String id, WfmNodeRole role, String kind, String title) {
    return new WfmNode(id, role, kind, title, null, null, List.of(), null);
  }

  private WfmTransition transition(String id, String from, String to, WfmTransitionSemantic semantic) {
    return new WfmTransition(id, from, to, semantic, null, null, null, null);
  }

  private static final class StubWfmGenerationClient implements WfmGenerationClient {

    private final WfmServiceGenerateResponse response;
    private final RuntimeException exception;
    private WfmServiceGenerateRequest request;
    private int calls;

    private StubWfmGenerationClient(WfmServiceGenerateResponse response) {
      this.response = response;
      this.exception = null;
    }

    private StubWfmGenerationClient(RuntimeException exception) {
      this.response = null;
      this.exception = exception;
    }

    @Override
    public WfmServiceGenerateResponse generate(WfmServiceGenerateRequest request) {
      calls++;
      this.request = request;
      if (exception != null) {
        throw exception;
      }
      return response;
    }
  }
}
