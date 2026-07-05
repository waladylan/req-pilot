package com.reqpilot.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ReactFlowNode(
    String id,
    String type,
    ReactFlowPosition position,
    Map<String, Object> data) {

  public ReactFlowNode {
    data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
  }
}
