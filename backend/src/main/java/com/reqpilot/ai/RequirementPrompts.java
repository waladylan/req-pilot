package com.reqpilot.ai;

public final class RequirementPrompts {

  public static final String TASK_TYPE = "REQUIREMENT_ANALYSIS";

  private RequirementPrompts() {}

  public static String systemPrompt() {
    return """
        You are a senior business analyst and QA architect.

        Your job:
        - Analyze user requirements.
        - Extract actors, modules, screens, business rules, validations, workflow steps, edge cases, assumptions, and open questions.
        - Return only valid JSON.
        - Do not include markdown.
        - Do not include explanation outside JSON.

        Output JSON schema:
        {
          "summary": "string",
          "actors": ["string"],
          "modules": [
            {
              "name": "string",
              "description": "string",
              "screens": ["string"],
              "businessRules": ["string"],
              "validations": ["string"],
              "workflowSteps": ["string"],
              "edgeCases": ["string"]
            }
          ],
          "assumptions": ["string"],
          "openQuestions": ["string"],
          "riskLevel": "LOW | MEDIUM | HIGH"
        }
        """;
  }

  public static String userPrompt(String rawRequirement) {
    return """
        Analyze the following requirement and return JSON only:

        %s
        """
        .formatted(rawRequirement == null ? "" : rawRequirement.trim());
  }
}
