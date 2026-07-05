package com.reqpilot.service;

import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.wfm.WfmDocument;
import java.util.Objects;

public record RequirementAnalysis(WfmDocument wfm, GenerationMetadata metadata) {

  public RequirementAnalysis {
    Objects.requireNonNull(wfm, "wfm is required");
    Objects.requireNonNull(metadata, "metadata is required");
  }
}
