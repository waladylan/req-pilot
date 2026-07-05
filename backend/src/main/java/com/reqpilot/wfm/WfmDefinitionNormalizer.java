package com.reqpilot.wfm;

import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WfmDefinitionNormalizer {

  private static final Set<String> KNOWN_KINDS =
      Set.of("start", "action", "decision", "approval", "end");

  public WfmNormalizationResult normalize(WfmDefinition wfm) {
    if (wfm == null) {
      throw new IllegalArgumentException("WFM definition is required");
    }

    List<String> repairs = new ArrayList<>();
    List<WfmNode> normalizedNodes = normalizeNodes(wfm.nodes());
    Map<String, WfmNode> nodesById = toNodeMap(normalizedNodes);
    List<WfmEdge> normalizedEdges = normalizeEdges(wfm.edges(), nodesById);
    List<ApprovalRepairPlan> repairPlans = buildApprovalRepairPlans(normalizedNodes, normalizedEdges, repairs);
    List<WfmNode> repairedNodes = insertDecisionNodes(normalizedNodes, repairPlans);
    List<WfmEdge> repairedEdges = rebuildEdges(repairedNodes, normalizedEdges, repairPlans);

    WfmDefinition normalized =
        new WfmDefinition(
            readableWorkflowName(wfm.workflowName()),
            blankToDefault(wfm.version(), "1.0"),
            trimToNull(wfm.summary()),
            wfm.actors(),
            repairedNodes,
            repairedEdges,
            wfm.businessRules(),
            wfm.validations(),
            wfm.assumptions(),
            wfm.edgeCases(),
            wfm.openQuestions(),
            blankToDefault(wfm.riskLevel(), "MEDIUM"));

    return new WfmNormalizationResult(
        normalized,
        new WfmQualityReport(true, false, List.of(), List.of(), repairs));
  }

  private List<WfmNode> normalizeNodes(List<WfmNode> nodes) {
    List<WfmNode> normalized = new ArrayList<>();
    Set<String> usedIds = new LinkedHashSet<>();
    int index = 1;

    for (WfmNode node : nodes) {
      String id = trimToNull(node.id());
      if (id == null || usedIds.contains(id)) {
        id = uniqueId("node_" + index, usedIds);
      }
      usedIds.add(id);

      String kind = normalizeKind(node.kind());
      String label = blankToDefault(node.label(), labelFromId(id));
      normalized.add(
          new WfmNode(
              id,
              kind,
              label,
              trimToNull(node.actor()),
              trimToNull(node.description()),
              metadataOrEmpty(node.metadata())));
      index++;
    }

    return List.copyOf(normalized);
  }

  private List<WfmEdge> normalizeEdges(List<WfmEdge> edges, Map<String, WfmNode> nodesById) {
    List<WfmEdge> normalized = new ArrayList<>();
    Set<String> usedIds = new LinkedHashSet<>();
    int index = 1;

    for (WfmEdge edge : edges) {
      String from = trimToNull(edge.from());
      String to = trimToNull(edge.to());
      String id = trimToNull(edge.id());
      if (id == null || usedIds.contains(id)) {
        id = uniqueId(defaultEdgeId(from, to, index), usedIds);
      }
      usedIds.add(id);

      String label = trimToNull(edge.label());
      String condition = trimToNull(edge.condition());
      if (condition != null && label == null) {
        label = condition;
      }

      WfmNode source = nodesById.get(from);
      if (source != null && "approval".equals(normalizeKind(source.kind()))) {
        ApprovalOutcome outcome = approvalOutcome(label, condition);
        if (outcome == ApprovalOutcome.APPROVE) {
          label = "Approve";
          condition = condition == null ? approvalCondition(source, true) : condition;
        } else if (outcome == ApprovalOutcome.REJECT) {
          label = "Reject";
          condition = condition == null ? approvalCondition(source, false) : condition;
        }
      }

      normalized.add(new WfmEdge(id, from, to, label, condition, metadataOrEmpty(edge.metadata())));
      index++;
    }

    return List.copyOf(normalized);
  }

  private List<ApprovalRepairPlan> buildApprovalRepairPlans(
      List<WfmNode> nodes, List<WfmEdge> edges, List<String> repairs) {
    Map<String, List<WfmEdge>> outgoing =
        edges.stream().collect(Collectors.groupingBy(WfmEdge::from, LinkedHashMap::new, Collectors.toList()));
    Map<String, WfmNode> nodesById = toNodeMap(nodes);
    Set<String> existingEdgeIds = edges.stream().map(WfmEdge::id).collect(Collectors.toCollection(LinkedHashSet::new));
    List<ApprovalRepairPlan> plans = new ArrayList<>();

    for (WfmNode approvalNode : nodes) {
      if (!"approval".equals(normalizeKind(approvalNode.kind()))) {
        continue;
      }

      List<WfmEdge> outgoingEdges = outgoing.getOrDefault(approvalNode.id(), List.of());
      List<WfmEdge> numericConditionEdges =
          outgoingEdges.stream().filter(this::hasNumericBusinessCondition).toList();
      if (numericConditionEdges.isEmpty()) {
        continue;
      }

      String fieldName =
          numericConditionEdges.stream()
              .map(this::numericConditionField)
              .flatMap(Optional::stream)
              .findFirst()
              .orElse("condition");
      WfmNode decisionNode = findOrCreateDecisionNode(nodes, fieldName);
      boolean decisionCreated = !nodesById.containsKey(decisionNode.id());
      WfmEdge approveEdge = existingApprovalToDecisionEdge(outgoingEdges, decisionNode.id())
          .orElseGet(() -> createApproveEdge(approvalNode, decisionNode, existingEdgeIds));
      existingEdgeIds.add(approveEdge.id());
      List<WfmEdge> movedEdges =
          numericConditionEdges.stream().map((edge) -> moveConditionEdgeToDecision(edge, decisionNode.id())).toList();

      plans.add(new ApprovalRepairPlan(approvalNode.id(), decisionNode, decisionCreated, approveEdge, movedEdges));
      repairs.add(
          "Moved amount condition branches from approval node '%s' to decision node '%s'."
              .formatted(approvalNode.id(), decisionNode.id()));
    }

    return List.copyOf(plans);
  }

  private Optional<WfmEdge> existingApprovalToDecisionEdge(List<WfmEdge> outgoingEdges, String decisionNodeId) {
    return outgoingEdges.stream()
        .filter((edge) -> decisionNodeId.equals(edge.to()))
        .filter(WfmSemanticValidator::isApproveLike)
        .findFirst();
  }

  private WfmEdge createApproveEdge(WfmNode approvalNode, WfmNode decisionNode, Set<String> existingEdgeIds) {
    String edgeId = uniqueId("edge_" + approvalNode.id() + "_" + decisionNode.id(), existingEdgeIds);
    return new WfmEdge(
        edgeId,
        approvalNode.id(),
        decisionNode.id(),
        "Approve",
        approvalCondition(approvalNode, true),
        Map.of());
  }

  private WfmEdge moveConditionEdgeToDecision(WfmEdge edge, String decisionNodeId) {
    String condition = firstNonBlank(edge.condition(), edge.label());
    return new WfmEdge(
        edge.id(),
        decisionNodeId,
        edge.to(),
        blankToDefault(edge.label(), condition),
        blankToDefault(edge.condition(), condition),
        metadataOrEmpty(edge.metadata()));
  }

  private WfmNode findOrCreateDecisionNode(List<WfmNode> nodes, String fieldName) {
    String fieldSnake = WfmConditionClassifier.toSnakeCase(fieldName);
    String expectedId = "check_" + fieldSnake;
    String fieldTitle = WfmConditionClassifier.toTitle(fieldName);

    Optional<WfmNode> existing =
        nodes.stream()
            .filter((node) -> "decision".equals(normalizeKind(node.kind())))
            .filter(
                (node) ->
                    expectedId.equals(node.id())
                        || normalizeText(node.label()).contains(normalizeText(fieldTitle)))
            .findFirst();
    if (existing.isPresent()) {
      return existing.get();
    }

    Set<String> usedIds = nodes.stream().map(WfmNode::id).collect(Collectors.toSet());
    String decisionId = uniqueId(expectedId, usedIds);
    return new WfmNode(
        decisionId,
        "decision",
        "Check " + fieldTitle,
        null,
        "Check " + fieldSnake.replace('_', ' ') + " condition.",
        Map.of());
  }

  private List<WfmNode> insertDecisionNodes(List<WfmNode> nodes, List<ApprovalRepairPlan> repairPlans) {
    Map<String, List<WfmNode>> createdDecisionNodesByApproval = new LinkedHashMap<>();
    for (ApprovalRepairPlan plan : repairPlans) {
      if (plan.decisionCreated()) {
        createdDecisionNodesByApproval
            .computeIfAbsent(plan.approvalNodeId(), ignored -> new ArrayList<>())
            .add(plan.decisionNode());
      }
    }

    List<WfmNode> repairedNodes = new ArrayList<>();
    Set<String> emittedNodeIds = new HashSet<>();
    for (WfmNode node : nodes) {
      if (emittedNodeIds.add(node.id())) {
        repairedNodes.add(node);
      }
      for (WfmNode decisionNode : createdDecisionNodesByApproval.getOrDefault(node.id(), List.of())) {
        if (emittedNodeIds.add(decisionNode.id())) {
          repairedNodes.add(decisionNode);
        }
      }
    }

    return List.copyOf(repairedNodes);
  }

  private List<WfmEdge> rebuildEdges(
      List<WfmNode> nodes, List<WfmEdge> normalizedEdges, List<ApprovalRepairPlan> repairPlans) {
    Map<String, ApprovalRepairPlan> repairPlanByApprovalId =
        repairPlans.stream()
            .collect(Collectors.toMap(ApprovalRepairPlan::approvalNodeId, Function.identity(), (left, right) -> left));
    Map<String, List<WfmEdge>> outgoing =
        normalizedEdges.stream().collect(Collectors.groupingBy(WfmEdge::from, LinkedHashMap::new, Collectors.toList()));
    Set<String> repairedApprovalIds = repairPlanByApprovalId.keySet();
    List<WfmEdge> repairedEdges = new ArrayList<>();
    Set<String> emittedEdgeIds = new LinkedHashSet<>();
    Set<String> emittedEdgeKeys = new LinkedHashSet<>();

    for (WfmNode node : nodes) {
      ApprovalRepairPlan plan = repairPlanByApprovalId.get(node.id());
      if (plan != null) {
        addEdgeIfUnique(plan.approveEdge(), repairedEdges, emittedEdgeIds, emittedEdgeKeys);

        List<WfmEdge> nonMoved =
            outgoing.getOrDefault(node.id(), List.of()).stream()
                .filter((edge) -> !hasNumericBusinessCondition(edge))
                .toList();
        nonMoved.stream()
            .filter(WfmSemanticValidator::isRejectLike)
            .forEach((edge) -> addEdgeIfUnique(edge, repairedEdges, emittedEdgeIds, emittedEdgeKeys));
        nonMoved.stream()
            .filter((edge) -> !WfmSemanticValidator.isRejectLike(edge))
            .filter((edge) -> !sameTransition(edge, plan.approveEdge()))
            .forEach((edge) -> addEdgeIfUnique(edge, repairedEdges, emittedEdgeIds, emittedEdgeKeys));
        plan.movedEdges().forEach((edge) -> addEdgeIfUnique(edge, repairedEdges, emittedEdgeIds, emittedEdgeKeys));
        continue;
      }

      outgoing.getOrDefault(node.id(), List.of()).stream()
          .filter((edge) -> !repairedApprovalIds.contains(edge.from()) || !hasNumericBusinessCondition(edge))
          .forEach((edge) -> addEdgeIfUnique(edge, repairedEdges, emittedEdgeIds, emittedEdgeKeys));
    }

    normalizedEdges.stream()
        .filter((edge) -> !repairedApprovalIds.contains(edge.from()) || !hasNumericBusinessCondition(edge))
        .forEach((edge) -> addEdgeIfUnique(edge, repairedEdges, emittedEdgeIds, emittedEdgeKeys));

    return List.copyOf(repairedEdges);
  }

  private boolean sameTransition(WfmEdge left, WfmEdge right) {
    return left.from().equals(right.from())
        && left.to().equals(right.to())
        && equalsNullable(left.label(), right.label())
        && equalsNullable(left.condition(), right.condition());
  }

  private void addEdgeIfUnique(
      WfmEdge edge, List<WfmEdge> edges, Set<String> emittedEdgeIds, Set<String> emittedEdgeKeys) {
    String key = edge.from() + "\u0000" + edge.to() + "\u0000" + edge.label() + "\u0000" + edge.condition();
    if (!emittedEdgeIds.add(edge.id()) && !emittedEdgeKeys.add(key)) {
      return;
    }
    if (!emittedEdgeKeys.add(key)) {
      emittedEdgeIds.add(edge.id());
      return;
    }
    edges.add(edge);
  }

  private boolean hasNumericBusinessCondition(WfmEdge edge) {
    return WfmConditionClassifier.isNumericBusinessCondition(firstNonBlank(edge.condition(), edge.label()));
  }

  private Optional<String> numericConditionField(WfmEdge edge) {
    return WfmConditionClassifier.numericConditionField(firstNonBlank(edge.condition(), edge.label()));
  }

  private ApprovalOutcome approvalOutcome(String label, String condition) {
    WfmEdge probe = new WfmEdge("probe", "from", "to", label, condition, Map.of());
    if (WfmSemanticValidator.isApproveLike(probe)) {
      return ApprovalOutcome.APPROVE;
    }
    if (WfmSemanticValidator.isRejectLike(probe)) {
      return ApprovalOutcome.REJECT;
    }
    return ApprovalOutcome.UNKNOWN;
  }

  private String approvalCondition(WfmNode node, boolean approved) {
    String actor = trimToNull(node.actor());
    if (actor == null) {
      return approved ? "Approved" : "Rejected";
    }
    return actor + (approved ? " approves" : " rejects");
  }

  private Map<String, WfmNode> toNodeMap(List<WfmNode> nodes) {
    return nodes.stream().collect(Collectors.toMap(WfmNode::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
  }

  private String normalizeKind(String kind) {
    String trimmed = trimToNull(kind);
    if (trimmed == null) {
      return "action";
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    return KNOWN_KINDS.contains(lower) ? lower : trimmed;
  }

  private String readableWorkflowName(String value) {
    String trimmed = blankToDefault(value, "Workflow");
    if (trimmed.contains(" ") || trimmed.contains("_") || trimmed.contains("-")) {
      return WfmConditionClassifier.toTitle(trimmed);
    }
    String readable = trimmed.replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
    return readable.isBlank() ? "Workflow" : readable;
  }

  private String labelFromId(String id) {
    return WfmConditionClassifier.toTitle(id);
  }

  private String uniqueId(String base, Set<String> usedIds) {
    String candidate = base;
    int suffix = 2;
    while (usedIds.contains(candidate)) {
      candidate = base + "_" + suffix;
      suffix++;
    }
    return candidate;
  }

  private String defaultEdgeId(String from, String to, int index) {
    if (from != null && to != null) {
      return "edge_" + from + "_" + to;
    }
    return "edge_" + index;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      String trimmed = trimToNull(value);
      if (trimmed != null) {
        return trimmed;
      }
    }
    return null;
  }

  private String blankToDefault(String value, String fallback) {
    String trimmed = trimToNull(value);
    return trimmed == null ? fallback : trimmed;
  }

  private String trimToNull(String value) {
    return value == null || value.trim().isBlank() ? null : value.trim();
  }

  private boolean equalsNullable(String left, String right) {
    return left == null ? right == null : left.equals(right);
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
  }

  private Map<String, Object> metadataOrEmpty(Map<String, Object> metadata) {
    return metadata == null ? Map.of() : metadata;
  }

  private enum ApprovalOutcome {
    APPROVE,
    REJECT,
    UNKNOWN
  }

  private record ApprovalRepairPlan(
      String approvalNodeId,
      WfmNode decisionNode,
      boolean decisionCreated,
      WfmEdge approveEdge,
      List<WfmEdge> movedEdges) {}
}
