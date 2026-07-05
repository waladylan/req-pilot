package com.reqpilot.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reqpilot.dto.GenerateFlowchartDebug;
import com.reqpilot.dto.GenerateFlowchartOptions;
import com.reqpilot.dto.GenerateFlowchartResponse;
import com.reqpilot.dto.ReactFlowDefinition;
import com.reqpilot.dto.ReactFlowEdge;
import com.reqpilot.dto.ReactFlowNode;
import com.reqpilot.dto.ReactFlowPosition;
import com.reqpilot.dto.RequirementAnalysisDto;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import com.reqpilot.service.FlowchartOrchestrationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FlowchartControllerTest {

  private StubFlowchartOrchestrationService orchestrationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    orchestrationService = new StubFlowchartOrchestrationService();
    mockMvc =
        MockMvcBuilders
            .standaloneSetup(new FlowchartController(orchestrationService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void blankRequirementReturnsBadRequestWithoutCallingOrchestration() throws Exception {
    mockMvc
        .perform(
            post("/api/flowcharts/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));

    org.assertj.core.api.Assertions.assertThat(orchestrationService.called).isFalse();
  }

  @Test
  void validRequirementReturnsPublicReactFlowResponse() throws Exception {
    mockMvc
        .perform(
            post("/api/flowcharts/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requirement": "User can create a purchase request. Manager approves. If amount > 5000, finance approval is required."
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowName").value("Purchase Request Approval"))
        .andExpect(jsonPath("$.format").value("REACT_FLOW"))
        .andExpect(jsonPath("$.flowchart.workflowName").value("Purchase Request Approval"))
        .andExpect(jsonPath("$.flowchart.format").value("REACT_FLOW"))
        .andExpect(jsonPath("$.flowchart.nodes[0].id").value("start"))
        .andExpect(jsonPath("$.flowchart.nodes[0].data.label").value("Start"))
        .andExpect(jsonPath("$.flowchart.edges[0].source").value("start"))
        .andExpect(jsonPath("$.warnings[0]").value("Placed unreachable node separately."))
        .andExpect(jsonPath("$.debug").doesNotExist())
        .andExpect(jsonPath("$.flowchart.mermaid").doesNotExist())
        .andExpect(content().string(not(containsString("api-key"))))
        .andExpect(content().string(not(containsString("OPENROUTER_API_KEY"))));

    org.assertj.core.api.Assertions.assertThat(orchestrationService.called).isTrue();
  }

  @Test
  void debugResponseIsIncludedWhenRequested() throws Exception {
    mockMvc
        .perform(
            post("/api/flowcharts/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requirement": "User can create a purchase request.",
                      "options": {
                        "includeDebug": true,
                        "includeRequirementAnalysis": true,
                        "includeWfm": true
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.debug.requirementAnalysis.summary").value("Analysis summary"))
        .andExpect(jsonPath("$.debug.wfm.workflowName").value("Purchase Request Approval"));
  }

  private static final class StubFlowchartOrchestrationService extends FlowchartOrchestrationService {

    private boolean called;

    private StubFlowchartOrchestrationService() {
      super(null, null, null);
    }

    @Override
    public GenerateFlowchartResponse generateFromRequirement(
        String requirement, GenerateFlowchartOptions options) {
      called = true;
      ReactFlowDefinition flowchart =
          new ReactFlowDefinition(
              "Purchase Request Approval",
              "1.0",
              "REACT_FLOW",
              "LR",
              List.of(
                  new ReactFlowNode(
                      "start",
                      "start",
                      new ReactFlowPosition(0, 0),
                      Map.of("label", "Start", "kind", "start", "sourceWfmNodeId", "start")),
                  new ReactFlowNode(
                      "approved",
                      "end",
                      new ReactFlowPosition(0, 140),
                      Map.of(
                          "label",
                          "Request approved",
                          "kind",
                          "end",
                          "sourceWfmNodeId",
                          "approved"))),
              List.of(
                  new ReactFlowEdge(
                      "edge_start_approved",
                      "start",
                      "approved",
                      "smoothstep",
                      null,
                      Map.of("condition", "", "sourceWfmEdgeId", "edge_start_approved"))),
              List.of("Placed unreachable node separately."));
      return new GenerateFlowchartResponse(
          "Purchase Request Approval",
          "REACT_FLOW",
          flowchart,
          flowchart.warnings(),
          debug(options));
    }

    private GenerateFlowchartDebug debug(GenerateFlowchartOptions options) {
      if (options == null || !options.debugEnabled()) {
        return null;
      }
      return new GenerateFlowchartDebug(
          new RequirementAnalysisDto("Analysis summary", List.of("User"), List.of(), List.of(), List.of(), "LOW"),
          new WfmDefinition(
              "Purchase Request Approval",
              "1.0",
              "summary",
              List.of("User"),
              List.of(
                  new WfmNode("start", "start", "Start", null, null, Map.of()),
                  new WfmNode("approved", "end", "Request approved", null, null, Map.of())),
              List.of(new WfmEdge("edge_start_approved", "start", "approved", null, null, Map.of())),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              "LOW"));
    }
  }
}
