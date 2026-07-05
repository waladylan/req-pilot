package com.reqpilot.ai;

public class AiWfmResponseParsingException extends RuntimeException {

  private final String reasonCode;

  public AiWfmResponseParsingException(String reasonCode, String message) {
    super(message);
    this.reasonCode = reasonCode;
  }

  public AiWfmResponseParsingException(String reasonCode, String message, Throwable cause) {
    super(message, cause);
    this.reasonCode = reasonCode;
  }

  public String reasonCode() {
    return reasonCode;
  }
}
