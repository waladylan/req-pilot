package com.reqpilot.controller;

import com.reqpilot.dto.AnalyzeRequirementRequest;
import com.reqpilot.dto.GenerateTestCaseSuiteRequest;
import com.reqpilot.dto.GenerateReactFlowRequest;
import com.reqpilot.dto.GenerateWfmRequest;
import com.reqpilot.dto.ReactFlowDefinition;
import com.reqpilot.dto.RequirementAnalysisDto;
import com.reqpilot.dto.TestCaseSuite;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.service.ReactFlowGenerationService;
import com.reqpilot.service.RequirementAnalysisService;
import com.reqpilot.service.TestCaseGenerationService;
import com.reqpilot.service.WfmGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

  private final RequirementAnalysisService requirementAnalysisService;
  private final WfmGenerationService wfmGenerationService;
  private final ReactFlowGenerationService reactFlowGenerationService;
  private final TestCaseGenerationService testCaseGenerationService;

  public AiController(
      RequirementAnalysisService requirementAnalysisService,
      WfmGenerationService wfmGenerationService,
      ReactFlowGenerationService reactFlowGenerationService,
      TestCaseGenerationService testCaseGenerationService) {
    this.requirementAnalysisService = requirementAnalysisService;
    this.wfmGenerationService = wfmGenerationService;
    this.reactFlowGenerationService = reactFlowGenerationService;
    this.testCaseGenerationService = testCaseGenerationService;
  }

  @PostMapping("/analyze-requirement")
  public RequirementAnalysisDto analyzeRequirement(@Valid @RequestBody AnalyzeRequirementRequest request) {
    return requirementAnalysisService.analyze(request.requirement());
  }

  @PostMapping("/generate-wfm")
  public WfmDefinition generateWfm(@Valid @RequestBody GenerateWfmRequest request) {
    if (request.requirementAnalysis() != null) {
      return wfmGenerationService.generateFromRequirementAnalysis(request.requirementAnalysis());
    }
    return wfmGenerationService.generateFromRequirementAnalysisJson(request.requirementAnalysisJson());
  }

  @PostMapping("/generate-flowchart")
  public ReactFlowDefinition generateFlowchart(@Valid @RequestBody GenerateReactFlowRequest request) {
    if (request.wfm() != null) {
      return reactFlowGenerationService.generateFromWfm(request.wfm());
    }
    return reactFlowGenerationService.generateFromWfmJson(request.wfmJson());
  }

  @PostMapping("/generate-test-cases")
  public TestCaseSuite generateTestCases(@Valid @RequestBody GenerateTestCaseSuiteRequest request) {
    if (request.wfm() != null) {
      return testCaseGenerationService.generateFromWfm(request.wfm());
    }
    return testCaseGenerationService.generateFromWfmJson(request.wfmJson());
  }
}
