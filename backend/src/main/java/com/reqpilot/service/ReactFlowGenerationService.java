package com.reqpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.dto.ReactFlowDefinition;
import com.reqpilot.dto.ReactFlowEdge;
import com.reqpilot.dto.ReactFlowNode;
import com.reqpilot.dto.ReactFlowPosition;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import com.reqpilot.wfm.WfmValidationError;
import com.reqpilot.wfm.WfmValidationException;
import com.reqpilot.wfm.WfmValidationResult;
import com.reqpilot.wfm.WfmValidator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReactFlowGenerationService {

  private static final String FORMAT = "REACT_FLOW";
  private static final String VERSION = "1.0";
  private static final String DEFAULT_DIRECTION = "LR";
  private static final String DEFAULT_EDGE_TYPE = "smoothstep";
  private static final double LEVEL_SPACING = 300.0;
  private static final double SIBLING_SPACING = 160.0;

  private final ObjectMapper objectMapper;
  private final WfmValidator wfmValidator;

  public ReactFlowGenerationService(ObjectMapper objectMapper, WfmValidator wfmValidator) {
    this.objectMapper = objectMapper;
    this.wfmValidator = wfmValidator;
  }

  public ReactFlowDefinition generateFromWfm(WfmDefinition wfm) {
    if (wfm == null) {
      throw new IllegalArgumentException("WFM is required");
    }

    WfmValidationResult validationResult = wfmValidator.validateDefinition(wfm);
    if (!validationResult.valid()) {
      throw new WfmValidationException(validationResult.errors());
    }

    List<String> warnings = new ArrayList<>(validationResult.warnings().stream().map(this::formatWarning).toList());
    Map<String, String> nodeIdsByWfmId = sanitizeNodeIds(wfm.nodes());
    Map<String, ReactFlowPosition> positions = calculatePositions(wfm, warnings);
    List<ReactFlowNode> nodes = toReactFlowNodes(wfm.nodes(), nodeIdsByWfmId, positions);
    List<ReactFlowEdge> edges = toReactFlowEdges(wfm.edges(), nodeIdsByWfmId);

    return new ReactFlowDefinition(
        wfm.workflowName(),
        VERSION,
        FORMAT,
        DEFAULT_DIRECTION,
        nodes,
        edges,
        warnings);
  }

  public ReactFlowDefinition generateFromWfmJson(String wfmJson) {
    if (wfmJson == null || wfmJson.isBlank()) {
      throw new IllegalArgumentException("WFM JSON is required");
    }
    try {
      JsonNode node = objectMapper.readTree(wfmJson);
      if (!node.isObject()) {
        throw new IllegalArgumentException("WFM JSON must be an object");
      }
      return generateFromWfm(objectMapper.treeToValue(node, WfmDefinition.class));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("WFM JSON is invalid", exception);
    }
  }

  private List<ReactFlowNode> toReactFlowNodes(
      List<WfmNode> wfmNodes,
      Map<String, String> nodeIdsByWfmId,
      Map<String, ReactFlowPosition> positions) {
    List<ReactFlowNode> nodes = new ArrayList<>();
    for (WfmNode node : wfmNodes) {
      nodes.add(
          new ReactFlowNode(
              nodeIdsByWfmId.get(node.id()),
              reactFlowType(node.kind()),
              positions.get(node.id()),
              nodeData(node)));
    }
    return nodes;
  }

  private List<ReactFlowEdge> toReactFlowEdges(List<WfmEdge> wfmEdges, Map<String, String> nodeIdsByWfmId) {
    List<ReactFlowEdge> edges = new ArrayList<>();
    for (WfmEdge edge : wfmEdges) {
      String edgeId = edgeKey(edge);
      String label = firstNonBlank(edge.condition(), edge.label());
      edges.add(
          new ReactFlowEdge(
              edgeId,
              nodeIdsByWfmId.get(edge.from()),
              nodeIdsByWfmId.get(edge.to()),
              DEFAULT_EDGE_TYPE,
              label,
              edgeData(edge)));
    }
    return edges;
  }

  private Map<String, ReactFlowPosition> calculatePositions(WfmDefinition wfm, List<String> warnings) {
    Map<String, List<WfmEdge>> outgoing = new LinkedHashMap<>();
    for (WfmEdge edge : wfm.edges()) {
      outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
    }

    Map<String, Integer> levelByNodeId = new LinkedHashMap<>();
    WfmNode startNode = wfm.nodes().stream().filter((node) -> isKind(node, "start")).findFirst().orElse(wfm.nodes().get(0));
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(startNode.id());
    levelByNodeId.put(startNode.id(), 0);

    while (!queue.isEmpty()) {
      String nodeId = queue.removeFirst();
      int currentLevel = levelByNodeId.get(nodeId);
      for (WfmEdge edge : outgoing.getOrDefault(nodeId, List.of())) {
        if (!levelByNodeId.containsKey(edge.to())) {
          levelByNodeId.put(edge.to(), currentLevel + 1);
          queue.add(edge.to());
        }
      }
    }

    int unreachableLevel = levelByNodeId.values().stream().max(Comparator.naturalOrder()).orElse(0) + 1;
    for (WfmNode node : wfm.nodes()) {
      if (!levelByNodeId.containsKey(node.id())) {
        levelByNodeId.put(node.id(), unreachableLevel);
        warnings.add("Unreachable node placed separately: " + node.id());
      }
    }

    Map<Integer, List<String>> nodeIdsByLevel = new LinkedHashMap<>();
    for (WfmNode node : wfm.nodes()) {
      int level = levelByNodeId.get(node.id());
      nodeIdsByLevel.computeIfAbsent(level, ignored -> new ArrayList<>()).add(node.id());
    }

    Map<String, ReactFlowPosition> positions = new HashMap<>();
    for (Map.Entry<Integer, List<String>> entry : nodeIdsByLevel.entrySet()) {
      List<String> nodeIds = entry.getValue();
      double startY = -((nodeIds.size() - 1) * SIBLING_SPACING) / 2.0;
      for (int index = 0; index < nodeIds.size(); index++) {
        positions.put(
            nodeIds.get(index),
            new ReactFlowPosition(entry.getKey() * LEVEL_SPACING, startY + index * SIBLING_SPACING));
      }
    }
    return positions;
  }

  private Map<String, String> sanitizeNodeIds(List<WfmNode> nodes) {
    Map<String, String> ids = new LinkedHashMap<>();
    Set<String> usedIds = new HashSet<>();
    int fallbackIndex = 1;
    for (WfmNode node : nodes) {
      String base = sanitizeNodeId(node.id(), fallbackIndex);
      String candidate = base;
      int suffix = 2;
      while (usedIds.contains(candidate)) {
        candidate = base + "_" + suffix;
        suffix++;
      }
      usedIds.add(candidate);
      ids.put(node.id(), candidate);
      fallbackIndex++;
    }
    return ids;
  }

  private String sanitizeNodeId(String rawId, int fallbackIndex) {
    String sanitized = rawId == null ? "" : rawId.trim().replaceAll("\\s+", "_");
    sanitized = sanitized.replaceAll("_+", "_");
    if (sanitized.isBlank()) {
      return "node_" + fallbackIndex;
    }
    return sanitized;
  }

  private String reactFlowType(String kind) {
    return switch (normalizeKind(kind)) {
      case "start" -> "start";
      case "action" -> "action";
      case "decision" -> "decision";
      case "approval" -> "approval";
      case "end" -> "end";
      default -> "custom";
    };
  }

  private Map<String, Object> nodeData(WfmNode node) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("label", textOrFallback(node.label(), node.id()));
    data.put("kind", textOrFallback(node.kind(), "custom"));
    data.put("actor", node.actor());
    data.put("description", node.description());
    data.put("sourceWfmNodeId", node.id());
    data.put("metadata", node.metadata());
    return data;
  }

  private Map<String, Object> edgeData(WfmEdge edge) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("condition", edge.condition());
    data.put("sourceWfmEdgeId", edge.id());
    data.put("metadata", edge.metadata());
    return data;
  }

  private String edgeKey(WfmEdge edge) {
    if (!isBlank(edge.id())) {
      return edge.id();
    }
    return edge.from() + "__to__" + edge.to();
  }

  private String textOrFallback(String value, String fallback) {
    return isBlank(value) ? fallback : value.trim();
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (!isBlank(value)) {
        return value.trim();
      }
    }
    return null;
  }

  private boolean isKind(WfmNode node, String kind) {
    return normalizeKind(node.kind()).equals(kind);
  }

  private String normalizeKind(String kind) {
    return kind == null ? "" : kind.trim().toLowerCase();
  }

  private String formatWarning(WfmValidationError error) {
    return "%s %s: %s".formatted(error.path(), error.code(), error.message());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
