package com.reqpilot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.ai.AiErrorType;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.config.AiProperties;
import com.reqpilot.config.WfmGenerationMode;
import com.reqpilot.config.WfmGenerationProperties;
import com.reqpilot.dto.RequirementMapper;
import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import com.reqpilot.service.ConfigurableWfmGenerator;
import com.reqpilot.service.PythonAiWfmGenerator;
import com.reqpilot.service.RequirementGenerationService;
import com.reqpilot.service.RuleBasedTestCaseGenerator;
import com.reqpilot.service.WfmGenerator;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmNormalizer;
import com.reqpilot.wfm.WfmToFlowchartMapper;
import com.reqpilot.wfm.WfmToMermaidGenerator;
import com.reqpilot.wfm.WfmToTestCaseGenerator;
import com.reqpilot.wfm.WfmValidator;
import com.reqpilot.wfmclient.WfmGenerationClient;
import com.reqpilot.wfmclient.WfmServiceGenerateRequest;
import com.reqpilot.wfmclient.WfmServiceGenerateResponse;
import com.reqpilot.wfmclient.WfmServiceMetadata;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RequirementPythonWfmIntegrationTest {

  @Test
  void publicGenerateFlowEndpointUsesPythonWfmClientAndKeepsResponseShape() throws Exception {
    StubWfmGenerationClient wfmClient = StubWfmGenerationClient.success();
    MockMvc mockMvc = mockMvcFor(wfmClient);

    mockMvc
        .perform(
            post("/api/requirements/generate-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"Feature: Leave Request Approval\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wfm").exists())
        .andExpect(jsonPath("$.flowchart").exists())
        .andExpect(jsonPath("$.metadata").exists())
        .andExpect(jsonPath("$.wfm.wfmVersion").value("2.0"))
        .andExpect(jsonPath("$.wfm.workflowId").value("leave_request_approval"))
        .andExpect(jsonPath("$.flowchart.nodes").isArray())
        .andExpect(jsonPath("$.flowchart.edges").isArray())
        .andExpect(jsonPath("$.flowchart.nodes[0].id").exists())
        .andExpect(jsonPath("$.flowchart.nodes[0].label").exists())
        .andExpect(jsonPath("$.flowchart.nodes[0].type").exists())
        .andExpect(jsonPath("$.flowchart.edges[2].id").value("T3"))
        .andExpect(jsonPath("$.flowchart.edges[2].source").value("N3"))
        .andExpect(jsonPath("$.flowchart.edges[2].target").value("N4"))
        .andExpect(jsonPath("$.flowchart.edges[2].label").value("Manager approves"))
        .andExpect(jsonPath("$.flowchart.edges[2].type").value("YES"))
        .andExpect(jsonPath("$.metadata.source").value("AI"))
        .andExpect(jsonPath("$.metadata.generationMode").value("AI"))
        .andExpect(jsonPath("$.metadata.wfmSource").value("python-wfm-v2-ai-generator"))
        .andExpect(jsonPath("$.metadata.flowchartSource").value("python-wfm-v2-flowchart-mapper"))
        .andExpect(jsonPath("$.metadata.validationStatus").value("PASSED"))
        .andExpect(jsonPath("$.metadata.canonicalizationStatus").value("PASSED"))
        .andExpect(jsonPath("$.metadata.mappingStatus").value("PASSED"))
        .andExpect(jsonPath("$.metadata.model").value("deepseek/deepseek-chat"))
        .andExpect(jsonPath("$.metadata.promptVersion").value("wfm-v2-python-001"));

    assertThat(wfmClient.calls).isEqualTo(1);
    assertThat(wfmClient.request.requirement()).isEqualTo("Feature: Leave Request Approval");
    assertThat(wfmClient.request.options().generationMode()).isEqualTo("AI");
    assertThat(wfmClient.request.options().wfmVersion()).isEqualTo("2.0");
  }

  @Test
  void publicGenerateFlowEndpointSendsRuleBasedModeToPythonWorkflowEngine() throws Exception {
    StubWfmGenerationClient wfmClient =
        StubWfmGenerationClient.success("RULE_BASED", "python-wfm-v2-rule-based-generator");
    MockMvc mockMvc = mockMvcFor(wfmClient, new WfmGenerationProperties(WfmGenerationMode.RULE_BASED, false));

    mockMvc
        .perform(
            post("/api/requirements/generate-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"Feature: Delete Product\\nStart\\nUser deletes product\\nEnd\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wfm").exists())
        .andExpect(jsonPath("$.flowchart").exists())
        .andExpect(jsonPath("$.metadata.source").value("RULE_BASED"))
        .andExpect(jsonPath("$.metadata.generationMode").value("RULE_BASED"))
        .andExpect(jsonPath("$.metadata.wfmSource").value("python-wfm-v2-rule-based-generator"))
        .andExpect(jsonPath("$.metadata.flowchartSource").value("python-wfm-v2-flowchart-mapper"))
        .andExpect(jsonPath("$.metadata.validationStatus").value("PASSED"))
        .andExpect(jsonPath("$.metadata.canonicalizationStatus").value("PASSED"))
        .andExpect(jsonPath("$.metadata.mappingStatus").value("PASSED"));

    assertThat(wfmClient.calls).isEqualTo(1);
    assertThat(wfmClient.request.options().generationMode()).isEqualTo("RULE_BASED");
    assertThat(wfmClient.request.options().wfmVersion()).isEqualTo("2.0");
  }

  @Test
  void publicGenerateFlowEndpointReturnsSafeErrorWhenPythonServiceUnavailable() throws Exception {
    MockMvc mockMvc =
        mockMvcFor(
            StubWfmGenerationClient.failure(
                new AiProviderException(
                    AiErrorType.PROVIDER_UNAVAILABLE,
                    "PYTHON_WFM_SERVICE",
                    "Python WFM service is unavailable")));

    mockMvc
        .perform(
            post("/api/requirements/generate-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"Feature: Leave Request Approval\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("AI provider error"))
        .andExpect(jsonPath("$.details[0]").value("AI_PROVIDER_UNAVAILABLE"))
        .andExpect(jsonPath("$.wfm").doesNotExist())
        .andExpect(content().string(not(containsString("Exception"))))
        .andExpect(content().string(not(containsString("OPENROUTER_API_KEY"))))
        .andExpect(content().string(not(containsString("Requirement:"))));
  }

  @Test
  void publicGenerateFlowEndpointDoesNotFallbackInSpringWhenPythonFails() throws Exception {
    StubWfmGenerationClient wfmClient =
        StubWfmGenerationClient.failure(
            new AiProviderException(
                AiErrorType.PROVIDER_UNAVAILABLE,
                "PYTHON_WFM_SERVICE",
                "Python WFM service is unavailable"));
    MockMvc mockMvc = mockMvcFor(wfmClient, new WfmGenerationProperties(WfmGenerationMode.AI, true));

    mockMvc
        .perform(
            post("/api/requirements/generate-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"Feature: Delete Product\\nStart\\nUser deletes product\\nEnd\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("AI provider error"))
        .andExpect(jsonPath("$.details[0]").value("AI_PROVIDER_UNAVAILABLE"))
        .andExpect(jsonPath("$.wfm").doesNotExist());

    assertThat(wfmClient.calls).isEqualTo(1);
  }

  @Test
  void publicGenerateFlowEndpointReturnsPythonFlowchartWithoutCallingJavaMapper() throws Exception {
    StubWfmGenerationClient wfmClient = StubWfmGenerationClient.success();
    MockMvc mockMvc = mockMvcFor(wfmClient, new WfmGenerationProperties(WfmGenerationMode.AI, false), throwingMapper());

    mockMvc
        .perform(
            post("/api/requirements/generate-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"Feature: Leave Request Approval\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flowchart.edges[2].label").value("Manager approves"))
        .andExpect(jsonPath("$.metadata.flowchartSource").value("python-wfm-v2-flowchart-mapper"));
  }

  @Test
  void publicGenerateFlowEndpointReturnsSafeErrorWhenPythonValidationFails() throws Exception {
    MockMvc mockMvc =
        mockMvcFor(
            StubWfmGenerationClient.failure(
                new AiProviderException(
                    AiErrorType.INVALID_RESPONSE,
                    "PYTHON_WFM_SERVICE",
                    "Generated WFM does not match WFM v1")));

    mockMvc
        .perform(
            post("/api/requirements/generate-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"Feature: Leave Request Approval\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("AI provider error"))
        .andExpect(jsonPath("$.details[0]").value("AI_INVALID_RESPONSE"))
        .andExpect(jsonPath("$.details[1]").value("Generated WFM does not match WFM v1"))
        .andExpect(content().string(not(containsString("Exception"))));
  }

  private MockMvc mockMvcFor(StubWfmGenerationClient wfmClient) {
    return mockMvcFor(wfmClient, new WfmGenerationProperties(WfmGenerationMode.AI, false));
  }

  private MockMvc mockMvcFor(StubWfmGenerationClient wfmClient, WfmGenerationProperties properties) {
    return mockMvcFor(wfmClient, properties, new WfmToFlowchartMapper(new WfmToMermaidGenerator()));
  }

  private MockMvc mockMvcFor(
      StubWfmGenerationClient wfmClient, WfmGenerationProperties properties, WfmToFlowchartMapper flowchartMapper) {
    WfmNormalizer normalizer = new WfmNormalizer();
    WfmValidator validator = new WfmValidator();
    WfmGenerator generator =
        new ConfigurableWfmGenerator(
            new PythonAiWfmGenerator(aiProperties(), properties, wfmClient));
    RequirementGenerationService generationService =
        new RequirementGenerationService(
            generator,
            new RuleBasedTestCaseGenerator(),
            flowchartMapper,
            new WfmToTestCaseGenerator(),
            normalizer,
            validator,
            null,
            new ObjectMapper());
    RequirementController controller = new RequirementController(generationService, new RequirementMapper());
    return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
  }

  private WfmToFlowchartMapper throwingMapper() {
    return new WfmToFlowchartMapper(new WfmToMermaidGenerator()) {
      @Override
      public Flowchart toFlowchart(WfmDocument wfm) {
        throw new AssertionError("Java WfmToFlowchartMapper must not be used by generate-flow");
      }
    };
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

  private static final class StubWfmGenerationClient implements WfmGenerationClient {

    private final String generationMode;
    private final String wfmSource;
    private final RuntimeException exception;
    private WfmServiceGenerateRequest request;
    private int calls;

    private StubWfmGenerationClient(String generationMode, String wfmSource, RuntimeException exception) {
      this.generationMode = generationMode;
      this.wfmSource = wfmSource;
      this.exception = exception;
    }

    private static StubWfmGenerationClient success() {
      return success("AI", "python-wfm-v2-ai-generator");
    }

    private static StubWfmGenerationClient success(String generationMode, String wfmSource) {
      return new StubWfmGenerationClient(generationMode, wfmSource, null);
    }

    private static StubWfmGenerationClient failure(RuntimeException exception) {
      return new StubWfmGenerationClient("AI", "python-ai-generator", exception);
    }

    @Override
    public WfmServiceGenerateResponse generate(WfmServiceGenerateRequest request) {
      calls++;
      this.request = request;
      if (exception != null) {
        throw exception;
      }
      return new WfmServiceGenerateResponse(
          validWfmV2(),
          validFlowchart(),
          new WfmServiceMetadata(
              "deepseek/deepseek-chat",
              "wfm-v2-python-001",
              generationMode,
              "2.0",
              wfmSource,
              "python-wfm-v2-flowchart-mapper",
              "PASSED",
              "PASSED",
              "PASSED",
              "PASSED",
              List.of()));
    }
  }

  private static com.fasterxml.jackson.databind.JsonNode validWfmV2() {
    try {
      return new ObjectMapper()
          .readTree(
              """
              {
                "wfmVersion": "2.0",
                "workflowId": "leave_request_approval",
                "workflowName": "Leave Request Approval",
                "direction": "LR",
                "nodes": [
                  {"id": "start", "kind": "START", "name": "Start", "data": {}},
                  {"id": "submit_leave", "kind": "USER_TASK", "name": "Employee submits leave request", "data": {}},
                  {"id": "manager_decision", "kind": "DECISION", "name": "Manager decision", "data": {}},
                  {"id": "end", "kind": "END", "name": "End", "data": {}}
                ],
                "transitions": [
                  {"id": "t_start_submit", "source": "start", "target": "submit_leave", "data": {}},
                  {"id": "t_submit_manager", "source": "submit_leave", "target": "manager_decision", "data": {}},
                  {"id": "t_manager_approves", "source": "manager_decision", "target": "end", "label": "Manager approves", "data": {}}
                ],
                "metadata": {"source": "AI", "language": "en", "warnings": []}
              }
              """);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to build WFM v2 fixture", exception);
    }
  }

  private static Flowchart validFlowchart() {
    return new Flowchart(
        List.of(
            new FlowNode("N1", "Start", FlowNodeType.START),
            new FlowNode("N2", "Employee submits leave request", FlowNodeType.ACTION),
            new FlowNode("N3", "Manager decision", FlowNodeType.DECISION),
            new FlowNode("N4", "Leave duration is more than 5 days?", FlowNodeType.DECISION),
            new FlowNode("N5", "HR decision", FlowNodeType.DECISION),
            new FlowNode("N6", "Leave is confirmed", FlowNodeType.ACTION),
            new FlowNode("N7", "Leave is rejected", FlowNodeType.ACTION),
            new FlowNode("N8", "End", FlowNodeType.END)),
        List.of(
            new FlowEdge("T1", "N1", "N2", null, FlowEdgeType.DEFAULT),
            new FlowEdge("T2", "N2", "N3", null, FlowEdgeType.DEFAULT),
            new FlowEdge("T3", "N3", "N4", "Manager approves", FlowEdgeType.YES),
            new FlowEdge("T4", "N3", "N7", "Manager rejects", FlowEdgeType.NO),
            new FlowEdge("T5", "N4", "N5", "Leave duration is more than 5 days", FlowEdgeType.YES),
            new FlowEdge("T6", "N4", "N6", "Leave duration is 5 days or less", FlowEdgeType.NO),
            new FlowEdge("T7", "N5", "N6", "HR approves", FlowEdgeType.YES),
            new FlowEdge("T8", "N5", "N7", "HR rejects", FlowEdgeType.NO),
            new FlowEdge("T9", "N6", "N8", null, FlowEdgeType.DEFAULT),
            new FlowEdge("T10", "N7", "N8", null, FlowEdgeType.DEFAULT)),
        "flowchart LR\n");
  }
}
