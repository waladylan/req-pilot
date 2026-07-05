package com.reqpilot.service;

import java.time.Instant;

public record AiUsageLogEntry(
    String userId,
    String projectId,
    String taskType,
    String provider,
    String model,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Instant createdAt) {}
