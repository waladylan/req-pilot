package com.reqpilot.wfm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WfmNormalizer {

  public WfmDocument normalize(WfmDocument document) {
    if (document == null) {
      throw new IllegalArgumentException("WFM document is required");
    }

    WfmWorkflow workflow = normalizeWorkflow(document.workflow());
    WfmAst ast = normalizeAst(document.ast());
    WfmExtensions extensions =
        document.extensions() == null ? new WfmExtensions(List.of(), List.of()) : document.extensions();

    return new WfmDocument(
        blankToDefault(document.schemaVersion(), "1.0"),
        blankToDefault(document.modelType(), "WORKFLOW_AST"),
        workflow,
        extensions,
        ast);
  }

  private WfmWorkflow normalizeWorkflow(WfmWorkflow workflow) {
    if (workflow == null) {
      return new WfmWorkflow("workflow", "Workflow", null, "unknown", null, null);
    }

    return new WfmWorkflow(
        blankToDefault(workflow.id(), "workflow"),
        blankToDefault(workflow.title(), "Workflow"),
        trimToNull(workflow.description()),
        blankToDefault(workflow.language(), "unknown"),
        trimToNull(workflow.domain()),
        trimToNull(workflow.sourceRequirement()));
  }

  private WfmAst normalizeAst(WfmAst ast) {
    if (ast == null) {
      return new WfmAst(defaultActors(), List.of(), List.of(), List.of(), List.of());
    }

    List<WfmNode> nodes = normalizeNodes(ast.nodes());
    List<WfmTransition> transitions = normalizeTransitions(ast.transitions());
    List<WfmActor> actors = normalizeActors(ast.actors());
    List<WfmVariable> variables = normalizeVariables(ast.variables());

    return new WfmAst(actors, variables, nodes, transitions, ast.annotations());
  }

  private List<WfmActor> defaultActors() {
    return List.of(
        new WfmActor("USER", "User", WfmActorType.USER),
        new WfmActor("SYSTEM", "System", WfmActorType.SYSTEM));
  }

  private List<WfmNode> normalizeNodes(List<WfmNode> nodes) {
    List<WfmNode> normalized = new ArrayList<>();
    Set<String> usedIds = new LinkedHashSet<>();
    int nextId = 1;

    for (WfmNode node : nodes == null ? List.<WfmNode>of() : nodes) {
      String id = trimToNull(node.id());
      if (id == null || usedIds.contains(id)) {
        id = nextAvailableId("N", nextId, usedIds);
      }
      usedIds.add(id);
      nextId++;

      WfmNodeRole role = node.role() == null ? WfmNodeRole.ACTION : node.role();
      String title = blankToDefault(node.title(), roleLabel(role));
      String kind = blankToDefault(node.kind(), role.name());

      normalized.add(
          new WfmNode(
              id,
              role,
              normalizeKind(kind),
              title,
              trimToNull(node.description()),
              trimToNull(node.actorId()),
              List.copyOf(new LinkedHashSet<>(node.tags())),
              node.data()));
    }

    return List.copyOf(normalized);
  }

  private List<WfmActor> normalizeActors(List<WfmActor> actors) {
    if (actors == null || actors.isEmpty()) {
      return defaultActors();
    }

    List<WfmActor> normalized = new ArrayList<>();
    Set<String> usedIds = new LinkedHashSet<>();
    int nextId = 1;

    for (WfmActor actor : actors) {
      String id = trimToNull(actor.id());
      if (id == null || usedIds.contains(id)) {
        id = nextAvailableId("A", nextId, usedIds);
      }
      usedIds.add(id);
      nextId++;

      WfmActorType type = actor.type() == null ? WfmActorType.USER : actor.type();
      normalized.add(new WfmActor(id, blankToDefault(actor.name(), actorLabel(type)), type));
    }

    return List.copyOf(normalized);
  }

  private List<WfmVariable> normalizeVariables(List<WfmVariable> variables) {
    List<WfmVariable> normalized = new ArrayList<>();
    Set<String> usedIds = new LinkedHashSet<>();
    int nextId = 1;

    for (WfmVariable variable : variables == null ? List.<WfmVariable>of() : variables) {
      String id = trimToNull(variable.id());
      if (id == null || usedIds.contains(id)) {
        id = nextAvailableId("V", nextId, usedIds);
      }
      usedIds.add(id);
      nextId++;

      normalized.add(
          new WfmVariable(
              id,
              blankToDefault(variable.name(), id),
              variable.type() == null ? WfmVariableType.UNKNOWN : variable.type(),
              trimToNull(variable.description()),
              variable.required(),
              variable.defaultValue()));
    }

    return List.copyOf(normalized);
  }

  private List<WfmTransition> normalizeTransitions(List<WfmTransition> transitions) {
    List<WfmTransition> normalized = new ArrayList<>();
    Set<String> usedIds = new LinkedHashSet<>();
    int nextId = 1;

    for (WfmTransition transition : transitions == null ? List.<WfmTransition>of() : transitions) {
      String id = trimToNull(transition.id());
      if (id == null || usedIds.contains(id)) {
        id = nextAvailableId("T", nextId, usedIds);
      }
      usedIds.add(id);
      nextId++;

      normalized.add(
          new WfmTransition(
              id,
              transition.from(),
              transition.to(),
              transition.semantic() == null ? WfmTransitionSemantic.DEFAULT : transition.semantic(),
              trimToNull(transition.kind()),
              trimToNull(transition.condition()),
              trimToNull(transition.description()),
              transition.data()));
    }

    return List.copyOf(normalized);
  }

  private String nextAvailableId(String prefix, int start, Set<String> usedIds) {
    int index = start;
    String candidate = prefix + index;
    while (usedIds.contains(candidate)) {
      index++;
      candidate = prefix + index;
    }
    return candidate;
  }

  private String roleLabel(WfmNodeRole role) {
    return switch (role) {
      case START -> "Start";
      case END -> "End";
      case DECISION -> "Decision";
      case INPUT -> "Input";
      case OUTPUT -> "Output";
      case ERROR -> "Error";
      case SUBPROCESS -> "Subprocess";
      case ACTION -> "Action";
    };
  }

  private String actorLabel(WfmActorType type) {
    return switch (type) {
      case USER -> "User";
      case SYSTEM -> "System";
      case EXTERNAL_SYSTEM -> "External System";
      case ADMIN -> "Admin";
      case GUEST -> "Guest";
    };
  }

  private String normalizeKind(String value) {
    return value.trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "").toUpperCase(Locale.ROOT);
  }

  private String blankToDefault(String value, String fallback) {
    String trimmed = trimToNull(value);
    return trimmed == null ? fallback : trimmed;
  }

  private String trimToNull(String value) {
    if (value == null || value.trim().isBlank()) {
      return null;
    }
    return value.trim();
  }
}
