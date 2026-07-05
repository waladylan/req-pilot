package com.reqpilot.wfm;

import com.reqpilot.ai.AiErrorType;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WfmValidator {

  private static final Set<String> UI_ONLY_FIELDS =
      Set.of(
          "x",
          "y",
          "position",
          "color",
          "shape",
          "width",
          "height",
          "selected",
          "dragging",
          "sourceHandle",
          "targetHandle",
          "reactFlowType",
          "edgeLabel",
          "style",
          "className");

  public WfmValidationResult validate(WfmDocument document) {
    List<WfmValidationError> errors = new ArrayList<>();

    if (document == null) {
      add(errors, WfmValidationSeverity.ERROR, "REQUIRED", "$", "WFM document is required");
      return new WfmValidationResult(errors);
    }

    validateTopLevel(document, errors);

    if (document.workflow() == null) {
      add(errors, WfmValidationSeverity.ERROR, "WORKFLOW_REQUIRED", "$.workflow", "workflow is required");
    } else {
      if (isBlank(document.workflow().id())) {
        add(errors, WfmValidationSeverity.ERROR, "WORKFLOW_ID_REQUIRED", "$.workflow.id", "workflow.id is required");
      }
      if (isBlank(document.workflow().title())) {
        add(
            errors,
            WfmValidationSeverity.ERROR,
            "WORKFLOW_TITLE_REQUIRED",
            "$.workflow.title",
            "workflow.title is required");
      }
    }

    if (document.ast() == null) {
      add(errors, WfmValidationSeverity.ERROR, "AST_REQUIRED", "$.ast", "ast is required");
      return new WfmValidationResult(errors);
    }

    validateNodesAndTransitions(document, errors);
    validateExtensions(document, errors);

    return new WfmValidationResult(errors);
  }

  public WfmDocument validateOrThrow(WfmDocument document) {
    WfmValidationResult result = validate(document);
    if (!result.valid()) {
      throw new WfmValidationException(result.errors());
    }
    return document;
  }

  public WfmValidationResult validateDefinition(WfmDefinition definition) {
    List<WfmValidationError> errors = new ArrayList<>();

    if (definition == null) {
      add(errors, WfmValidationSeverity.ERROR, "REQUIRED", "$", "WFM definition is required");
      return new WfmValidationResult(errors);
    }

    if (isBlank(definition.workflowName())) {
      add(errors, WfmValidationSeverity.ERROR, "WORKFLOW_NAME_REQUIRED", "$.workflowName", "workflowName is required");
    }
    if (isBlank(definition.version())) {
      add(errors, WfmValidationSeverity.ERROR, "VERSION_REQUIRED", "$.version", "version is required");
    }
    if (definition.actors() == null) {
      add(errors, WfmValidationSeverity.ERROR, "ACTORS_REQUIRED", "$.actors", "actors must not be null");
    }
    if (definition.nodes() == null || definition.nodes().isEmpty()) {
      add(errors, WfmValidationSeverity.ERROR, "NODES_REQUIRED", "$.nodes", "nodes must not be empty");
      return new WfmValidationResult(errors);
    }
    if (definition.edges() == null) {
      add(errors, WfmValidationSeverity.ERROR, "EDGES_REQUIRED", "$.edges", "edges must not be null");
      return new WfmValidationResult(errors);
    }

    validateDefinitionNodesAndEdges(definition, errors);
    return new WfmValidationResult(errors);
  }

  public WfmDefinition validateDefinitionOrThrow(WfmDefinition definition, String provider) {
    WfmValidationResult result = validateDefinition(definition);
    if (!result.valid()) {
      String details =
          result.errors().stream()
              .map((error) -> "%s %s: %s".formatted(error.path(), error.code(), error.message()))
              .toList()
              .toString();
      throw new AiProviderException(
          AiErrorType.INVALID_RESPONSE,
          provider,
          "AI response WFM is invalid: " + details);
    }
    return definition;
  }

  private void validateTopLevel(WfmDocument document, List<WfmValidationError> errors) {
    if (!"1.0".equals(document.schemaVersion())) {
      add(errors, WfmValidationSeverity.ERROR, "SCHEMA_VERSION", "$.schemaVersion", "schemaVersion must be 1.0");
    }
    if (!"WORKFLOW_AST".equals(document.modelType())) {
      add(errors, WfmValidationSeverity.ERROR, "MODEL_TYPE", "$.modelType", "modelType must be WORKFLOW_AST");
    }
  }

  private void validateNodesAndTransitions(WfmDocument document, List<WfmValidationError> errors) {
    WfmAst ast = document.ast();
    List<WfmNode> nodes = ast.nodes();
    List<WfmTransition> transitions = ast.transitions();

    if (nodes.isEmpty()) {
      add(errors, WfmValidationSeverity.ERROR, "NODES_REQUIRED", "$.ast.nodes", "ast.nodes must not be empty");
      return;
    }

    Map<String, WfmNode> nodesById = new HashMap<>();
    Map<WfmNodeRole, Integer> roleCounts = new HashMap<>();
    Set<String> actorIds = new HashSet<>();
    ast.actors().forEach((actor) -> actorIds.add(actor.id()));

    for (int index = 0; index < nodes.size(); index++) {
      WfmNode node = nodes.get(index);
      String path = "$.ast.nodes[" + index + "]";
      if (isBlank(node.id())) {
        add(errors, WfmValidationSeverity.ERROR, "NODE_ID_REQUIRED", path + ".id", "node id is required");
      } else if (nodesById.put(node.id(), node) != null) {
        add(errors, WfmValidationSeverity.ERROR, "DUPLICATE_NODE_ID", path + ".id", "node id must be unique");
      }
      if (node.role() == null) {
        add(errors, WfmValidationSeverity.ERROR, "NODE_ROLE_REQUIRED", path + ".role", "node role is required");
      } else {
        roleCounts.merge(node.role(), 1, Integer::sum);
      }
      if (isBlank(node.title())) {
        add(errors, WfmValidationSeverity.ERROR, "NODE_TITLE_REQUIRED", path + ".title", "node title is required");
      }
      if (!actorIds.isEmpty() && !isBlank(node.actorId()) && !actorIds.contains(node.actorId())) {
        add(
            errors,
            WfmValidationSeverity.WARNING,
            "UNKNOWN_ACTOR",
            path + ".actorId",
            "node actorId should reference an existing actor");
      }
      rejectUiFields(node.data(), path + ".data", errors);
    }

    if (roleCounts.getOrDefault(WfmNodeRole.START, 0) != 1) {
      add(
          errors,
          WfmValidationSeverity.ERROR,
          "START_COUNT",
          "$.ast.nodes",
          "exactly one START role node is required");
    }
    if (roleCounts.getOrDefault(WfmNodeRole.END, 0) < 1) {
      add(errors, WfmValidationSeverity.ERROR, "END_REQUIRED", "$.ast.nodes", "at least one END role node is required");
    }

    validateTransitions(transitions, nodesById, errors);
    validateReachability(nodes, transitions, errors);
  }

  private void validateTransitions(
      List<WfmTransition> transitions, Map<String, WfmNode> nodesById, List<WfmValidationError> errors) {
    Set<String> transitionIds = new HashSet<>();
    Map<String, Integer> incomingCounts = new HashMap<>();
    Map<String, Integer> outgoingCounts = new HashMap<>();

    for (int index = 0; index < transitions.size(); index++) {
      WfmTransition transition = transitions.get(index);
      String path = "$.ast.transitions[" + index + "]";

      if (isBlank(transition.id())) {
        add(errors, WfmValidationSeverity.ERROR, "TRANSITION_ID_REQUIRED", path + ".id", "transition id is required");
      } else if (!transitionIds.add(transition.id())) {
        add(
            errors,
            WfmValidationSeverity.ERROR,
            "DUPLICATE_TRANSITION_ID",
            path + ".id",
            "transition id must be unique");
      }

      if (transition.semantic() == null) {
        add(errors, WfmValidationSeverity.ERROR, "SEMANTIC_REQUIRED", path + ".semantic", "semantic is required");
      }

      WfmNode from = nodesById.get(transition.from());
      WfmNode to = nodesById.get(transition.to());
      if (from == null) {
        add(errors, WfmValidationSeverity.ERROR, "INVALID_FROM", path + ".from", "transition.from must reference a node");
      } else {
        outgoingCounts.merge(from.id(), 1, Integer::sum);
      }
      if (to == null) {
        add(errors, WfmValidationSeverity.ERROR, "INVALID_TO", path + ".to", "transition.to must reference a node");
      } else {
        incomingCounts.merge(to.id(), 1, Integer::sum);
      }
      rejectUiFields(transition.data(), path + ".data", errors);
    }

    nodesById.values().forEach((node) -> {
      if (node.role() == WfmNodeRole.START && incomingCounts.getOrDefault(node.id(), 0) > 0) {
        add(
            errors,
            WfmValidationSeverity.ERROR,
            "START_INCOMING",
            "$.ast.nodes." + node.id(),
            "START should not have incoming transitions");
      }
      if (node.role() == WfmNodeRole.END && outgoingCounts.getOrDefault(node.id(), 0) > 0) {
        add(
            errors,
            WfmValidationSeverity.ERROR,
            "END_OUTGOING",
            "$.ast.nodes." + node.id(),
            "END should not have outgoing transitions");
      }
      if (node.role() == WfmNodeRole.DECISION && outgoingCounts.getOrDefault(node.id(), 0) == 1) {
        add(
            errors,
            WfmValidationSeverity.WARNING,
            "DECISION_BRANCH_COUNT",
            "$.ast.nodes." + node.id(),
            "DECISION nodes should have at least two outgoing transitions when possible");
      }
    });
  }

  private void validateReachability(
      List<WfmNode> nodes, List<WfmTransition> transitions, List<WfmValidationError> errors) {
    WfmNode startNode =
        nodes.stream().filter((node) -> node.role() == WfmNodeRole.START).findFirst().orElse(null);
    if (startNode == null) {
      return;
    }

    Map<String, List<String>> outgoing = new HashMap<>();
    transitions.forEach((transition) -> outgoing.computeIfAbsent(transition.from(), ignored -> new ArrayList<>()).add(transition.to()));

    Set<String> reachable = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(startNode.id());
    while (!queue.isEmpty()) {
      String nodeId = queue.removeFirst();
      if (!reachable.add(nodeId)) {
        continue;
      }
      outgoing.getOrDefault(nodeId, List.of()).forEach(queue::addLast);
    }

    nodes.stream()
        .filter((node) -> node.role() != WfmNodeRole.START)
        .filter((node) -> !reachable.contains(node.id()))
        .forEach(
            (node) ->
                add(
                    errors,
                    WfmValidationSeverity.ERROR,
                    "UNREACHABLE_NODE",
                    "$.ast.nodes." + node.id(),
                    "every non-START node should be reachable from START"));
  }

  private void validateExtensions(WfmDocument document, List<WfmValidationError> errors) {
    if (document.extensions() == null) {
      return;
    }

    for (int index = 0; index < document.extensions().nodeKinds().size(); index++) {
      WfmNodeKindDefinition definition = document.extensions().nodeKinds().get(index);
      if (definition.extendsRole() == null) {
        add(
            errors,
            WfmValidationSeverity.ERROR,
            "EXTENSION_NODE_ROLE",
            "$.extensions.nodeKinds[" + index + "].extendsRole",
            "custom node kind extendsRole must be a valid role");
      }
    }

    for (int index = 0; index < document.extensions().transitionKinds().size(); index++) {
      WfmTransitionKindDefinition definition = document.extensions().transitionKinds().get(index);
      if (definition.extendsSemantic() == null) {
        add(
            errors,
            WfmValidationSeverity.ERROR,
            "EXTENSION_TRANSITION_SEMANTIC",
            "$.extensions.transitionKinds[" + index + "].extendsSemantic",
            "custom transition kind extendsSemantic must be a valid semantic");
      }
    }
  }

  private void validateDefinitionNodesAndEdges(WfmDefinition definition, List<WfmValidationError> errors) {
    Map<String, com.reqpilot.dto.WfmNode> nodesById = new HashMap<>();
    Map<String, Integer> kindCounts = new HashMap<>();

    for (int index = 0; index < definition.nodes().size(); index++) {
      com.reqpilot.dto.WfmNode node = definition.nodes().get(index);
      String path = "$.nodes[" + index + "]";
      if (node == null) {
        add(errors, WfmValidationSeverity.ERROR, "NODE_REQUIRED", path, "node is required");
        continue;
      }
      if (isBlank(node.id())) {
        add(errors, WfmValidationSeverity.ERROR, "NODE_ID_REQUIRED", path + ".id", "node id is required");
      } else if (nodesById.put(node.id(), node) != null) {
        add(errors, WfmValidationSeverity.ERROR, "DUPLICATE_NODE_ID", path + ".id", "node id must be unique");
      }
      if (isBlank(node.kind())) {
        add(errors, WfmValidationSeverity.ERROR, "NODE_KIND_REQUIRED", path + ".kind", "node kind is required");
      } else {
        kindCounts.merge(normalizeKind(node.kind()), 1, Integer::sum);
      }
      if (isBlank(node.label())) {
        add(errors, WfmValidationSeverity.ERROR, "NODE_LABEL_REQUIRED", path + ".label", "node label is required");
      }
      if ("approval".equals(normalizeKind(node.kind())) && isBlank(node.actor())) {
        add(
            errors,
            WfmValidationSeverity.WARNING,
            "APPROVAL_ACTOR_RECOMMENDED",
            path + ".actor",
            "approval nodes should have an actor when possible");
      }
      rejectUiFields(node.metadata(), path + ".metadata", errors);
    }

    if (kindCounts.getOrDefault("start", 0) != 1) {
      add(errors, WfmValidationSeverity.ERROR, "START_COUNT", "$.nodes", "exactly one start node is required");
    }
    if (kindCounts.getOrDefault("end", 0) < 1) {
      add(errors, WfmValidationSeverity.ERROR, "END_REQUIRED", "$.nodes", "at least one end node is required");
    }

    validateDefinitionEdges(definition.edges(), nodesById, errors);
  }

  private void validateDefinitionEdges(
      List<WfmEdge> edges, Map<String, com.reqpilot.dto.WfmNode> nodesById, List<WfmValidationError> errors) {
    Set<String> edgeIds = new HashSet<>();
    Map<String, List<WfmEdge>> outgoingEdges = new HashMap<>();

    for (int index = 0; index < edges.size(); index++) {
      WfmEdge edge = edges.get(index);
      String path = "$.edges[" + index + "]";
      if (edge == null) {
        add(errors, WfmValidationSeverity.ERROR, "EDGE_REQUIRED", path, "edge is required");
        continue;
      }
      if (!isBlank(edge.id()) && !edgeIds.add(edge.id())) {
        add(errors, WfmValidationSeverity.ERROR, "DUPLICATE_EDGE_ID", path + ".id", "edge id must be unique");
      }
      if (isBlank(edge.from())) {
        add(errors, WfmValidationSeverity.ERROR, "EDGE_FROM_REQUIRED", path + ".from", "edge.from is required");
      } else if (!nodesById.containsKey(edge.from())) {
        add(errors, WfmValidationSeverity.ERROR, "INVALID_FROM", path + ".from", "edge.from must reference a node");
      } else {
        outgoingEdges.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
      }
      if (isBlank(edge.to())) {
        add(errors, WfmValidationSeverity.ERROR, "EDGE_TO_REQUIRED", path + ".to", "edge.to is required");
      } else if (!nodesById.containsKey(edge.to())) {
        add(errors, WfmValidationSeverity.ERROR, "INVALID_TO", path + ".to", "edge.to must reference a node");
      }
      rejectUiFields(edge.metadata(), path + ".metadata", errors);
    }

    nodesById.values().forEach((node) -> validateDefinitionBranching(node, outgoingEdges, errors));
  }

  private void validateDefinitionBranching(
      com.reqpilot.dto.WfmNode node, Map<String, List<WfmEdge>> outgoingEdges, List<WfmValidationError> errors) {
    String kind = normalizeKind(node.kind());
    List<WfmEdge> outgoing = outgoingEdges.getOrDefault(node.id(), List.of());
    if ("decision".equals(kind) && outgoing.size() == 1) {
      add(
          errors,
          WfmValidationSeverity.WARNING,
          "DECISION_BRANCH_COUNT",
          "$.nodes." + node.id(),
          "decision nodes should have at least two outgoing edges when possible");
    }
    if ("approval".equals(kind) && outgoing.size() == 1) {
      add(
          errors,
          WfmValidationSeverity.WARNING,
          "APPROVAL_BRANCH_COUNT",
          "$.nodes." + node.id(),
          "approval nodes should normally have both approve and reject outgoing paths");
    }
    if ("decision".equals(kind)) {
      outgoing.stream()
          .filter((edge) -> isBlank(edge.condition()) && isBlank(edge.label()))
          .forEach(
              (edge) ->
                  add(
                      errors,
                      WfmValidationSeverity.WARNING,
                      "DECISION_EDGE_CONDITION",
                      "$.edges." + (isBlank(edge.id()) ? edge.from() + "_" + edge.to() : edge.id()),
                      "outgoing edges from decision nodes should have condition or label"));
    }
  }

  private void rejectUiFields(Object value, String path, List<WfmValidationError> errors) {
    if (value instanceof Map<?, ?> map) {
      map.forEach((key, nestedValue) -> {
        String keyValue = String.valueOf(key);
        String nestedPath = path + "." + keyValue;
        if (UI_ONLY_FIELDS.contains(keyValue)) {
          add(errors, WfmValidationSeverity.ERROR, "UI_FIELD", nestedPath, "WFM must not contain UI-only fields");
        }
        rejectUiFields(nestedValue, nestedPath, errors);
      });
      return;
    }

    if (value instanceof Iterable<?> iterable) {
      int index = 0;
      for (Object nestedValue : iterable) {
        rejectUiFields(nestedValue, path + "[" + index + "]", errors);
        index++;
      }
    }
  }

  private void add(
      List<WfmValidationError> errors, WfmValidationSeverity severity, String code, String path, String message) {
    errors.add(new WfmValidationError(severity, code, path, message));
  }

  private String normalizeKind(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
