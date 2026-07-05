package com.reqpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.config.AiProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
public class GeminiProvider implements AiProvider {

  private static final String PROVIDER = "GEMINI";

  private final AiProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public GeminiProvider(AiProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newHttpClient());
  }

  GeminiProvider(AiProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public AiResponse generate(AiRequest request) {
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw new AiProviderException(AiErrorType.INVALID_API_KEY, PROVIDER, "Gemini API key is not configured");
    }

    String model = request.model() == null || request.model().isBlank() ? properties.model() : request.model();
    HttpRequest httpRequest =
        HttpRequest.newBuilder(endpoint(model))
            .timeout(Duration.ofMillis(properties.timeoutMs()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(request), StandardCharsets.UTF_8))
            .build();

    try {
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new AiProviderException(
            statusToErrorType(response.statusCode()), PROVIDER, "Gemini returned HTTP " + response.statusCode());
      }
      return new AiResponse(extractText(response.body()), PROVIDER, model, null, null, null, null);
    } catch (IOException exception) {
      throw new AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, PROVIDER, "Gemini request failed", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AiProviderException(AiErrorType.TIMEOUT, PROVIDER, "Gemini request was interrupted", exception);
    }
  }

  private URI endpoint(String model) {
    String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
    return URI.create(
        "https://generativelanguage.googleapis.com/v1beta/models/"
            + encodedModel
            + ":generateContent?key="
            + properties.apiKey());
  }

  private String requestBody(AiRequest request) {
    try {
      String prompt = request.systemPrompt() + "\n\n" + request.userPrompt();
      Map<String, Object> body =
          Map.of(
              "contents",
              new Object[] {
                Map.of("role", "user", "parts", new Object[] {Map.of("text", prompt)})
              },
              "generationConfig",
              Map.of(
                  "temperature",
                  request.temperature() == null ? properties.temperature() : request.temperature(),
                  "maxOutputTokens",
                  request.maxTokens() == null ? properties.maxOutputTokens() : request.maxTokens(),
                  "responseMimeType",
                  "application/json"));
      return objectMapper.writeValueAsString(body);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to serialize AI provider request", exception);
    }
  }

  private String extractText(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
      if (!parts.isArray() || parts.isEmpty() || parts.path(0).path("text").asText().isBlank()) {
        throw new AiProviderException(AiErrorType.INVALID_RESPONSE, PROVIDER, "Gemini returned empty content");
      }
      return parts.path(0).path("text").asText();
    } catch (IOException exception) {
      throw new AiProviderException(AiErrorType.INVALID_RESPONSE, PROVIDER, "Gemini response is invalid", exception);
    }
  }

  private AiErrorType statusToErrorType(int statusCode) {
    return switch (statusCode) {
      case 401 -> AiErrorType.INVALID_API_KEY;
      case 402 -> AiErrorType.PAYMENT_REQUIRED;
      case 429 -> AiErrorType.PROVIDER_RATE_LIMIT;
      case 500, 502, 503, 504 -> AiErrorType.PROVIDER_UNAVAILABLE;
      default -> AiErrorType.UNKNOWN;
    };
  }
}
