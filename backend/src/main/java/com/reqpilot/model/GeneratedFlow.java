package com.reqpilot.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.reqpilot.wfm.WfmDocument;
import java.util.List;
import java.util.Objects;

public record GeneratedFlow(
    JsonNode wfm, WfmDocument wfmDocument, Flowchart flowchart, GenerationMetadata metadata) {

  public GeneratedFlow {
    Objects.requireNonNull(wfm, "wfm is required");
    Objects.requireNonNull(flowchart, "flowchart is required");
    metadata = metadata == null ? GenerationMetadata.ruleBased(List.of()) : metadata;
  }

  public GeneratedFlow(WfmDocument wfm, Flowchart flowchart, GenerationMetadata metadata) {
    this(WfmJson.from(wfm), wfm, flowchart, metadata);
  }

  public GeneratedFlow(JsonNode wfm, Flowchart flowchart, GenerationMetadata metadata) {
    this(wfm, null, flowchart, metadata);
  }

  public GeneratedFlow(WfmDocument wfm, Flowchart flowchart, List<String> warnings) {
    this(wfm, flowchart, GenerationMetadata.ruleBased(warnings));
  }

  public List<String> warnings() {
    return metadata.warnings();
  }
}
