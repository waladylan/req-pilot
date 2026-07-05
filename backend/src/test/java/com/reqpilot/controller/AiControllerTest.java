package com.reqpilot.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.dto.ReactFlowDefinition;
import com.reqpilot.dto.ReactFlowEdge;
import com.reqpilot.dto.ReactFlowNode;
import com.reqpilot.dto.ReactFlowPosition;
import com.reqpilot.dto.RequirementAnalysisDto;
import com.reqpilot.dto.RequirementAnalysisModuleDto;
import com.reqpilot.dto.TestCase;
import com.reqpilot.dto.TestCaseSuite;
import com.reqpilot.dto.TestCoverage;
import com.reqpilot.dto.TestStep;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.service.ReactFlowGenerationService;
import com.reqpilot.service.RequirementAnalysisService;
import com.reqpilot.service.TestCaseGenerationService;
import com.reqpilot.service.WfmGenerationService;
import com.reqpilot.wfm.WfmValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AiController controller =
        new AiController(
            new StubRequirementAnalysisService(),
            new StubWfmGenerationService(),
            new StubReactFlowGenerationService(),
            new StubTestCaseGenerationService());
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
  }

  @Test
  void analyzeRequirementReturnsRequirementAnalysis() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/analyze-requirement")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"User can create a purchase request.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary").value("User can create a purchase request."))
        .andExpect(jsonPath("$.actors[0]").value("User"))
        .andExpect(jsonPath("$.modules[0].businessRules[0]").value("Manager approval is required."))
        .andExpect(jsonPath("$.openQuestions").isArray());
  }

  @Test
  void analyzeRequirementRejectsBlankRequirement() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/analyze-requirement")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void generateWfmReturnsWfmDefinition() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/generate-wfm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requirementAnalysis": {
                        "summary": "User can create a purchase request.",
                        "actors": ["User", "Manager"],
                        "modules": [],
                        "assumptions": [],
                        "openQuestions": [],
                        "riskLevel": "MEDIUM"
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowName").value("Purchase Request Approval"))
        .andExpect(jsonPath("$.version").value("1.0"))
        .andExpect(jsonPath("$.nodes[0].kind").value("start"))
        .andExpect(jsonPath("$.nodes[2].kind").value("approval"))
        .andExpect(jsonPath("$.edges[0].from").value("start"))
        .andExpect(jsonPath("$.businessRules[0]").value("Manager approval is required."));
  }

  @Test
  void generateWfmRejectsMissingInput() throws Exception {
    MockMvc validatingMockMvc =
        MockMvcBuilders
            .standaloneSetup(
                new AiController(
                    new StubRequirementAnalysisService(),
                    new WfmGenerationService(null, null, null, null, new ObjectMapper(), new WfmValidator()),
                    new StubReactFlowGenerationService(),
                    new StubTestCaseGenerationService()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    validatingMockMvc
        .perform(post("/api/ai/generate-wfm").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void generateFlowchartReturnsReactFlowDefinition() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/generate-flowchart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "wfm": {
                        "workflowName": "Purchase Request Approval",
                        "version": "1.0",
                        "summary": "summary",
                        "actors": ["User", "Manager"],
                        "nodes": [
                          {"id": "start", "kind": "start", "label": "Start", "metadata": {}},
                          {"id": "approved", "kind": "end", "label": "Approved", "metadata": {}}
                        ],
                        "edges": [
                          {"id": "edge_1", "from": "start", "to": "approved", "metadata": {}}
                        ],
                        "businessRules": [],
                        "validations": [],
                        "assumptions": [],
                        "edgeCases": [],
                        "openQuestions": [],
                        "riskLevel": "LOW"
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowName").value("Purchase Request Approval"))
        .andExpect(jsonPath("$.format").value("REACT_FLOW"))
        .andExpect(jsonPath("$.direction").value("LR"))
        .andExpect(jsonPath("$.mermaid").doesNotExist())
        .andExpect(jsonPath("$.nodes[0].type").value("start"))
        .andExpect(jsonPath("$.nodes[0].position.x").value(0.0))
        .andExpect(jsonPath("$.nodes[0].data.sourceWfmNodeId").value("start"))
        .andExpect(jsonPath("$.edges[0].type").value("smoothstep"))
        .andExpect(jsonPath("$.edges[0].source").value("start"));
  }

  @Test
  void generateFlowchartRejectsInvalidWfm() throws Exception {
    MockMvc validatingMockMvc =
        MockMvcBuilders
            .standaloneSetup(
                new AiController(
                    new StubRequirementAnalysisService(),
                    new StubWfmGenerationService(),
                    new ReactFlowGenerationService(new ObjectMapper(), new WfmValidator()),
                    new StubTestCaseGenerationService()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    validatingMockMvc
        .perform(
            post("/api/ai/generate-flowchart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidWfmRequest()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid WFM document"));
  }

  @Test
  void generateTestCasesReturnsTestCaseSuite() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/generate-test-cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "wfm": {
                        "workflowName": "Purchase Request Approval",
                        "version": "1.0",
                        "summary": "summary",
                        "actors": ["User", "Manager"],
                        "nodes": [
                          {"id": "start", "kind": "start", "label": "Start", "metadata": {}},
                          {"id": "approved", "kind": "end", "label": "Approved", "metadata": {}}
                        ],
                        "edges": [
                          {"id": "edge_1", "from": "start", "to": "approved", "metadata": {}}
                        ],
                        "businessRules": [],
                        "validations": [],
                        "assumptions": [],
                        "edgeCases": [],
                        "openQuestions": [],
                        "riskLevel": "LOW"
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suiteName").value("Purchase Request Approval Test Suite"))
        .andExpect(jsonPath("$.version").value("1.0"))
        .andExpect(jsonPath("$.sourceWorkflowName").value("Purchase Request Approval"))
        .andExpect(jsonPath("$.testCases[0].id").value("TC-001"))
        .andExpect(jsonPath("$.testCases[0].sourceNodeIds[0]").value("approved"))
        .andExpect(jsonPath("$.coverage.nodeCount").value(2))
        .andExpect(jsonPath("$.warnings").isArray());
  }

  @Test
  void generateTestCasesRejectsInvalidWfm() throws Exception {
    MockMvc validatingMockMvc =
        MockMvcBuilders
            .standaloneSetup(
                new AiController(
                    new StubRequirementAnalysisService(),
                    new StubWfmGenerationService(),
                    new StubReactFlowGenerationService(),
                    new TestCaseGenerationService(new ObjectMapper(), new WfmValidator())))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    validatingMockMvc
        .perform(
            post("/api/ai/generate-test-cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidWfmRequest()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid WFM document"));
  }

  private String invalidWfmRequest() {
    return """
        {
          "wfm": {
            "workflowName": "Invalid",
            "version": "1.0",
            "summary": "summary",
            "actors": [],
            "nodes": [
              {"id": "start", "kind": "start", "label": "Start", "metadata": {}}
            ],
            "edges": [],
            "businessRules": [],
            "validations": [],
            "assumptions": [],
            "edgeCases": [],
            "openQuestions": [],
            "riskLevel": "LOW"
          }
        }
        """;
  }

  private static final class StubRequirementAnalysisService extends RequirementAnalysisService {

    private StubRequirementAnalysisService() {
      super(null, null, null, null);
    }

    @Override
    public RequirementAnalysisDto analyze(String rawRequirement) {
      return new RequirementAnalysisDto(
          rawRequirement,
          List.of("User", "Manager"),
          List.of(
              new RequirementAnalysisModuleDto(
                  "Purchase Request",
                  "Module for creating and approving purchase requests.",
                  List.of("Purchase Request Creation Screen", "Approval Screen"),
                  List.of("Manager approval is required."),
                  List.of("Amount must be positive."),
                  List.of("User creates a purchase request."),
                  List.of("Manager rejects request."))),
          List.of("Role-based access control exists."),
          List.of("Is there a time limit for approvals?"),
          "MEDIUM");
    }
  }

  private static final class StubWfmGenerationService extends WfmGenerationService {

    private StubWfmGenerationService() {
      super(null, null, null, null, null, null);
    }

    @Override
    public WfmDefinition generateFromRequirementAnalysis(RequirementAnalysisDto requirementAnalysis) {
      return definition();
    }

    @Override
    public WfmDefinition generateFromRequirementAnalysisJson(String requirementAnalysisJson) {
      return definition();
    }

    private WfmDefinition definition() {
      return new WfmDefinition(
          "Purchase Request Approval",
          "1.0",
          "summary",
          List.of("User", "Manager"),
          List.of(
              node("start", "start", "Start"),
              node("create_purchase_request", "action", "Create purchase request"),
              node("manager_approval", "approval", "Manager approval"),
              node("approved", "end", "Approved")),
          List.of(
              new WfmEdge("edge_start_create", "start", "create_purchase_request", null, null, Map.of()),
              new WfmEdge(
                  "edge_create_approval", "create_purchase_request", "manager_approval", null, null, Map.of()),
              new WfmEdge(
                  "edge_approval_approved",
                  "manager_approval",
                  "approved",
                  "Approve",
                  "Manager approves",
                  Map.of())),
          List.of("Manager approval is required."),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          "MEDIUM");
    }

    private com.reqpilot.dto.WfmNode node(String id, String kind, String label) {
      return new com.reqpilot.dto.WfmNode(id, kind, label, null, null, Map.of());
    }
  }

  private static final class StubReactFlowGenerationService extends ReactFlowGenerationService {

    private StubReactFlowGenerationService() {
      super(null, null);
    }

    @Override
    public ReactFlowDefinition generateFromWfm(WfmDefinition wfm) {
      return definition();
    }

    @Override
    public ReactFlowDefinition generateFromWfmJson(String wfmJson) {
      return definition();
    }

    private ReactFlowDefinition definition() {
      return new ReactFlowDefinition(
          "Purchase Request Approval",
          "1.0",
          "REACT_FLOW",
          "LR",
          List.of(
              new ReactFlowNode(
                  "start",
                  "start",
                  new ReactFlowPosition(0, 0),
                  Map.of("label", "Start", "kind", "start", "sourceWfmNodeId", "start"))),
          List.of(
              new ReactFlowEdge(
                  "edge_1",
                  "start",
                  "approved",
                  "smoothstep",
                  null,
                  Map.of("sourceWfmEdgeId", "edge_1", "metadata", Map.of()))),
          List.of());
    }
  }

  private static final class StubTestCaseGenerationService extends TestCaseGenerationService {

    private StubTestCaseGenerationService() {
      super(null, null);
    }

    @Override
    public TestCaseSuite generateFromWfm(WfmDefinition wfm) {
      return suite();
    }

    @Override
    public TestCaseSuite generateFromWfmJson(String wfmJson) {
      return suite();
    }

    private TestCaseSuite suite() {
      return new TestCaseSuite(
          "Purchase Request Approval Test Suite",
          "1.0",
          "Purchase Request Approval",
          List.of(
              new TestCase(
                  "TC-001",
                  "Happy path - Purchase Request Approval",
                  "HAPPY_PATH",
                  "P0",
                  "User",
                  List.of("User is logged in."),
                  List.of(new TestStep(1, "Complete workflow", "User", Map.of())),
                  List.of("Workflow is approved."),
                  List.of("approved"),
                  List.of("edge_1"),
                  List.of("happy-path"))),
          new TestCoverage(2, 1, List.of("approved"), List.of("edge_1"), List.of("start"), List.of()),
          List.of());
    }
  }
}
