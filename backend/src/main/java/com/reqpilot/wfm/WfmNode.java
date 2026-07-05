package com.reqpilot.wfm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record WfmNode(
    @NotBlank String id,
    @NotNull WfmNodeRole role,
    @NotBlank String kind,
    @NotBlank String title,
    String description,
    String actorId,
    List<String> tags,
    Map<String, Object> data) {

  public WfmNode {
    tags = tags == null ? List.of() : List.copyOf(tags);
    data = data == null ? Map.of() : Map.copyOf(data);
  }
}
