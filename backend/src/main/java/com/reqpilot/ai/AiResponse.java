package com.reqpilot.ai;

import java.util.Objects;

public record AiResponse(
    String content,
    String provider,
    String model,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    String rawResponseId) {

  public AiResponse {
    Objects.requireNonNull(content, "content is required");
    Objects.requireNonNull(provider, "provider is required");
    Objects.requireNonNull(model, "model is required");
  }
}
