package com.reqpilot.wfm;

import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
// Flowchart mapping for the public generate-flow path now lives in the Python workflow engine.
// This mapper is retained for rollback, legacy endpoints, and test case compatibility.
public class WfmToFlowchartMapper {

  private final WfmToMermaidGenerator mermaidGenerator;

  public WfmToFlowchartMapper(WfmToMermaidGenerator mermaidGenerator) {
    this.mermaidGenerator = mermaidGenerator;
  }

  public Flowchart toFlowchart(WfmDocument wfm) {
    List<FlowNode> nodes = wfm.ast().nodes().stream().map(this::toFlowNode).toList();
    List<FlowEdge> edges = wfm.ast().transitions().stream().map(this::toFlowEdge).toList();
    return new Flowchart(nodes, edges, mermaidGenerator.generate(wfm));
  }

  private FlowNode toFlowNode(WfmNode node) {
    return new FlowNode(node.id(), node.title(), toFlowNodeType(node.role()));
  }

  private FlowNodeType toFlowNodeType(WfmNodeRole role) {
    return switch (role) {
      case START -> FlowNodeType.START;
      case END -> FlowNodeType.END;
      case DECISION -> FlowNodeType.DECISION;
      case ACTION, INPUT, OUTPUT, ERROR, SUBPROCESS -> FlowNodeType.ACTION;
    };
  }

  private FlowEdge toFlowEdge(WfmTransition transition) {
    return new FlowEdge(
        transition.id(),
        transition.from(),
        transition.to(),
        transition.condition(),
        toFlowEdgeType(transition.semantic()));
  }

  private FlowEdgeType toFlowEdgeType(WfmTransitionSemantic semantic) {
    return switch (semantic) {
      case YES -> FlowEdgeType.YES;
      case NO -> FlowEdgeType.NO;
      case SUCCESS -> FlowEdgeType.SUCCESS;
      case FAILURE -> FlowEdgeType.FAILURE;
      case CANCEL -> FlowEdgeType.CANCEL;
      case RETRY -> FlowEdgeType.RETRY;
      case TIMEOUT -> FlowEdgeType.TIMEOUT;
      case DEFAULT -> FlowEdgeType.DEFAULT;
    };
  }
}
