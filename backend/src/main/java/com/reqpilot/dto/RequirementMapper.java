package com.reqpilot.dto;

import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GeneratedTestCase;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RequirementMapper {

  public FlowchartDto toDto(Flowchart flowchart) {
    return new FlowchartDto(
        flowchart.nodes().stream().map(this::toDto).toList(),
        flowchart.edges().stream().map(this::toDto).toList(),
        flowchart.mermaid());
  }

  public Flowchart toModel(FlowchartDto flowchart) {
    return new Flowchart(
        flowchart.nodes().stream().map(this::toModel).toList(),
        flowchart.edges().stream().map(this::toModel).toList(),
        flowchart.mermaid());
  }

  public List<TestCaseDto> toTestCaseDtos(List<GeneratedTestCase> testCases) {
    return testCases.stream().map(this::toDto).toList();
  }

  private FlowNodeDto toDto(FlowNode node) {
    return new FlowNodeDto(node.id(), node.label(), node.type());
  }

  private FlowEdgeDto toDto(FlowEdge edge) {
    return new FlowEdgeDto(edge.id(), edge.source(), edge.target(), edge.label(), edge.type());
  }

  private FlowNode toModel(FlowNodeDto node) {
    return new FlowNode(node.id(), node.label(), node.type());
  }

  private FlowEdge toModel(FlowEdgeDto edge) {
    return new FlowEdge(edge.id(), edge.source(), edge.target(), edge.label(), edge.type());
  }

  private TestCaseDto toDto(GeneratedTestCase testCase) {
    return new TestCaseDto(
        testCase.id(),
        testCase.title(),
        testCase.preconditions(),
        testCase.steps(),
        testCase.expectedResult(),
        testCase.priority());
  }
}
