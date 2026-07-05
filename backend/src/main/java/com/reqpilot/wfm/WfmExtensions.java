package com.reqpilot.wfm;

import jakarta.validation.Valid;
import java.util.List;

public record WfmExtensions(
    @Valid List<WfmNodeKindDefinition> nodeKinds,
    @Valid List<WfmTransitionKindDefinition> transitionKinds) {

  public WfmExtensions {
    nodeKinds = nodeKinds == null ? List.of() : List.copyOf(nodeKinds);
    transitionKinds = transitionKinds == null ? List.of() : List.copyOf(transitionKinds);
  }
}
