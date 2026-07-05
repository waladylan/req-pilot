package com.reqpilot.service;

import com.reqpilot.dto.GenerateFlowchartDebug;
import com.reqpilot.dto.GenerateFlowchartOptions;
import com.reqpilot.dto.GenerateFlowchartResponse;
import com.reqpilot.dto.ReactFlowDefinition;
import com.reqpilot.dto.RequirementAnalysisDto;
import com.reqpilot.dto.WfmDefinition;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FlowchartOrchestrationService {

  private static final String REACT_FLOW_FORMAT = "REACT_FLOW";

  private final RequirementAnalysisService requirementAnalysisService;
  private final WfmGenerationService wfmGenerationService;
  private final ReactFlowGenerationService reactFlowGenerationService;

  public FlowchartOrchestrationService(
      RequirementAnalysisService requirementAnalysisService,
      WfmGenerationService wfmGenerationService,
      ReactFlowGenerationService reactFlowGenerationService) {
    this.requirementAnalysisService = requirementAnalysisService;
    this.wfmGenerationService = wfmGenerationService;
    this.reactFlowGenerationService = reactFlowGenerationService;
  }

  public GenerateFlowchartResponse generateFromRequirement(
      String requirement, GenerateFlowchartOptions options) {
    if (requirement == null || requirement.isBlank()) {
      throw new IllegalArgumentException("Requirement is required");
    }

    RequirementAnalysisDto requirementAnalysis = requirementAnalysisService.analyze(requirement);
    WfmDefinition wfm = wfmGenerationService.generateFromRequirementAnalysis(requirementAnalysis);
    ReactFlowDefinition flowchart = reactFlowGenerationService.generateFromWfm(wfm);
    if (!REACT_FLOW_FORMAT.equals(flowchart.format())) {
      throw new IllegalStateException("Unsupported flowchart format: " + flowchart.format());
    }

    List<String> warnings = flowchart.warnings();
    return new GenerateFlowchartResponse(
        flowchart.workflowName(),
        REACT_FLOW_FORMAT,
        flowchart,
        warnings,
        debug(options, requirementAnalysis, wfm));
  }

  private GenerateFlowchartDebug debug(
      GenerateFlowchartOptions options, RequirementAnalysisDto requirementAnalysis, WfmDefinition wfm) {
    if (options == null || !options.debugEnabled()) {
      return null;
    }

    return new GenerateFlowchartDebug(
        options.requirementAnalysisDebugEnabled() ? requirementAnalysis : null,
        options.wfmDebugEnabled() ? wfm : null);
  }
}
