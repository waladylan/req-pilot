package com.reqpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.dto.TestCase;
import com.reqpilot.dto.TestCaseSuite;
import com.reqpilot.dto.TestCoverage;
import com.reqpilot.dto.TestStep;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import com.reqpilot.wfm.WfmValidationError;
import com.reqpilot.wfm.WfmValidationException;
import com.reqpilot.wfm.WfmValidationResult;
import com.reqpilot.wfm.WfmValidator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TestCaseGenerationService {

  private static final Pattern NUMERIC_CONDITION_PATTERN =
      Pattern.compile("\\b([A-Za-z][A-Za-z0-9_]*)\\s*(>=|<=|==|>|<|=)\\s*(-?\\d+)\\b");
  private static final String VERSION = "1.0";

  private final ObjectMapper objectMapper;
  private final WfmValidator wfmValidator;

  public TestCaseGenerationService(ObjectMapper objectMapper, WfmValidator wfmValidator) {
    this.objectMapper = objectMapper;
    this.wfmValidator = wfmValidator;
  }

  public TestCaseSuite generateFromWfm(WfmDefinition wfm) {
    if (wfm == null) {
      throw new IllegalArgumentException("WFM is required");
    }

    WfmValidationResult validationResult = wfmValidator.validateDefinition(wfm);
    if (!validationResult.valid()) {
      throw new WfmValidationException(validationResult.errors());
    }

    GenerationContext context = new GenerationContext(wfm);
    List<String> warnings = new ArrayList<>();
    warnings.addAll(validationResult.warnings().stream().map(this::formatWarning).toList());
    warnings.addAll(wfm.openQuestions().stream().map((question) -> "Open question requires clarification: " + question).toList());

    List<TestCase> testCases = new ArrayList<>();
    warnings.addAll(unreachableWarnings(context));
    addHappyPathCase(context, testCases, warnings);
    addApprovalApproveCases(context, testCases);
    addApprovalRejectCases(context, testCases);
    addDecisionBranchCases(context, testCases);
    addValidationCases(context, testCases);
    addEdgeCaseCases(context, testCases);

    return new TestCaseSuite(
        wfm.workflowName() + " Test Suite",
        VERSION,
        wfm.workflowName(),
        testCases,
        calculateCoverage(context, testCases),
        warnings);
  }

  public TestCaseSuite generateFromWfmJson(String wfmJson) {
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

  private void addHappyPathCase(GenerationContext context, List<TestCase> testCases, List<String> warnings) {
    Optional<PathResult> path = findHappyPath(context);
    if (path.isEmpty()) {
      warnings.add("Unable to find a happy path from start to an end node.");
      return;
    }

    PathResult result = path.get();
    List<WfmNode> stepNodes = result.nodes().stream().filter((node) -> !isKind(node, "start") && !isKind(node, "end")).toList();
    List<TestStep> steps = new ArrayList<>();
    for (int index = 0; index < stepNodes.size(); index++) {
      WfmNode node = stepNodes.get(index);
      WfmEdge incoming = result.incomingEdgeForNode(node.id()).orElse(null);
      WfmEdge outgoing = result.outgoingEdgeForNode(node.id()).orElse(null);
      WfmEdge branchContext = isKind(node, "decision") ? outgoing : incoming;
      steps.add(new TestStep(index + 1, actionForNode(node, branchContext), node.actor(), inputDataForEdge(branchContext)));
    }

    WfmNode endNode = result.nodes().get(result.nodes().size() - 1);
    testCases.add(
        newTestCase(
            testCases,
            "Happy path - " + context.wfm.workflowName(),
            "HAPPY_PATH",
            "P0",
            actorForPath(result.nodes(), context),
            defaultPreconditions(),
            steps,
            List.of("Workflow reaches the successful end state: " + endNode.label() + "."),
            result.nodes().stream().map(WfmNode::id).toList(),
            result.edges().stream().map(context::edgeKey).toList(),
            List.of("happy-path", "workflow")));
  }

  private void addApprovalApproveCases(GenerationContext context, List<TestCase> testCases) {
    for (WfmNode approvalNode : context.nodesByKind("approval")) {
      List<WfmEdge> outgoing = context.outgoingEdges(approvalNode.id());
      Optional<WfmEdge> approveEdge =
          outgoing.stream().filter(this::isApproveEdge).findFirst()
              .or(() -> outgoing.stream().filter((edge) -> !isRejectEdge(edge)).findFirst());
      if (approveEdge.isPresent()) {
        addSingleEdgeCase(
            context,
            testCases,
            "Approval approve - " + approvalNode.label(),
            "APPROVAL_APPROVE",
            "P1",
            approvalNode,
            approveEdge.get(),
            "Approve %s.".formatted(approvalNode.label()),
            "The approval action is accepted and the workflow proceeds to the next expected step.",
            List.of("approval", "approve"));
      } else {
        addNodeOnlyCase(
            testCases,
            "Approval approve - " + approvalNode.label(),
            "APPROVAL_APPROVE",
            "P1",
            approvalNode,
            "Approve %s.".formatted(approvalNode.label()),
            "The approval action is accepted and the workflow proceeds to the next expected step.",
            List.of("approval", "approve"));
      }
    }
  }

  private void addApprovalRejectCases(GenerationContext context, List<TestCase> testCases) {
    for (WfmNode approvalNode : context.nodesByKind("approval")) {
      List<WfmEdge> outgoing = context.outgoingEdges(approvalNode.id());
      outgoing.stream()
          .filter(this::isRejectEdge)
          .forEach((edge) -> addSingleEdgeCase(
              context,
              testCases,
              "Approval reject - " + approvalNode.label(),
              "APPROVAL_REJECT",
              "P1",
              approvalNode,
              edge,
              "Reject %s.".formatted(approvalNode.label()),
              "The request is rejected and the workflow reaches the rejection end state.",
              List.of("approval", "reject")));
    }
  }

  private void addDecisionBranchCases(GenerationContext context, List<TestCase> testCases) {
    for (WfmNode decisionNode : context.nodesByKind("decision")) {
      for (WfmEdge edge : context.outgoingEdges(decisionNode.id())) {
        String branch = decisionBranchLabel(context, decisionNode, edge);
        addSingleEdgeCase(
            context,
            testCases,
            "Decision branch - " + branch,
            "DECISION_BRANCH",
            "P1",
            decisionNode,
            edge,
            "Proceed with branch: " + branch,
            "The workflow follows the branch: " + branch + ".",
            List.of("decision-branch"));
      }
    }
  }

  private void addValidationCases(GenerationContext context, List<TestCase> testCases) {
    for (String validation : context.wfm.validations()) {
      if (isBlank(validation)) {
        continue;
      }
      testCases.add(
          newTestCase(
              testCases,
              "Validation - " + validation,
              "VALIDATION",
              "P1",
              defaultActor(context),
              preconditionsWith("User is on the relevant input screen."),
              List.of(
                  new TestStep(1, "Enter data that violates validation: " + validation, defaultActor(context), Map.of()),
                  new TestStep(2, "Submit the form or continue the workflow.", defaultActor(context), Map.of())),
              List.of("The system rejects invalid input and displays an appropriate validation message."),
              List.of(),
              List.of(),
              List.of("validation")));
    }
  }

  private void addEdgeCaseCases(GenerationContext context, List<TestCase> testCases) {
    for (String edgeCase : context.wfm.edgeCases()) {
      if (isBlank(edgeCase)) {
        continue;
      }
      testCases.add(
          newTestCase(
              testCases,
              "Edge case - " + edgeCase,
              "EDGE_CASE",
              "P2",
              defaultActor(context),
              defaultPreconditions(),
              List.of(new TestStep(1, "Execute edge case scenario: " + edgeCase, defaultActor(context), Map.of())),
              List.of("The system handles the edge case safely without data corruption or unexpected workflow state."),
              List.of(),
              List.of(),
              List.of("edge-case")));
    }
  }

  private void addSingleEdgeCase(
      GenerationContext context,
      List<TestCase> testCases,
      String title,
      String type,
      String priority,
      WfmNode sourceNode,
      WfmEdge edge,
      String action,
      String expectedResult,
      List<String> tags) {
    WfmNode targetNode = context.nodesById.get(edge.to());
    List<String> sourceNodeIds = new ArrayList<>();
    sourceNodeIds.add(sourceNode.id());
    if (targetNode != null) {
      sourceNodeIds.add(targetNode.id());
    }

    testCases.add(
        newTestCase(
            testCases,
            title,
            type,
            priority,
            sourceNode.actor(),
            preconditionsForType(type),
            List.of(new TestStep(1, action, sourceNode.actor(), inputDataForEdge(edge))),
            List.of(expectedResult),
            sourceNodeIds,
            List.of(context.edgeKey(edge)),
            tags));
  }

  private void addNodeOnlyCase(
      List<TestCase> testCases,
      String title,
      String type,
      String priority,
      WfmNode sourceNode,
      String action,
      String expectedResult,
      List<String> tags) {
    testCases.add(
        newTestCase(
            testCases,
            title,
            type,
            priority,
            sourceNode.actor(),
            preconditionsForType(type),
            List.of(new TestStep(1, action, sourceNode.actor(), Map.of())),
            List.of(expectedResult),
            List.of(sourceNode.id()),
            List.of(),
            tags));
  }

  private Optional<PathResult> findHappyPath(GenerationContext context) {
    WfmNode start = context.nodesByKind("start").stream().findFirst().orElse(null);
    if (start == null) {
      return Optional.empty();
    }
    WfmNode end =
        context.nodesByKind("end").stream().filter(this::isPositiveEndNode).findFirst()
            .orElseGet(() -> context.nodesByKind("end").stream().findFirst().orElse(null));
    if (end == null) {
      return Optional.empty();
    }

    ArrayDeque<PathResult> queue = new ArrayDeque<>();
    queue.add(new PathResult(List.of(start), List.of()));
    Set<String> visited = new LinkedHashSet<>();

    while (!queue.isEmpty()) {
      PathResult current = queue.removeFirst();
      WfmNode currentNode = current.nodes().get(current.nodes().size() - 1);
      if (currentNode.id().equals(end.id())) {
        return Optional.of(current);
      }
      if (!visited.add(currentNode.id())) {
        continue;
      }
      for (WfmEdge edge : context.outgoingEdges(currentNode.id())) {
        WfmNode next = context.nodesById.get(edge.to());
        if (next == null || current.containsNode(next.id())) {
          continue;
        }
        queue.add(current.append(next, edge));
      }
    }

    return Optional.empty();
  }

  private List<String> unreachableWarnings(GenerationContext context) {
    WfmNode start = context.nodesByKind("start").stream().findFirst().orElse(null);
    if (start == null) {
      return List.of();
    }

    Set<String> reachableNodeIds = new LinkedHashSet<>();
    Set<String> reachableEdgeIds = new LinkedHashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(start.id());

    while (!queue.isEmpty()) {
      String nodeId = queue.removeFirst();
      if (!reachableNodeIds.add(nodeId)) {
        continue;
      }
      for (WfmEdge edge : context.outgoingEdges(nodeId)) {
        reachableEdgeIds.add(context.edgeKey(edge));
        if (!reachableNodeIds.contains(edge.to())) {
          queue.add(edge.to());
        }
      }
    }

    List<String> warnings = new ArrayList<>();
    context.nodesById.keySet().stream()
        .filter((nodeId) -> !reachableNodeIds.contains(nodeId))
        .map((nodeId) -> "Unreachable node from start: " + nodeId)
        .forEach(warnings::add);
    context.edgeKeys.stream()
        .filter((edgeId) -> !reachableEdgeIds.contains(edgeId))
        .map((edgeId) -> "Unreachable edge from start: " + edgeId)
        .forEach(warnings::add);
    return warnings;
  }

  private TestCase newTestCase(
      List<TestCase> existingCases,
      String title,
      String type,
      String priority,
      String actor,
      List<String> preconditions,
      List<TestStep> steps,
      List<String> expectedResults,
      List<String> sourceNodeIds,
      List<String> sourceEdgeIds,
      List<String> tags) {
    return new TestCase(
        "TC-%03d".formatted(existingCases.size() + 1),
        title,
        type,
        priority,
        normalizeOptional(actor),
        preconditions,
        steps,
        expectedResults,
        distinct(sourceNodeIds),
        distinct(sourceEdgeIds),
        tags);
  }

  private TestCoverage calculateCoverage(GenerationContext context, List<TestCase> testCases) {
    Set<String> allNodeIds = new LinkedHashSet<>(context.nodesById.keySet());
    Set<String> allEdgeIds = new LinkedHashSet<>(context.edgeKeys);
    Set<String> coveredNodeIdSet = new LinkedHashSet<>();
    Set<String> coveredEdgeIdSet = new LinkedHashSet<>();
    for (TestCase testCase : testCases) {
      coveredNodeIdSet.addAll(testCase.sourceNodeIds());
      coveredEdgeIdSet.addAll(testCase.sourceEdgeIds());
    }
    List<String> coveredNodeIds = filterByOrder(allNodeIds, coveredNodeIdSet);
    List<String> coveredEdgeIds = filterByOrder(allEdgeIds, coveredEdgeIdSet);

    Set<String> uncoveredNodeIds = new LinkedHashSet<>(allNodeIds);
    uncoveredNodeIds.removeAll(coveredNodeIdSet);
    Set<String> uncoveredEdgeIds = new LinkedHashSet<>(allEdgeIds);
    uncoveredEdgeIds.removeAll(coveredEdgeIdSet);

    return new TestCoverage(
        allNodeIds.size(),
        allEdgeIds.size(),
        coveredNodeIds,
        coveredEdgeIds,
        List.copyOf(uncoveredNodeIds),
        List.copyOf(uncoveredEdgeIds));
  }

  private Map<String, Object> inputDataForEdge(WfmEdge edge) {
    if (edge == null) {
      return Map.of();
    }
    String condition = firstNonBlank(edge.condition(), edge.label());
    if (condition == null) {
      return Map.of();
    }

    Matcher matcher = NUMERIC_CONDITION_PATTERN.matcher(condition);
    if (!matcher.find()) {
      return Map.of();
    }

    String field = matcher.group(1);
    String operator = matcher.group(2);
    int value = Integer.parseInt(matcher.group(3));
    return switch (operator) {
      case ">" -> Map.of(field, value + 1);
      case "<" -> Map.of(field, value - 1);
      case ">=", "<=", "=", "==" -> Map.of(field, value);
      default -> Map.of();
    };
  }

  private String actionForNode(WfmNode node, WfmEdge incoming) {
    String action = node.label();
    String branch = incoming == null ? null : firstNonBlank(incoming.condition(), incoming.label());
    if (isKind(node, "decision") && !isBlank(branch)) {
      return "Proceed with branch: " + branch;
    }
    if (!isBlank(branch)) {
      return action + " when " + branch;
    }
    return action;
  }

  private boolean isPositiveEndNode(WfmNode node) {
    String normalized = normalize(node.label() + " " + node.kind());
    return containsAny(normalized, "approved", "success", "completed", "created", "saved", "submitted", "done");
  }

  private boolean isApproveEdge(WfmEdge edge) {
    String normalized = normalize(branchLabel(edge));
    return containsAny(normalized, "approve", "approved", "accept", "accepted", "yes", "success");
  }

  private boolean isRejectEdge(WfmEdge edge) {
    String normalized = normalize(branchLabel(edge));
    return containsAny(normalized, "reject", "rejected", "denied", "deny", "decline", "declined", "fail", "failed");
  }

  private String branchLabel(WfmEdge edge) {
    return firstNonBlank(edge.condition(), edge.label(), edge.id(), edge.from() + " -> " + edge.to());
  }

  private String decisionBranchLabel(GenerationContext context, WfmNode decisionNode, WfmEdge edge) {
    String branch = firstNonBlank(edge.condition(), edge.label());
    if (!isBlank(branch)) {
      return branch;
    }
    WfmNode target = context.nodesById.get(edge.to());
    String targetLabel = target == null ? edge.to() : target.label();
    return "Branch from " + decisionNode.label() + " to " + targetLabel;
  }

  private String actorForPath(List<WfmNode> nodes, GenerationContext context) {
    return nodes.stream().map(WfmNode::actor).filter((actor) -> !isBlank(actor)).findFirst().orElse(defaultActor(context));
  }

  private String defaultActor(GenerationContext context) {
    return context.wfm.actors().stream().filter((actor) -> !isBlank(actor)).findFirst().orElse(null);
  }

  private List<String> defaultPreconditions() {
    return List.of(
        "User is logged in.",
        "Required roles and permissions are configured.",
        "Workflow configuration is available.");
  }

  private List<String> preconditionsForType(String type) {
    if ("APPROVAL_APPROVE".equals(type) || "APPROVAL_REJECT".equals(type)) {
      return preconditionsWith("The request is submitted and waiting for approval.");
    }
    return defaultPreconditions();
  }

  private List<String> preconditionsWith(String precondition) {
    List<String> preconditions = new ArrayList<>(defaultPreconditions());
    preconditions.add(precondition);
    return preconditions;
  }

  private List<String> distinct(List<String> values) {
    return values.stream().filter((value) -> !isBlank(value)).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)).stream().toList();
  }

  private List<String> filterByOrder(Set<String> orderedValues, Set<String> includedValues) {
    return orderedValues.stream().filter(includedValues::contains).toList();
  }

  private String formatWarning(WfmValidationError error) {
    return "%s %s: %s".formatted(error.path(), error.code(), error.message());
  }

  private boolean isKind(WfmNode node, String kind) {
    return normalizeKind(node.kind()).equals(kind);
  }

  private String normalizeKind(String kind) {
    return kind == null ? "" : kind.trim().toLowerCase();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (!isBlank(value)) {
        return value.trim();
      }
    }
    return null;
  }

  private String normalizeOptional(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record PathResult(List<WfmNode> nodes, List<WfmEdge> edges) {

    private PathResult append(WfmNode node, WfmEdge edge) {
      List<WfmNode> nextNodes = new ArrayList<>(nodes);
      nextNodes.add(node);
      List<WfmEdge> nextEdges = new ArrayList<>(edges);
      nextEdges.add(edge);
      return new PathResult(nextNodes, nextEdges);
    }

    private boolean containsNode(String nodeId) {
      return nodes.stream().anyMatch((node) -> node.id().equals(nodeId));
    }

    private Optional<WfmEdge> incomingEdgeForNode(String nodeId) {
      for (int index = 1; index < nodes.size(); index++) {
        if (nodes.get(index).id().equals(nodeId)) {
          return Optional.of(edges.get(index - 1));
        }
      }
      return Optional.empty();
    }

    private Optional<WfmEdge> outgoingEdgeForNode(String nodeId) {
      for (int index = 0; index < nodes.size() - 1; index++) {
        if (nodes.get(index).id().equals(nodeId)) {
          return Optional.of(edges.get(index));
        }
      }
      return Optional.empty();
    }
  }

  private static final class GenerationContext {

    private final WfmDefinition wfm;
    private final Map<String, WfmNode> nodesById;
    private final Map<String, List<WfmEdge>> outgoingEdges;
    private final List<String> edgeKeys;

    private GenerationContext(WfmDefinition wfm) {
      this.wfm = wfm;
      this.nodesById = new LinkedHashMap<>();
      this.outgoingEdges = new LinkedHashMap<>();
      this.edgeKeys = new ArrayList<>();
      for (WfmNode node : wfm.nodes()) {
        nodesById.put(node.id(), node);
      }
      for (WfmEdge edge : wfm.edges()) {
        outgoingEdges.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        edgeKeys.add(edgeKey(edge));
      }
    }

    private List<WfmNode> nodesByKind(String kind) {
      return nodesById.values().stream()
          .filter((node) -> node.kind() != null && node.kind().trim().equalsIgnoreCase(kind))
          .toList();
    }

    private List<WfmEdge> outgoingEdges(String nodeId) {
      return outgoingEdges.getOrDefault(nodeId, List.of());
    }

    private String edgeKey(WfmEdge edge) {
      if (edge.id() != null && !edge.id().isBlank()) {
        return edge.id();
      }
      return "edge_" + edge.from() + "_" + edge.to();
    }
  }
}
