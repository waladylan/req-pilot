package com.reqpilot.ai;

import com.reqpilot.config.AiProperties;
import org.springframework.stereotype.Component;

@Component
public class RequirementToWfmPromptBuilder {

  private static final String FORBIDDEN_FIELDS =
      "x, y, position, color, shape, width, height, selected, dragging, sourceHandle, "
          + "targetHandle, reactFlowType, edgeLabel";

  private final AiProperties properties;

  public RequirementToWfmPromptBuilder(AiProperties properties) {
    this.properties = properties;
  }

  public String systemPrompt() {
    return """
        You are a workflow AST generator.

        Convert the user's requirement into WFM v1 JSON.

        Return only valid JSON.
        Do not return Markdown.
        Do not wrap the JSON in code fences.
        Do not include explanations or comments.

        WFM is a Workflow AST, not a UI graph.

        Do not generate:
        - React Flow
        - Mermaid
        - BPMN
        - PlantUML
        - layout
        - position
        - color
        - shape
        - icon
        - edge label
        - UI state
        - test cases

        Forbidden WFM fields: %s

        Use this exact WFM v1 structure:
        {
          "schemaVersion": "1.0",
          "modelType": "WORKFLOW_AST",
          "workflow": {
            "id": "stable-kebab-case-id",
            "title": "Workflow title",
            "description": "Optional business description",
            "language": "en|vi|unknown",
            "domain": "optional-domain",
            "sourceRequirement": "optional source requirement"
          },
          "extensions": {
            "nodeKinds": [],
            "transitionKinds": []
          },
          "ast": {
            "actors": [],
            "variables": [],
            "nodes": [],
            "transitions": [],
            "annotations": []
          }
        }

        Core node roles:
        - START
        - END
        - ACTION
        - DECISION
        - INPUT
        - OUTPUT
        - ERROR
        - SUBPROCESS

        Core transition semantics:
        - DEFAULT
        - YES
        - NO
        - SUCCESS
        - FAILURE
        - CANCEL
        - RETRY
        - TIMEOUT

        Node rules:
        - Use role for core workflow grammar.
        - Use kind for extensible business/domain meaning.
        - Node IDs must be stable and readable: N1, N2, N3...
        - Every node must include id, role, kind, and title.
        - Exactly one START node is required.
        - At least one END node is required.
        - Do not include UI fields in node data.

        Transition rules:
        - Transition IDs must be stable and readable: T1, T2, T3...
        - Every transition must include id, from, to, and semantic.
        - from and to must reference existing node IDs.
        - Do not include UI fields in transition data.

        Prompt version: %s
        """
        .formatted(FORBIDDEN_FIELDS, properties.promptVersion());
  }

  public String userPrompt(String requirement) {
    return """
        Requirement:
        %s
        """
        .formatted(requirement == null ? "" : requirement.trim());
  }

  public String build(String requirement) {
    return systemPrompt() + "\n" + userPrompt(requirement);
  }
}
