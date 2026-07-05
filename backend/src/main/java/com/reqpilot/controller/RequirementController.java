package com.reqpilot.controller;

import com.reqpilot.dto.GenerationMetadataDto;
import com.reqpilot.dto.GenerateFlowRequest;
import com.reqpilot.dto.GenerateFlowResponse;
import com.reqpilot.dto.GenerateTestCasesRequest;
import com.reqpilot.dto.GenerateTestCasesResponse;
import com.reqpilot.dto.RequirementMapper;
import com.reqpilot.dto.TestCaseGenerationMetadataDto;
import com.reqpilot.model.GeneratedFlow;
import com.reqpilot.model.GeneratedTestCases;
import com.reqpilot.service.RequirementGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

  private final RequirementGenerationService generationService;
  private final RequirementMapper mapper;

  public RequirementController(RequirementGenerationService generationService, RequirementMapper mapper) {
    this.generationService = generationService;
    this.mapper = mapper;
  }

  @PostMapping("/generate-flow")
  public ResponseEntity<GenerateFlowResponse> generateFlow(
      @Valid @RequestBody GenerateFlowRequest request) {
    GeneratedFlow generatedFlow = generationService.generateFlow(request.requirement());
    return ResponseEntity.ok(
        new GenerateFlowResponse(
            generatedFlow.wfm(),
            mapper.toDto(generatedFlow.flowchart()),
            GenerationMetadataDto.from(generatedFlow.metadata())));
  }

  @PostMapping("/generate-testcases")
  public ResponseEntity<GenerateTestCasesResponse> generateTestCases(
      @Valid @RequestBody GenerateTestCasesRequest request) {
    GeneratedTestCases generatedTestCases =
        generationService.generateTestCases(
            request.requirement(),
            request.flowchart() == null ? null : mapper.toModel(request.flowchart()),
            request.wfm());
    return ResponseEntity.ok(
        new GenerateTestCasesResponse(
            mapper.toTestCaseDtos(generatedTestCases.testCases()),
            new TestCaseGenerationMetadataDto(
                generatedTestCases.source(),
                generatedTestCases.workflowId(),
                generatedTestCases.pathCount(),
                generatedTestCases.warnings())));
  }
}
