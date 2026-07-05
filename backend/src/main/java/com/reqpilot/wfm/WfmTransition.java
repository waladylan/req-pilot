package com.reqpilot.wfm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record WfmTransition(
    @NotBlank String id,
    @NotBlank String from,
    @NotBlank String to,
    @NotNull WfmTransitionSemantic semantic,
    String kind,
    String condition,
    String description,
    Map<String, Object> data) {

  public WfmTransition {
    data = data == null ? Map.of() : Map.copyOf(data);
  }
}
