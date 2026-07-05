package com.reqpilot.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reqpilot.dto.RequirementMapper;
import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GeneratedFlow;
import com.reqpilot.model.GeneratedTestCase;
import com.reqpilot.model.GeneratedTestCases;
import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.service.RequirementGenerationService;
import com.reqpilot.wfm.WfmNormalizer;
import com.reqpilot.wfm.WfmAst;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmExtensions;
import com.reqpilot.wfm.WfmNode;
import com.reqpilot.wfm.WfmNodeRole;
import com.reqpilot.wfm.WfmTransition;
import com.reqpilot.wfm.WfmTransitionSemantic;
import com.reqpilot.wfm.WfmWorkflow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RequirementControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    RequirementController controller =
        new RequirementController(
            new StubRequirementGenerationService(minimalWfm(), minimalFlowchart()),
            new RequirementMapper());
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
  }

  @Test
  void generateFlowReturnsWfmFlowchartAndMetadata() throws Exception {
    mockMvc
        .perform(
            post("/api/requirements/generate-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"Feature: Delete Product\\n\\nIf user confirms, delete product\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wfm.schemaVersion").value("1.0"))
        .andExpect(jsonPath("$.wfm.modelType").value("WORKFLOW_AST"))
        .andExpect(jsonPath("$.wfm.workflow.id").value("delete-product"))
        .andExpect(jsonPath("$.wfm.extensions.nodeKinds").isArray())
        .andExpect(jsonPath("$.wfm.ast.nodes[0].role").value("START"))
        .andExpect(jsonPath("$.wfm.ast.transitions[0].semantic").value("DEFAULT"))
        .andExpect(jsonPath("$.flowchart.nodes[0].id").value("N1"))
        .andExpect(jsonPath("$.flowchart.edges[0].type").value("DEFAULT"))
        .andExpect(jsonPath("$.metadata.source").value("RULE_BASED"))
        .andExpect(jsonPath("$.metadata.generationMode").value("RULE_BASED"))
        .andExpect(jsonPath("$.metadata.wfmSource").value("spring-boot-rule-engine"))
        .andExpect(jsonPath("$.metadata.validationStatus").value("PASSED"))
        .andExpect(jsonPath("$.metadata.normalizationStatus").value("PASSED"))
        .andExpect(jsonPath("$.metadata.warnings").isArray())
        .andExpect(content().string(not(containsString("\"position\""))))
        .andExpect(content().string(not(containsString("\"sourceHandle\""))))
        .andExpect(content().string(not(containsString("\"targetHandle\""))))
        .andExpect(content().string(not(containsString("\"reactFlowType\""))));
  }

  @Test
  void generateFlowReturnsAiMetadataFields() throws Exception {
    RequirementController controller =
        new RequirementController(
            new StubRequirementGenerationService(
                minimalWfm(),
                minimalFlowchart(),
                GenerationMetadata.ai(
                    "OPENROUTER",
                    "deepseek/deepseek-chat",
                    "requirement-to-wfm-v1",
                    true,
                    false,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    42L)),
            new RequirementMapper());
    MockMvc aiMockMvc =
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();

    aiMockMvc
        .perform(
            post("/api/requirements/generate-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"Feature: Delete Product\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wfm.schemaVersion").value("1.0"))
        .andExpect(jsonPath("$.flowchart.nodes").isArray())
        .andExpect(jsonPath("$.metadata.source").value("AI"))
        .andExpect(jsonPath("$.metadata.analyzer").value("AI"))
        .andExpect(jsonPath("$.metadata.generationMode").value("AI"))
        .andExpect(jsonPath("$.metadata.wfmSource").value("python-wfm-service"))
        .andExpect(jsonPath("$.metadata.provider").value("OPENROUTER"))
        .andExpect(jsonPath("$.metadata.model").value("deepseek/deepseek-chat"))
        .andExpect(jsonPath("$.metadata.promptVersion").value("requirement-to-wfm-v1"))
        .andExpect(jsonPath("$.metadata.cacheHit").value(true))
        .andExpect(jsonPath("$.metadata.fallbackUsed").value(false))
        .andExpect(jsonPath("$.metadata.validationWarnings").isArray())
        .andExpect(jsonPath("$.metadata.validationErrors").isArray())
        .andExpect(jsonPath("$.metadata.latencyMs").value(42));
  }

  @Test
  void generateTestCasesAcceptsWfmWithoutFlowchartAndReturnsMetadata() throws Exception {
    mockMvc
        .perform(
            post("/api/requirements/generate-testcases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "wfm": {
                        "schemaVersion": "1.0",
                        "modelType": "WORKFLOW_AST",
                        "workflow": {"id": "delete-product", "title": "Delete Product", "language": "en"},
                        "extensions": {"nodeKinds": [], "transitionKinds": []},
                        "ast": {
                          "actors": [],
                          "variables": [],
                          "nodes": [
                            {"id": "N1", "role": "START", "kind": "START", "title": "Start"},
                            {"id": "N2", "role": "ACTION", "kind": "DELETE_PRODUCT", "title": "Delete Product"},
                            {"id": "N3", "role": "END", "kind": "END", "title": "End"}
                          ],
                          "transitions": [
                            {"id": "T1", "from": "N1", "to": "N2", "semantic": "DEFAULT"},
                            {"id": "T2", "from": "N2", "to": "N3", "semantic": "SUCCESS"}
                          ],
                          "annotations": []
                        }
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testCases").isArray())
        .andExpect(jsonPath("$.metadata.source").value("WFM"))
        .andExpect(jsonPath("$.metadata.workflowId").value("delete-product"))
        .andExpect(jsonPath("$.metadata.pathCount").value(1))
        .andExpect(jsonPath("$.metadata.warnings").isArray());
  }

  private WfmDocument minimalWfm() {
    return new WfmDocument(
        "1.0",
        "WORKFLOW_AST",
        new WfmWorkflow("delete-product", "Delete Product", null, "en", null, null),
        new WfmExtensions(List.of(), List.of()),
        new WfmAst(
            List.of(),
            List.of(),
            List.of(
                new WfmNode("N1", WfmNodeRole.START, "START", "Start", null, null, List.of(), null),
                new WfmNode("N2", WfmNodeRole.ACTION, "DELETE_PRODUCT", "Delete Product", null, null, List.of(), null),
                new WfmNode("N3", WfmNodeRole.END, "END", "End", null, null, List.of(), null)),
            List.of(
                new WfmTransition("T1", "N1", "N2", WfmTransitionSemantic.DEFAULT, null, null, null, null),
                new WfmTransition("T2", "N2", "N3", WfmTransitionSemantic.SUCCESS, null, null, null, null)),
            List.of()));
  }

  private Flowchart minimalFlowchart() {
    return new Flowchart(
        List.of(
            new FlowNode("N1", "Start", FlowNodeType.START),
            new FlowNode("N2", "Delete Product", FlowNodeType.ACTION),
            new FlowNode("N3", "End", FlowNodeType.END)),
        List.of(
            new FlowEdge("T1", "N1", "N2", null, FlowEdgeType.DEFAULT),
            new FlowEdge("T2", "N2", "N3", null, FlowEdgeType.SUCCESS)),
        "flowchart LR\n  N1 --> N2\n  N2 --> N3");
  }

  private static final class StubRequirementGenerationService extends RequirementGenerationService {

    private final Flowchart flowchart;
    private final GenerationMetadata metadata;
    private final WfmDocument wfm;

    private StubRequirementGenerationService(WfmDocument wfm, Flowchart flowchart) {
      this(wfm, flowchart, GenerationMetadata.ruleBased(List.of()));
    }

    private StubRequirementGenerationService(WfmDocument wfm, Flowchart flowchart, GenerationMetadata metadata) {
      super(null, null, null, null, new WfmNormalizer(), null, null, null);
      this.wfm = wfm;
      this.flowchart = flowchart;
      this.metadata = metadata;
    }

    @Override
    public GeneratedFlow generateFlow(String requirement) {
      return new GeneratedFlow(wfm, flowchart, metadata);
    }

    @Override
    public GeneratedTestCases generateTestCases(String requirement, Flowchart flowchart, WfmDocument wfm) {
      return new GeneratedTestCases(
          List.of(
              new GeneratedTestCase(
                  "TC001",
                  "Delete product successfully",
                  "User is logged in and product exists.",
                  List.of("Delete Product."),
                  "Product is deleted.",
                  com.reqpilot.model.TestCasePriority.HIGH)),
          "WFM",
          "delete-product",
          1,
          List.of());
    }
  }
}
