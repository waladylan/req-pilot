package com.reqpilot.wfm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reqpilot.ai.AiProviderException;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.dto.WfmEdge;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WfmDefinitionValidatorTest {

  private final WfmValidator validator = new WfmValidator();

  @Test
  void acceptsValidWfmDefinition() {
    assertThat(validator.validateDefinition(validDefinition()).valid()).isTrue();
  }

  @Test
  void rejectsMissingNodes() {
    WfmDefinition invalid =
        new WfmDefinition(
            "Purchase Request",
            "1.0",
            "summary",
            List.of("User"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "LOW");

    assertThat(validator.validateDefinition(invalid).errors())
        .extracting(WfmValidationError::code)
        .contains("NODES_REQUIRED");
  }

  @Test
  void rejectsDuplicateNodeIds() {
    WfmDefinition invalid =
        new WfmDefinition(
            "Purchase Request",
            "1.0",
            "summary",
            List.of("User"),
            List.of(node("start", "start", "Start"), node("start", "action", "Duplicate")),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "LOW");

    assertThat(validator.validateDefinition(invalid).errors())
        .extracting(WfmValidationError::code)
        .contains("DUPLICATE_NODE_ID", "END_REQUIRED");
  }

  @Test
  void rejectsInvalidEdgeReferences() {
    WfmDefinition invalid =
        new WfmDefinition(
            "Purchase Request",
            "1.0",
            "summary",
            List.of("User"),
            List.of(node("start", "start", "Start"), node("end", "end", "End")),
            List.of(edge("edge_1", "missing_source", "missing_target")),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "LOW");

    assertThat(validator.validateDefinition(invalid).errors())
        .extracting(WfmValidationError::code)
        .contains("INVALID_FROM", "INVALID_TO");
  }

  @Test
  void allowsUnknownNodeKindWhenNonBlank() {
    WfmDefinition definition =
        new WfmDefinition(
            "Purchase Request",
            "1.0",
            "summary",
            List.of("User"),
            List.of(
                node("start", "start", "Start"),
                node("custom_risk_gate", "custom_risk_gate", "Custom risk gate"),
                node("end", "end", "End")),
            List.of(edge("edge_1", "start", "custom_risk_gate"), edge("edge_2", "custom_risk_gate", "end")),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "LOW");

    assertThat(validator.validateDefinition(definition).valid()).isTrue();
  }

  @Test
  void rejectsMissingMultipleStartAndMissingEnd() {
    WfmDefinition missingStart =
        definitionWithNodes(List.of(node("create", "action", "Create"), node("end", "end", "End")));
    WfmDefinition multipleStart =
        definitionWithNodes(List.of(node("start_1", "start", "Start"), node("start_2", "start", "Start again"), node("end", "end", "End")));
    WfmDefinition missingEnd =
        definitionWithNodes(List.of(node("start", "start", "Start"), node("create", "action", "Create")));

    assertThat(validator.validateDefinition(missingStart).errors())
        .extracting(WfmValidationError::code)
        .contains("START_COUNT");
    assertThat(validator.validateDefinition(multipleStart).errors())
        .extracting(WfmValidationError::code)
        .contains("START_COUNT");
    assertThat(validator.validateDefinition(missingEnd).errors())
        .extracting(WfmValidationError::code)
        .contains("END_REQUIRED");
  }

  @Test
  void validateDefinitionOrThrowUsesAiInvalidResponseForInvalidWfm() {
    assertThatThrownBy(() -> validator.validateDefinitionOrThrow(definitionWithNodes(List.of()), "OPENROUTER"))
        .isInstanceOf(AiProviderException.class)
        .hasMessageContaining("AI response WFM is invalid");
  }

  private WfmDefinition validDefinition() {
    return new WfmDefinition(
        "Purchase Request",
        "1.0",
        "summary",
        List.of("User", "Manager"),
        List.of(
            node("start", "start", "Start"),
            node("create_request", "action", "Create purchase request"),
            node("manager_approval", "approval", "Manager approval", "Manager"),
            node("approved", "end", "Approved"),
            node("rejected", "end", "Rejected")),
        List.of(
            edge("edge_start_create", "start", "create_request"),
            edge("edge_create_approval", "create_request", "manager_approval"),
            new WfmEdge("edge_approval_approved", "manager_approval", "approved", "Approve", "Manager approves", Map.of()),
            new WfmEdge("edge_approval_rejected", "manager_approval", "rejected", "Reject", "Manager rejects", Map.of())),
        List.of("Manager approval is required."),
        List.of("Amount must be positive."),
        List.of("Role-based access exists."),
        List.of("Manager rejects request."),
        List.of("Is there an approval time limit?"),
        "MEDIUM");
  }

  private WfmDefinition definitionWithNodes(List<com.reqpilot.dto.WfmNode> nodes) {
    return new WfmDefinition(
        "Purchase Request",
        "1.0",
        "summary",
        List.of("User"),
        nodes,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "LOW");
  }

  private com.reqpilot.dto.WfmNode node(String id, String kind, String label) {
    return node(id, kind, label, null);
  }

  private com.reqpilot.dto.WfmNode node(String id, String kind, String label, String actor) {
    return new com.reqpilot.dto.WfmNode(id, kind, label, actor, null, Map.of());
  }

  private WfmEdge edge(String id, String from, String to) {
    return new WfmEdge(id, from, to, null, null, Map.of());
  }
}
