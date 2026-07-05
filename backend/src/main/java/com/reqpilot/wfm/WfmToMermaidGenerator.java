package com.reqpilot.wfm;

import org.springframework.stereotype.Service;

@Service
public class WfmToMermaidGenerator {

  public String generate(WfmDocument wfm) {
    StringBuilder mermaid = new StringBuilder("flowchart LR\n");

    for (WfmNode node : wfm.ast().nodes()) {
      mermaid.append("  ").append(node.id()).append(formatNode(node)).append('\n');
    }

    for (WfmTransition transition : wfm.ast().transitions()) {
      mermaid.append("  ").append(transition.from()).append(" --> ");
      if (transition.condition() != null && !transition.condition().isBlank()) {
        mermaid.append("|").append(escapeMermaid(transition.condition())).append("| ");
      }
      mermaid.append(transition.to()).append('\n');
    }

    return mermaid.toString();
  }

  private String formatNode(WfmNode node) {
    String label = escapeMermaid(node.title());
    return switch (node.role()) {
      case START, END -> "([\"" + label + "\"])";
      case DECISION -> "{\"" + label + "\"}";
      case INPUT, OUTPUT -> "[/\"" + label + "\"/]";
      case ERROR -> "[\"" + label + "\"]";
      case SUBPROCESS -> "[[\"" + label + "\"]]";
      case ACTION -> "[\"" + label + "\"]";
    };
  }

  private String escapeMermaid(String label) {
    return label.replace("\\", "\\\\").replace("\"", "\\\"").replace("|", "\\|");
  }
}
