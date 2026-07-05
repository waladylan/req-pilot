package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reqpilot.dto.GenerateFlowchartOptions;
import com.reqpilot.dto.GenerateFlowchartResponse;
import com.reqpilot.dto.ReactFlowDefinition;
import com.reqpilot.dto.ReactFlowEdge;
import com.reqpilot.dto.ReactFlowNode;
import com.reqpilot.dto.ReactFlowPosition;
import com.reqpilot.dto.RequirementAnalysisDto;
import com.reqpilot.dto.RequirementAnalysisModuleDto;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowchartOrchestrationServiceTest {

  @Test
  void generatesReactFlowFromRequirementThroughInternalPipeline() {
    List<String> calls = new ArrayList<>();
    RequirementAnalysisDto analysis = requirementAnalysis();
    WfmDefinition wfm = wfm();
    ReactFlowDefinition flowchart = reactFlow("REACT_FLOW", List.of("Placed unreachable node separately."));
    FlowchartOrchestrationService service =
        new FlowchartOrchestrationService(
            new StubRequirementAnalysisService(calls, analysis),
            new StubWfmGenerationService(calls, analysis, wfm),
            new StubReactFlowGenerationService(calls, wfm, flowchart));

    GenerateFlowchartResponse response =
        service.generateFromRequirement("User can create a purchase request.", null);

    assertThat(calls).containsExactly("analyze", "generate-wfm", "generate-react-flow");
    assertThat(response.workflowName()).isEqualTo("Purchase Request Approval");
    assertThat(response.format()).isEqualTo("REACT_FLOW");
    assertThat(response.flowchart()).isSameAs(flowchart);
    assertThat(response.warnings()).containsExactly("Placed unreachable node separately.");
    assertThat(response.debug()).isNull();
  }

  @Test
  void includesDebugArtifactsWhenRequested() {
    List<String> calls = new ArrayList<>();
    RequirementAnalysisDto analysis = requirementAnalysis();
    WfmDefinition wfm = wfm();
    FlowchartOrchestrationService service =
        new FlowchartOrchestrationService(
            new StubRequirementAnalysisService(calls, analysis),
            new StubWfmGenerationService(calls, analysis, wfm),
            new StubReactFlowGenerationService(calls, wfm, reactFlow("REACT_FLOW", List.of())));

    GenerateFlowchartResponse response =
        service.generateFromRequirement(
            "User can create a purchase request.",
            new GenerateFlowchartOptions(true, true, true));

    assertThat(response.debug()).isNotNull();
    assertThat(response.debug().requirementAnalysis()).isSameAs(analysis);
    assertThat(response.debug().wfm()).isSameAs(wfm);
  }

  @Test
  void rejectsBlankRequirementBeforeCallingInternalServices() {
    List<String> calls = new ArrayList<>();
    FlowchartOrchestrationService service =
        new FlowchartOrchestrationService(
            new StubRequirementAnalysisService(calls, requirementAnalysis()),
            new StubWfmGenerationService(calls, requirementAnalysis(), wfm()),
            new StubReactFlowGenerationService(calls, wfm(), reactFlow("REACT_FLOW", List.of())));

    assertThatThrownBy(() -> service.generateFromRequirement("   ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Requirement is required");
    assertThat(calls).isEmpty();
  }

  @Test
  void rejectsUnsupportedFlowchartFormat() {
    List<String> calls = new ArrayList<>();
    RequirementAnalysisDto analysis = requirementAnalysis();
    WfmDefinition wfm = wfm();
    FlowchartOrchestrationService service =
        new FlowchartOrchestrationService(
            new StubRequirementAnalysisService(calls, analysis),
            new StubWfmGenerationService(calls, analysis, wfm),
            new StubReactFlowGenerationService(calls, wfm, reactFlow("MERMAID", List.of())));

    assertThatThrownBy(() -> service.generateFromRequirement("User can create a purchase request.", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported flowchart format");
  }

  private RequirementAnalysisDto requirementAnalysis() {
    return new RequirementAnalysisDto(
        "User can create a purchase request.",
        List.of("User", "Manager"),
        List.of(
            new RequirementAnalysisModuleDto(
                "Purchase Request",
                "Module for creating and approving purchase requests.",
                List.of("Purchase Request Creation Screen"),
                List.of("Manager approval is required."),
                List.of("Amount must be positive."),
                List.of("User creates a purchase request.", "Manager approves the request."),
                List.of("Manager rejects the request."))),
        List.of("Role-based access exists."),
        List.of("Is there a time limit?"),
        "MEDIUM");
  }

  private WfmDefinition wfm() {
    return new WfmDefinition(
        "Purchase Request Approval",
        "1.0",
        "summary",
        List.of("User", "Manager"),
        List.of(
            new WfmNode("start", "start", "Start", null, null, Map.of()),
            new WfmNode("create_purchase_request", "action", "Create purchase request", "User", null, Map.of()),
            new WfmNode("approved", "end", "Request approved", null, null, Map.of())),
        List.of(
            new WfmEdge("edge_start_create", "start", "create_purchase_request", null, null, Map.of()),
            new WfmEdge("edge_create_approved", "create_purchase_request", "approved", "Success", "Created", Map.of())),
        List.of("Manager approval is required."),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "MEDIUM");
  }

  private ReactFlowDefinition reactFlow(String format, List<String> warnings) {
    return new ReactFlowDefinition(
        "Purchase Request Approval",
        "1.0",
        format,
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
                Map.of("label", "Request approved", "kind", "end", "sourceWfmNodeId", "approved"))),
        List.of(
            new ReactFlowEdge(
                "edge_start_approved",
                "start",
                "approved",
                "smoothstep",
                null,
                Map.of("condition", "", "sourceWfmEdgeId", "edge_start_approved"))),
        warnings);
  }

  private static final class StubRequirementAnalysisService extends RequirementAnalysisService {

    private final RequirementAnalysisDto analysis;
    private final List<String> calls;

    private StubRequirementAnalysisService(List<String> calls, RequirementAnalysisDto analysis) {
      super(null, null, null, null);
      this.calls = calls;
      this.analysis = analysis;
    }

    @Override
    public RequirementAnalysisDto analyze(String rawRequirement) {
      calls.add("analyze");
      return analysis;
    }
  }

  private static final class StubWfmGenerationService extends WfmGenerationService {

    private final RequirementAnalysisDto expectedAnalysis;
    private final List<String> calls;
    private final WfmDefinition wfm;

    private StubWfmGenerationService(
        List<String> calls, RequirementAnalysisDto expectedAnalysis, WfmDefinition wfm) {
      super(null, null, null, null, null, null);
      this.calls = calls;
      this.expectedAnalysis = expectedAnalysis;
      this.wfm = wfm;
    }

    @Override
    public WfmDefinition generateFromRequirementAnalysis(RequirementAnalysisDto requirementAnalysis) {
      assertThat(requirementAnalysis).isSameAs(expectedAnalysis);
      calls.add("generate-wfm");
      return wfm;
    }
  }

  private static final class StubReactFlowGenerationService extends ReactFlowGenerationService {

    private final ReactFlowDefinition flowchart;
    private final List<String> calls;
    private final WfmDefinition expectedWfm;

    private StubReactFlowGenerationService(
        List<String> calls, WfmDefinition expectedWfm, ReactFlowDefinition flowchart) {
      super(null, null);
      this.calls = calls;
      this.expectedWfm = expectedWfm;
      this.flowchart = flowchart;
    }

    @Override
    public ReactFlowDefinition generateFromWfm(WfmDefinition wfm) {
      assertThat(wfm).isSameAs(expectedWfm);
      calls.add("generate-react-flow");
      return flowchart;
    }
  }
}
