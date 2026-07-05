package com.reqpilot.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record GenerateFlowResponse(
    JsonNode wfm, FlowchartDto flowchart, GenerationMetadataDto metadata) {

  public GenerateFlowResponse {
    Objects.requireNonNull(wfm, "wfm is required");
    Objects.requireNonNull(flowchart, "flowchart is required");
    Objects.requireNonNull(metadata, "metadata is required");
  }
}
