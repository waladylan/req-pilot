package com.reqpilot.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record WfmEdge(
    String id,
    @NotBlank String from,
    @NotBlank String to,
    String label,
    String condition,
    Map<String, Object> metadata) {

  public WfmEdge {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
