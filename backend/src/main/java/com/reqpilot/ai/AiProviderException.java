package com.reqpilot.ai;

public class AiProviderException extends RuntimeException {

  private final AiErrorType errorType;
  private final String provider;

  public AiProviderException(AiErrorType errorType, String provider, String message) {
    super(message);
    this.errorType = errorType == null ? AiErrorType.UNKNOWN : errorType;
    this.provider = provider;
  }

  public AiProviderException(AiErrorType errorType, String provider, String message, Throwable cause) {
    super(message, cause);
    this.errorType = errorType == null ? AiErrorType.UNKNOWN : errorType;
    this.provider = provider;
  }

  public AiErrorType errorType() {
    return errorType;
  }

  public String provider() {
    return provider;
  }

  public String reasonCode() {
    return "AI_" + errorType.name();
  }
}
