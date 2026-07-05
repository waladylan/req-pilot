package com.reqpilot.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ReactFlowEdge(
    String id,
    String source,
    String target,
    String type,
    String label,
    Map<String, Object> data) {

  public ReactFlowEdge {
    data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
  }
}
