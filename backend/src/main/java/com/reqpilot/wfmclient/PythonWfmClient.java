package com.reqpilot.wfmclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.ai.AiErrorType;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.config.WfmServiceProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PythonWfmClient implements WfmGenerationClient, WfmTestCaseGenerationClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(PythonWfmClient.class);
  private static final String PROVIDER = "PYTHON_WFM_SERVICE";

  private final WfmServiceProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Autowired
  public PythonWfmClient(WfmServiceProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newHttpClient());
  }

  PythonWfmClient(WfmServiceProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public WfmServiceGenerateResponse generate(WfmServiceGenerateRequest request) {
    HttpRequest httpRequest = buildRequest(workflowEndpoint(), request);

    try {
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return parseSuccess(response.body());
      }
      throw serviceException(response.statusCode(), response.body());
    } catch (HttpTimeoutException exception) {
      throw new AiProviderException(
          AiErrorType.TIMEOUT, PROVIDER, "Python WFM service timed out", exception);
    } catch (IOException exception) {
      throw new AiProviderException(
          AiErrorType.PROVIDER_UNAVAILABLE, PROVIDER, "Python WFM service is unavailable", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AiProviderException(
          AiErrorType.TIMEOUT, PROVIDER, "Python WFM service request was interrupted", exception);
    }
  }

  @Override
  public WfmServiceTestCaseGenerateResponse generateTestCases(WfmServiceTestCaseGenerateRequest request) {
    HttpRequest httpRequest = buildRequest(testCaseEndpoint(), request);

    try {
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return parseTestCaseSuccess(response.body());
      }
      throw serviceException(response.statusCode(), response.body());
    } catch (HttpTimeoutException exception) {
      throw new AiProviderException(
          AiErrorType.TIMEOUT, PROVIDER, "Python WFM service timed out", exception);
    } catch (IOException exception) {
      throw new AiProviderException(
          AiErrorType.PROVIDER_UNAVAILABLE, PROVIDER, "Python WFM service is unavailable", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AiProviderException(
          AiErrorType.TIMEOUT, PROVIDER, "Python WFM service request was interrupted", exception);
    }
  }

  HttpRequest buildRequest(WfmServiceGenerateRequest request) {
    return buildRequest(workflowEndpoint(), request);
  }

  HttpRequest buildTestCaseRequest(WfmServiceTestCaseGenerateRequest request) {
    return buildRequest(testCaseEndpoint(), request);
  }

  private HttpRequest buildRequest(URI endpoint, Object request) {
    return HttpRequest.newBuilder(endpoint)
        .version(HttpClient.Version.HTTP_1_1)
        .timeout(Duration.ofMillis(properties.timeoutMs()))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(serialize(request), StandardCharsets.UTF_8))
        .build();
  }

  private URI workflowEndpoint() {
    return URI.create(properties.baseUrl().replaceAll("/+$", "") + "/internal/workflow/generate");
  }

  private URI testCaseEndpoint() {
    return URI.create(properties.baseUrl().replaceAll("/+$", "") + "/internal/test-cases/generate");
  }

  String serialize(Object request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize WFM service request", exception);
    }
  }

  WfmServiceGenerateResponse parseSuccess(String responseBody) {
    try {
      WfmServiceGenerateResponse response =
          objectMapper.readValue(responseBody, WfmServiceGenerateResponse.class);
      if (response == null || response.wfm() == null || response.flowchart() == null) {
        throw new AiProviderException(
            AiErrorType.INVALID_RESPONSE, PROVIDER, "Python workflow engine returned an incomplete workflow");
      }
      return response;
    } catch (JsonProcessingException exception) {
      throw new AiProviderException(
          AiErrorType.INVALID_RESPONSE, PROVIDER, "Python WFM service response is invalid", exception);
    }
  }

  WfmServiceTestCaseGenerateResponse parseTestCaseSuccess(String responseBody) {
    try {
      WfmServiceTestCaseGenerateResponse response =
          objectMapper.readValue(responseBody, WfmServiceTestCaseGenerateResponse.class);
      if (response == null || response.testCaseSet() == null) {
        throw new AiProviderException(
            AiErrorType.INVALID_RESPONSE, PROVIDER, "Python workflow engine returned incomplete test cases");
      }
      return response;
    } catch (JsonProcessingException exception) {
      throw new AiProviderException(
          AiErrorType.INVALID_RESPONSE, PROVIDER, "Python WFM service test case response is invalid", exception);
    }
  }

  AiProviderException serviceException(int statusCode, String responseBody) {
    PythonWfmError error = parseError(responseBody);
    LOGGER.warn(
        "Python WFM service failed status={} code={} message={} details={}",
        statusCode,
        error.code(),
        error.message(),
        error.detailsSummary());
    return new AiProviderException(errorType(statusCode), PROVIDER, userMessage(statusCode, error));
  }

  private PythonWfmError parseError(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return new PythonWfmError("WFM_SERVICE_ERROR", "Python WFM service returned an error", null);
    }
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode error = root.path("error");
      String code = textOrDefault(error.path("code"), "WFM_SERVICE_ERROR");
      String message = textOrDefault(error.path("message"), "Python WFM service returned an error");
      return new PythonWfmError(code, message, detailsSummary(error.path("details")));
    } catch (JsonProcessingException exception) {
      return new PythonWfmError("WFM_SERVICE_ERROR", "Python WFM service returned an unreadable error", null);
    }
  }

  private String detailsSummary(JsonNode details) {
    if (details == null || details.isMissingNode() || details.isNull()) {
      return null;
    }
    List<String> entries = new ArrayList<>();
    collectIssueSummaries(details.path("errors"), entries);
    if (entries.isEmpty()) {
      collectIssueSummaries(details.path("warnings"), entries);
    }
    if (entries.isEmpty() && details.path("reason").isTextual()) {
      entries.add(details.path("reason").asText());
    }
    if (entries.isEmpty()) {
      return null;
    }
    return String.join("; ", entries.stream().limit(3).toList());
  }

  private void collectIssueSummaries(JsonNode issues, List<String> entries) {
    if (issues == null || !issues.isArray()) {
      return;
    }
    for (JsonNode issue : issues) {
      if (entries.size() >= 3) {
        return;
      }
      if (issue.isTextual()) {
        entries.add(issue.asText());
        continue;
      }
      if (!issue.isObject()) {
        continue;
      }
      String path = textOrDefault(issue.path("path"), "");
      String code = textOrDefault(issue.path("code"), "");
      String message = textOrDefault(issue.path("message"), "");
      String summary = ("%s %s: %s".formatted(path, code, message)).trim();
      if (!summary.isBlank() && !summary.equals(":")) {
        entries.add(summary);
      }
    }
  }

  private String textOrDefault(JsonNode node, String fallback) {
    return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : fallback;
  }

  private AiErrorType errorType(int statusCode) {
    return switch (statusCode) {
      case 401 -> AiErrorType.INVALID_API_KEY;
      case 402 -> AiErrorType.PAYMENT_REQUIRED;
      case 408, 504 -> AiErrorType.TIMEOUT;
      case 422 -> AiErrorType.INVALID_RESPONSE;
      case 502, 503 -> AiErrorType.PROVIDER_UNAVAILABLE;
      default -> AiErrorType.UNKNOWN;
    };
  }

  private String userMessage(int statusCode, PythonWfmError error) {
    if (statusCode == 503) {
      return "Python WFM service or its LLM provider is unavailable. Please retry later.";
    }
    if (statusCode == 422 && error.detailsSummary() != null && !error.detailsSummary().isBlank()) {
      return error.message() + ": " + error.detailsSummary();
    }
    return error.message();
  }

  private record PythonWfmError(String code, String message, String detailsSummary) {}
}
