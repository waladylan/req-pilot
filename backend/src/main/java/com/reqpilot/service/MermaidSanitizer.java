package com.reqpilot.service;

import com.reqpilot.dto.WfmNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.springframework.stereotype.Component;

@Component
public class MermaidSanitizer {

  public Map<String, String> sanitizeNodeIds(List<WfmNode> nodes) {
    Map<String, String> ids = new LinkedHashMap<>();
    Set<String> usedIds = new HashSet<>();
    int fallbackIndex = 1;
    for (WfmNode node : nodes == null ? List.<WfmNode>of() : nodes) {
      String sourceId = node == null ? null : node.id();
      ids.put(sourceId, sanitizeUniqueId(sourceId, usedIds, fallbackIndex));
      fallbackIndex++;
    }
    return ids;
  }

  public String sanitizeUniqueId(String rawId, Set<String> usedIds, int fallbackIndex) {
    Set<String> used = usedIds == null ? new HashSet<>() : usedIds;
    String base = sanitizeBaseId(rawId, fallbackIndex);
    String candidate = base;
    int suffix = 2;
    while (used.contains(candidate)) {
      candidate = base + "_" + suffix;
      suffix++;
    }
    used.add(candidate);
    return candidate;
  }

  public String escapeNodeLabel(String value, String fallback) {
    String label = isBlank(value) ? fallback : value;
    if (label == null) {
      return "";
    }
    return label.trim()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", "<br/>")
        .replace("\"", "\\\"");
  }

  public String escapeEdgeLabel(String value) {
    if (isBlank(value)) {
      return "";
    }
    return value.trim()
        .replace("\r\n", " ")
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace("|", "&#124;");
  }

  private String sanitizeBaseId(String rawId, int fallbackIndex) {
    String sanitized = rawId == null ? "" : rawId.trim();
    sanitized = sanitized.replaceAll("[^A-Za-z0-9_]+", "_");
    sanitized = sanitized.replaceAll("_+", "_");
    sanitized = sanitized.replaceAll("^_+|_+$", "");
    if (sanitized.isBlank()) {
      sanitized = "node_" + Math.max(fallbackIndex, 1);
    }
    if (Character.isDigit(sanitized.charAt(0))) {
      sanitized = "node_" + sanitized;
    }
    return sanitized;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
