package com.reqpilot.dto;

import com.reqpilot.model.FlowEdgeType;
import jakarta.validation.constraints.NotBlank;

public record FlowEdgeDto(
    @NotBlank String id,
    @NotBlank String source,
    @NotBlank String target,
    String label,
    FlowEdgeType type) {

  public FlowEdgeDto {
    if (type == null) {
      type = FlowEdgeType.DEFAULT;
    }
  }
}
