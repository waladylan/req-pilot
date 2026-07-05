package com.reqpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.config.AiProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openrouter", matchIfMissing = true)
public class OpenRouterProvider implements AiProvider {

  private static final String PROVIDER = "OPENROUTER";
  private static final int MAX_RETRIES = 2;
  private static final List<Long> BACKOFF_MS = List.of(500L, 1500L);
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouterProvider.class);

  private final AiProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public OpenRouterProvider(AiProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newHttpClient());
  }

  OpenRouterProvider(AiProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public AiResponse generate(AiRequest request) {
    String apiKey = properties.openrouter().apiKey();
    if (apiKey == null || apiKey.isBlank()) {
      throw new AiProviderException(AiErrorType.INVALID_API_KEY, PROVIDER, "OpenRouter API key is not configured");
    }

    String primaryModel = hasText(request.model()) ? request.model() : properties.openrouter().defaultModel();
    List<String> models = requestModels(primaryModel);

    int attempt = 0;
    while (true) {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(endpoint())
              .timeout(Duration.ofSeconds(properties.openrouter().timeoutSeconds()))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(requestBody(request, models), StandardCharsets.UTF_8))
              .build();

      try {
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          return parseSuccessfulResponse(response.body(), models.getFirst());
        }

        AiProviderException exception = providerException(response, models);
        if (!shouldRetry(exception.errorType()) || attempt >= MAX_RETRIES) {
          throw exception;
        }
        sleepBeforeRetry(attempt, retryAfterMs(response));
        attempt++;
      } catch (HttpTimeoutException exception) {
        AiProviderException providerException =
            new AiProviderException(
                AiErrorType.TIMEOUT,
                PROVIDER,
                "OpenRouter provider timed out. Please retry later.",
                exception);
        if (!shouldRetry(providerException.errorType()) || attempt >= MAX_RETRIES) {
          throw providerException;
        }
        sleepBeforeRetry(attempt, Optional.empty());
        attempt++;
      } catch (IOException exception) {
        AiProviderException providerException =
            new AiProviderException(
                AiErrorType.PROVIDER_UNAVAILABLE,
                PROVIDER,
                "OpenRouter provider is unavailable. Please retry later.",
                exception);
        if (!shouldRetry(providerException.errorType()) || attempt >= MAX_RETRIES) {
          throw providerException;
        }
        sleepBeforeRetry(attempt, Optional.empty());
        attempt++;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new AiProviderException(AiErrorType.TIMEOUT, PROVIDER, "OpenRouter request was interrupted", exception);
      }
    }
  }

  AiResponse parseSuccessfulResponse(String responseBody, String fallbackModel) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode content = root.path("choices").path(0).path("message").path("content");
      if (content.asText().isBlank()) {
        throw new AiProviderException(AiErrorType.INVALID_RESPONSE, PROVIDER, "OpenRouter returned empty content");
      }

      JsonNode usage = root.path("usage");
      return new AiResponse(
          content.asText(),
          PROVIDER,
          hasText(root.path("model").asText()) ? root.path("model").asText() : fallbackModel,
          integerOrNull(usage.path("prompt_tokens")),
          integerOrNull(usage.path("completion_tokens")),
          integerOrNull(usage.path("total_tokens")),
          hasText(root.path("id").asText()) ? root.path("id").asText() : null);
    } catch (AiProviderException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new AiProviderException(AiErrorType.INVALID_RESPONSE, PROVIDER, "OpenRouter response is invalid", exception);
    }
  }

  String requestBody(AiRequest request, List<String> models) {
    try {
      List<Map<String, String>> messages = new ArrayList<>();
      if (hasText(request.systemPrompt())) {
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
      }
      messages.add(Map.of("role", "user", "content", request.userPrompt()));

      Map<String, Object> body = new LinkedHashMap<>();
      if (models.size() > 1) {
        body.put("models", models);
      } else {
        body.put("model", models.getFirst());
      }
      body.put("messages", messages);
      body.put("temperature", request.temperature() == null ? properties.openrouter().temperature() : request.temperature());
      body.put(
          "max_completion_tokens",
          request.maxTokens() == null ? properties.openrouter().maxCompletionTokens() : request.maxTokens());
      body.put("stream", false);
      return objectMapper.writeValueAsString(body);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to serialize OpenRouter request", exception);
    }
  }

  AiProviderException providerException(HttpResponse<String> response, List<String> models) {
    OpenRouterError error = parseError(response.body());
    AiErrorType errorType = errorType(response.statusCode(), error);
    logProviderError(response.statusCode(), error, models);
    return new AiProviderException(errorType, PROVIDER, userMessage(errorType));
  }

  AiErrorType errorType(int statusCode, OpenRouterError error) {
    if (statusCode == 401) {
      return AiErrorType.INVALID_API_KEY;
    }
    if (statusCode == 402) {
      return AiErrorType.PAYMENT_REQUIRED;
    }
    if (statusCode == 408) {
      return AiErrorType.TIMEOUT;
    }
    if (statusCode == 429) {
      String metadataErrorType = error == null ? null : error.metadataErrorType();
      if ("rate_limit_exceeded".equals(metadataErrorType)) {
        return AiErrorType.PROVIDER_RATE_LIMIT;
      }
      if ("provider_overloaded".equals(metadataErrorType)) {
        return AiErrorType.PROVIDER_OVERLOADED;
      }
      if ("provider_unavailable".equals(metadataErrorType)) {
        return AiErrorType.PROVIDER_UNAVAILABLE;
      }
      return AiErrorType.OPENROUTER_RATE_LIMIT;
    }
    if (statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504) {
      return AiErrorType.PROVIDER_UNAVAILABLE;
    }
    return AiErrorType.UNKNOWN;
  }

  private URI endpoint() {
    String baseUrl = properties.openrouter().baseUrl().replaceAll("/+$", "");
    return URI.create(baseUrl + "/chat/completions");
  }

  private OpenRouterError parseError(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }
    try {
      OpenRouterErrorResponse response = objectMapper.readValue(responseBody, OpenRouterErrorResponse.class);
      return response == null ? null : response.error();
    } catch (IOException exception) {
      return null;
    }
  }

  private List<String> requestModels(String primaryModel) {
    Set<String> models = new LinkedHashSet<>();
    models.add(primaryModel);
    models.addAll(properties.openrouter().fallbackModels());
    return List.copyOf(models);
  }

  private boolean shouldRetry(AiErrorType errorType) {
    return errorType == AiErrorType.PROVIDER_RATE_LIMIT
        || errorType == AiErrorType.PROVIDER_OVERLOADED
        || errorType == AiErrorType.PROVIDER_UNAVAILABLE
        || errorType == AiErrorType.TIMEOUT;
  }

  private Optional<Long> retryAfterMs(HttpResponse<?> response) {
    return response.headers().firstValue("Retry-After").flatMap(this::parseRetryAfterMs);
  }

  private Optional<Long> parseRetryAfterMs(String value) {
    try {
      return Optional.of(Math.max(0, Long.parseLong(value.trim()) * 1000L));
    } catch (NumberFormatException exception) {
      try {
        Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        return Optional.of(Math.max(0, Duration.between(Instant.now(), retryAt).toMillis()));
      } catch (RuntimeException ignored) {
        return Optional.empty();
      }
    }
  }

  private void sleepBeforeRetry(int attempt, Optional<Long> retryAfterMs) {
    long backoffMs = retryAfterMs.orElse(BACKOFF_MS.get(Math.min(attempt, BACKOFF_MS.size() - 1)));
    try {
      Thread.sleep(backoffMs);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AiProviderException(AiErrorType.TIMEOUT, PROVIDER, "OpenRouter retry sleep was interrupted", exception);
    }
  }

  private String userMessage(AiErrorType errorType) {
    return switch (errorType) {
      case PROVIDER_RATE_LIMIT, PROVIDER_OVERLOADED, PROVIDER_UNAVAILABLE, OPENROUTER_RATE_LIMIT ->
          "OpenRouter provider is rate limited or overloaded. Please retry later or use a paid/fallback model.";
      case INVALID_API_KEY -> "OpenRouter API key is invalid or missing.";
      case PAYMENT_REQUIRED -> "OpenRouter account needs credits before using this model.";
      case TIMEOUT -> "OpenRouter provider timed out. Please retry later.";
      case INVALID_RESPONSE -> "OpenRouter returned an invalid response.";
      case UNKNOWN -> "OpenRouter returned an unexpected error.";
    };
  }

  private void logProviderError(int statusCode, OpenRouterError error, List<String> models) {
    LOGGER.warn(
        "OpenRouter request failed status={} message={} metadataErrorType={} providerCode={} models={}",
        statusCode,
        error == null ? null : error.message(),
        error == null ? null : error.metadataErrorType(),
        error == null ? null : error.metadataProviderCode(),
        models);
  }

  private Integer integerOrNull(JsonNode node) {
    return node != null && node.isNumber() ? node.asInt() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
