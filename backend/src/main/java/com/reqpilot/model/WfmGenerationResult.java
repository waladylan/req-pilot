package com.reqpilot.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.reqpilot.wfm.WfmDocument;
import java.util.List;
import java.util.Objects;

public record WfmGenerationResult(
    JsonNode wfm, WfmDocument wfmDocument, Flowchart flowchart, GenerationMetadata metadata) {

  public WfmGenerationResult {
    Objects.requireNonNull(wfm, "wfm is required");
    Objects.requireNonNull(flowchart, "flowchart is required");
    metadata = metadata == null ? GenerationMetadata.ruleBased(java.util.List.of()) : metadata;
  }

  public WfmGenerationResult(WfmDocument wfm, Flowchart flowchart, GenerationMetadata metadata) {
    this(WfmJson.from(wfm), wfm, flowchart, metadata);
  }

  public WfmGenerationResult(JsonNode wfm, Flowchart flowchart, GenerationMetadata metadata) {
    this(wfm, null, flowchart, metadata);
  }

  public WfmGenerationResult(WfmDocument wfm, GenerationMetadata metadata) {
    this(wfm, new Flowchart(List.of(), List.of(), "flowchart LR\n"), metadata);
  }
}
