package com.reqpilot.wfm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WfmAst(
    @Valid List<WfmActor> actors,
    @Valid List<WfmVariable> variables,
    @NotNull @Valid List<WfmNode> nodes,
    @NotNull @Valid List<WfmTransition> transitions,
    @Valid List<WfmAnnotation> annotations) {

  public WfmAst {
    actors = actors == null ? List.of() : List.copyOf(actors);
    variables = variables == null ? List.of() : List.copyOf(variables);
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    transitions = transitions == null ? List.of() : List.copyOf(transitions);
    annotations = annotations == null ? List.of() : List.copyOf(annotations);
  }
}
