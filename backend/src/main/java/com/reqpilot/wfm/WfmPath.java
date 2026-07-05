package com.reqpilot.wfm;

import java.util.List;

public record WfmPath(
    List<WfmNode> nodes,
    List<WfmTransition> transitions,
    String semanticSummary,
    WfmNode terminalNode,
    boolean containsLoop,
    boolean happyPath,
    boolean negativePath,
    boolean errorPath,
    boolean cancelPath,
    boolean retryPath,
    boolean timeoutPath) {

  public WfmPath {
    nodes = List.copyOf(nodes == null ? List.of() : nodes);
    transitions = List.copyOf(transitions == null ? List.of() : transitions);
    semanticSummary = semanticSummary == null ? "default" : semanticSummary;
  }
}
