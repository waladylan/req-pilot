package com.reqpilot.controller;

import com.reqpilot.dto.RequirementResponse;
import com.reqpilot.dto.RequirementTestCasesResponse;
import com.reqpilot.dto.RequirementUpdateRequest;
import com.reqpilot.dto.SavedRequirementGenerationResponse;
import com.reqpilot.service.RequirementService;
import com.reqpilot.service.RequirementTestCaseService;
import com.reqpilot.service.SavedRequirementGenerationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requirements")
public class RequirementResourceController {

  private final RequirementService requirementService;
  private final SavedRequirementGenerationService generationService;
  private final RequirementTestCaseService testCaseService;

  public RequirementResourceController(
      RequirementService requirementService,
      SavedRequirementGenerationService generationService,
      RequirementTestCaseService testCaseService) {
    this.requirementService = requirementService;
    this.generationService = generationService;
    this.testCaseService = testCaseService;
  }

  @GetMapping("/{requirementId}")
  public RequirementResponse getRequirement(@PathVariable UUID requirementId) {
    return requirementService.toResponse(requirementService.getRequirement(requirementId));
  }

  @PutMapping("/{requirementId}")
  public RequirementResponse updateRequirement(
      @PathVariable UUID requirementId, @Valid @RequestBody RequirementUpdateRequest request) {
    return requirementService.toResponse(requirementService.updateRequirement(requirementId, request));
  }

  @DeleteMapping("/{requirementId}")
  public ResponseEntity<Void> deleteRequirement(@PathVariable UUID requirementId) {
    requirementService.deleteRequirement(requirementId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{requirementId}/generate-flow")
  public SavedRequirementGenerationResponse generateFlow(@PathVariable UUID requirementId) {
    return generationService.generateFlow(requirementId);
  }

  @PostMapping("/{requirementId}/generate-test-cases")
  public RequirementTestCasesResponse generateTestCases(@PathVariable UUID requirementId) {
    return testCaseService.generateTestCases(requirementId);
  }

  @GetMapping("/{requirementId}/test-cases")
  public RequirementTestCasesResponse getTestCases(@PathVariable UUID requirementId) {
    return testCaseService.getTestCases(requirementId);
  }
}
