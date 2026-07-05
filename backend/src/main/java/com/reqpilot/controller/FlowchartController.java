package com.reqpilot.controller;

import com.reqpilot.dto.GenerateFlowchartRequest;
import com.reqpilot.dto.GenerateFlowchartResponse;
import com.reqpilot.service.FlowchartOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flowcharts")
public class FlowchartController {

  private final FlowchartOrchestrationService orchestrationService;

  public FlowchartController(FlowchartOrchestrationService orchestrationService) {
    this.orchestrationService = orchestrationService;
  }

  @PostMapping("/generate")
  public ResponseEntity<GenerateFlowchartResponse> generateFlowchart(
      @Valid @RequestBody GenerateFlowchartRequest request) {
    return ResponseEntity.ok(
        orchestrationService.generateFromRequirement(request.requirement(), request.options()));
  }
}
