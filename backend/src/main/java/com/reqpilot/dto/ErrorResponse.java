package com.reqpilot.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(String message, List<String> details, Instant timestamp) {

  public static ErrorResponse of(String message, List<String> details) {
    return new ErrorResponse(message, details, Instant.now());
  }
}
