package com.reqpilot.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiJsonValidator {

  private final ObjectMapper objectMapper;

  public AiJsonValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonNode parseObject(String content, String provider) {
    String json = extractJson(content, provider);
    try {
      JsonNode node = objectMapper.readTree(json);
      if (!node.isObject()) {
        throw invalid(provider, "AI response JSON must be an object");
      }
      return node;
    } catch (JsonProcessingException exception) {
      throw new AiProviderException(AiErrorType.INVALID_RESPONSE, provider, "AI response is not valid JSON", exception);
    }
  }

  public <T> T parseObject(String content, Class<T> type, String provider) {
    JsonNode node = parseObject(content, provider);
    try {
      return objectMapper.treeToValue(node, type);
    } catch (JsonProcessingException exception) {
      throw new AiProviderException(AiErrorType.INVALID_RESPONSE, provider, "AI response JSON has invalid shape", exception);
    }
  }

  private String extractJson(String content, String provider) {
    if (content == null || content.isBlank()) {
      throw invalid(provider, "AI response is empty");
    }

    String cleaned = stripCodeFence(content.trim());
    int start = cleaned.indexOf('{');
    int end = cleaned.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw invalid(provider, "AI response does not contain a JSON object");
    }
    return cleaned.substring(start, end + 1);
  }

  private String stripCodeFence(String value) {
    if (!value.startsWith("```")) {
      return value;
    }
    String withoutOpeningFence = value.replaceFirst("^```(?:json)?\\s*", "");
    return withoutOpeningFence.replaceFirst("\\s*```\\s*$", "").trim();
  }

  private AiProviderException invalid(String provider, String message) {
    return new AiProviderException(AiErrorType.INVALID_RESPONSE, provider, message);
  }
}
