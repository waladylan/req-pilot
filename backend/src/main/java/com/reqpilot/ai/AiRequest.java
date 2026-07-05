package com.reqpilot.ai;

import java.util.Objects;

public record AiRequest(
    String taskType,
    String systemPrompt,
    String userPrompt,
    String model,
    Integer maxTokens,
    Double temperature) {

  public AiRequest {
    Objects.requireNonNull(taskType, "taskType is required");
    Objects.requireNonNull(systemPrompt, "systemPrompt is required");
    Objects.requireNonNull(userPrompt, "userPrompt is required");
  }
}
