package com.reqpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.config.AiProperties;
import com.reqpilot.wfm.WfmAst;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmExtensions;
import com.reqpilot.wfm.WfmNode;
import com.reqpilot.wfm.WfmNodeRole;
import com.reqpilot.wfm.WfmTransition;
import com.reqpilot.wfm.WfmTransitionSemantic;
import com.reqpilot.wfm.WfmWorkflow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiWfmComponentsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AiProperties aiProperties =
      new AiProperties(
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

  @Test
  void promptBuilderConstrainsAiToWfmJsonOnly() {
    String prompt = new RequirementToWfmPromptBuilder(aiProperties).build("Feature: Login");

    assertThat(prompt)
        .contains("Return only valid JSON")
        .contains("WFM is a Workflow AST, not a UI graph")
        .contains("Core node roles")
        .contains("Core transition semantics")
        .contains("Forbidden WFM fields")
        .contains("Prompt version: requirement-to-wfm-v1");
  }

  @Test
  void parserExtractsJsonFromCodeFence() throws JsonProcessingException {
    String response = "```json\n" + objectMapper.writeValueAsString(validWfm()) + "\n```";

    WfmDocument parsed = new AiWfmResponseParser(objectMapper).parse(response);

    assertThat(parsed.schemaVersion()).isEqualTo("1.0");
    assertThat(parsed.modelType()).isEqualTo("WORKFLOW_AST");
    assertThat(parsed.ast().nodes()).hasSize(2);
  }

  @Test
  void parserRejectsUiOnlyFieldsBeforeMapping() throws JsonProcessingException {
    WfmDocument invalid =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            workflow(),
            new WfmExtensions(List.of(), List.of()),
            new WfmAst(
                List.of(),
                List.of(),
                List.of(
                    new WfmNode(
                        "N1",
                        WfmNodeRole.START,
                        "START",
                        "Start",
                        null,
                        null,
                        List.of(),
                        Map.of("position", Map.of("x", 10))),
                    node("N2", WfmNodeRole.END, "End")),
                List.of(transition("T1", "N1", "N2", WfmTransitionSemantic.DEFAULT)),
                List.of()));

    String response = objectMapper.writeValueAsString(invalid);

    assertThatThrownBy(() -> new AiWfmResponseParser(objectMapper).parse(response))
        .isInstanceOf(AiWfmResponseParsingException.class)
        .hasMessageContaining("UI-only field");
  }

  @Test
  void cacheKeyChangesWhenPromptVersionChanges() {
    RequirementCacheKeyBuilder builder = new RequirementCacheKeyBuilder();

    RequirementCacheKey keyV1 =
        builder.build(" Feature: Login ", com.reqpilot.config.AnalyzerMode.AI, "openrouter", "deepseek", "v1");
    RequirementCacheKey keyV2 =
        builder.build("Feature: Login", com.reqpilot.config.AnalyzerMode.AI, "openrouter", "deepseek", "v2");

    assertThat(keyV1.normalizedRequirementHash()).isEqualTo(keyV2.normalizedRequirementHash());
    assertThat(keyV1.value()).isNotEqualTo(keyV2.value());
  }

  private WfmDocument validWfm() {
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
    return new WfmWorkflow("login", "Login", null, "en", null, null);
  }

  private WfmNode node(String id, WfmNodeRole role, String title) {
    return new WfmNode(id, role, role.name(), title, null, null, List.of(), null);
  }

  private WfmTransition transition(String id, String from, String to, WfmTransitionSemantic semantic) {
    return new WfmTransition(id, from, to, semantic, null, null, null, null);
  }
}
