package com.reqpilot.wfm;

import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import com.reqpilot.dto.WfmNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WfmSemanticValidator {

  public WfmQualityReport validate(WfmDefinition wfm) {
    if (wfm == null) {
      return new WfmQualityReport(false, false, List.of("WFM definition is required"), List.of(), List.of());
    }

    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    boolean repairable = false;

    Map<String, WfmNode> nodesById = wfm.nodes().stream().collect(Collectors.toMap(WfmNode::id, (node) -> node, (left, right) -> left));
    Map<String, List<WfmEdge>> outgoing =
        wfm.edges().stream().collect(Collectors.groupingBy(WfmEdge::from));

    for (WfmNode node : wfm.nodes()) {
      String kind = normalize(node.kind());
      List<WfmEdge> outgoingEdges = outgoing.getOrDefault(node.id(), List.of());

      if ("approval".equals(kind)) {
        ApprovalSemantic approvalSemantic = validateApprovalNode(node, outgoingEdges, warnings, errors);
        repairable = repairable || approvalSemantic.repairable();
      }

      if ("decision".equals(kind)) {
        validateDecisionNode(node, outgoingEdges, warnings);
      }
    }

    for (WfmEdge edge : wfm.edges()) {
      WfmNode source = nodesById.get(edge.from());
      if (source != null
          && !"decision".equals(normalize(source.kind()))
          && isBusinessCondition(edge)) {
        String message =
            "Business condition '%s' should originate from a decision node."
                .formatted(firstNonBlank(edge.condition(), edge.label()));
        if ("approval".equals(normalize(source.kind()))) {
          errors.add(message);
          repairable = true;
        } else {
          warnings.add(message);
        }
      }
    }

    return new WfmQualityReport(errors.isEmpty(), repairable, errors, warnings, List.of());
  }

  public boolean isNumericBusinessCondition(String value) {
    return WfmConditionClassifier.isNumericBusinessCondition(value);
  }

  private ApprovalSemantic validateApprovalNode(
      WfmNode node, List<WfmEdge> outgoingEdges, List<String> warnings, List<String> errors) {
    boolean hasApprovePath = false;
    boolean hasRejectPath = false;
    boolean repairable = false;

    for (WfmEdge edge : outgoingEdges) {
      if (isApproveLike(edge)) {
        hasApprovePath = true;
      }
      if (isRejectLike(edge)) {
        hasRejectPath = true;
      }
      if (isBusinessCondition(edge)) {
        String condition = firstNonBlank(edge.condition(), edge.label());
        errors.add("Approval node '%s' has business condition outgoing edge '%s'.".formatted(node.id(), condition));
        warnings.add("Business condition '%s' should originate from a decision node.".formatted(condition));
        repairable = true;
      }
    }

    if (!outgoingEdges.isEmpty() && !hasApprovePath && !outgoingEdges.stream().anyMatch(this::isBusinessCondition)) {
      warnings.add("Approval node '%s' should have an approve-like outgoing path.".formatted(node.id()));
    }
    if (!outgoingEdges.isEmpty() && !hasRejectPath) {
      warnings.add("Approval node '%s' should have a reject-like outgoing path when possible.".formatted(node.id()));
    }

    return new ApprovalSemantic(repairable);
  }

  private void validateDecisionNode(WfmNode node, List<WfmEdge> outgoingEdges, List<String> warnings) {
    if (outgoingEdges.size() == 1) {
      warnings.add("Decision node '%s' should have at least two outgoing conditional edges when possible.".formatted(node.id()));
    }
    outgoingEdges.stream()
        .filter((edge) -> isBlank(edge.condition()) && isBlank(edge.label()))
        .forEach(
            (edge) ->
                warnings.add(
                    "Outgoing edge '%s' from decision node '%s' should have condition or label."
                        .formatted(edgeId(edge), node.id())));
  }

  private boolean isBusinessCondition(WfmEdge edge) {
    return WfmConditionClassifier.isNumericBusinessCondition(firstNonBlank(edge.condition(), edge.label()));
  }

  static boolean isApproveLike(WfmEdge edge) {
    String value = normalize(firstNonBlank(edge.label(), edge.condition()));
    return containsAny(value, "approve", "approved", "accept", "accepted", "pass");
  }

  static boolean isRejectLike(WfmEdge edge) {
    String value = normalize(firstNonBlank(edge.label(), edge.condition()));
    return containsAny(value, "reject", "rejected", "deny", "denied", "decline", "declined", "fail", "failed");
  }

  private static boolean containsAny(String value, String... tokens) {
    for (String token : tokens) {
      if (value.contains(token)) {
        return true;
      }
    }
    return false;
  }

  private String edgeId(WfmEdge edge) {
    return isBlank(edge.id()) ? edge.from() + "->" + edge.to() : edge.id();
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (!isBlank(value)) {
        return value.trim();
      }
    }
    return null;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record ApprovalSemantic(boolean repairable) {}
}
