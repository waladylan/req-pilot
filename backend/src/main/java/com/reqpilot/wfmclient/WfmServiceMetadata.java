package com.reqpilot.wfmclient;

import java.util.List;

public record WfmServiceMetadata(
    String model,
    String promptVersion,
    String generationMode,
    String wfmVersion,
    String wfmSource,
    String flowchartSource,
    String validationStatus,
    String canonicalizationStatus,
    String normalizationStatus,
    String mappingStatus,
    List<String> warnings) {

  public WfmServiceMetadata(String model, String promptVersion, String validationStatus, List<String> warnings) {
    this(model, promptVersion, null, null, null, null, validationStatus, null, null, null, warnings);
  }

  public WfmServiceMetadata {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
