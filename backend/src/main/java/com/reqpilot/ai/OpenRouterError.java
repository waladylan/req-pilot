package com.reqpilot.ai;

import java.util.Map;

public record OpenRouterError(Integer code, String message, Map<String, Object> metadata) {

  public String metadataErrorType() {
    return metadataValue("error_type");
  }

  public String metadataProviderCode() {
    return metadataValue("provider_code");
  }

  private String metadataValue(String key) {
    if (metadata == null || !metadata.containsKey(key)) {
      return null;
    }
    Object value = metadata.get(key);
    return value == null ? null : String.valueOf(value);
  }
}
