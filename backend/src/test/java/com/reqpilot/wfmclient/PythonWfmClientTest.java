package com.reqpilot.wfmclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.ai.AiErrorType;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.config.WfmServiceProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;

class PythonWfmClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serializesExpectedRequestBody() throws Exception {
    PythonWfmClient client = client();

    String requestBody =
        client.serialize(
            new WfmServiceGenerateRequest(
                "Feature: Login",
                new WfmServiceContext("project-1", "en", "auth"),
                new WfmServiceOptions("AI", "2.0", "deepseek/deepseek-chat", 0.2)));

    JsonNode body = objectMapper.readTree(requestBody);
    assertThat(body.path("requirement").asText()).isEqualTo("Feature: Login");
    assertThat(body.path("context").path("projectId").asText()).isEqualTo("project-1");
    assertThat(body.path("options").path("generationMode").asText()).isEqualTo("AI");
    assertThat(body.path("options").path("wfmVersion").asText()).isEqualTo("2.0");
    assertThat(body.path("options").path("model").asText()).isEqualTo("deepseek/deepseek-chat");
  }

  @Test
  void buildsHttp11RequestForUvicornCompatibility() {
    PythonWfmClient client = client();

    HttpRequest request =
        client.buildRequest(
            new WfmServiceGenerateRequest(
                "Feature: Login",
                new WfmServiceContext("project-1", "en", "auth"),
                new WfmServiceOptions("AI", "2.0", "deepseek/deepseek-chat", 0.2)));

    assertThat(request.version()).contains(HttpClient.Version.HTTP_1_1);
    assertThat(request.uri().toString()).isEqualTo("http://localhost:8001/internal/workflow/generate");
  }

  @Test
  void parsesWfmResponse() {
    PythonWfmClient client = client();

    WfmServiceGenerateResponse response = client.parseSuccess(validResponse());

    assertThat(response.wfm().path("schemaVersion").asText()).isEqualTo("1.0");
    assertThat(response.wfm().path("ast").path("nodes").get(0).path("id").asText()).isEqualTo("N1");
    assertThat(response.flowchart().nodes()).hasSize(2);
    assertThat(response.flowchart().edges()).hasSize(1);
    assertThat(response.metadata().promptVersion()).isEqualTo("wfm-v1-python-001");
    assertThat(response.metadata().flowchartSource()).isEqualTo("python-flowchart-mapper");
  }

  @Test
  void mapsPythonServiceErrorToAiProviderException() {
    PythonWfmClient client = client();

    AiProviderException exception =
        client.serviceException(
            503,
            """
        {
          "error": {
            "code": "WFM_GENERATION_FAILED",
            "message": "Unable to generate WFM from LLM provider",
            "details": {}
          }
        }
        """);

    assertThat(exception.errorType()).isEqualTo(AiErrorType.PROVIDER_UNAVAILABLE);
  }

  @Test
  void mapsPythonValidationErrorToInvalidResponse() {
    PythonWfmClient client = client();

    AiProviderException exception =
        client.serviceException(
            422,
            """
        {
          "error": {
            "code": "WFM_VALIDATION_FAILED",
            "message": "Generated WFM does not match WFM v1",
            "details": {"errors": ["$.schemaVersion must be 1.0"]}
          }
        }
        """);

    assertThat(exception.errorType()).isEqualTo(AiErrorType.INVALID_RESPONSE);
    assertThat(exception.getMessage()).contains("Generated WFM");
  }

  @Test
  void includesPythonValidationDetailsInInvalidResponseMessage() {
    PythonWfmClient client = client();

    AiProviderException exception =
        client.serviceException(
            422,
            """
        {
          "error": {
            "code": "WFM_VALIDATION_FAILED",
            "message": "Generated WFM does not match WFM v2",
            "details": {
              "errors": [
                {
                  "code": "CYCLE_REQUIRES_LOOP_MARKER",
                  "message": "Cycle detected without data.loop = true",
                  "path": "transitions"
                }
              ]
            }
          }
        }
        """);

    assertThat(exception.errorType()).isEqualTo(AiErrorType.INVALID_RESPONSE);
    assertThat(exception.getMessage()).contains("Generated WFM does not match WFM v2");
    assertThat(exception.getMessage()).contains("CYCLE_REQUIRES_LOOP_MARKER");
    assertThat(exception.getMessage()).contains("data.loop = true");
  }

  private PythonWfmClient client() {
    return new PythonWfmClient(
        new WfmServiceProperties("http://localhost:8001", 5000),
        objectMapper,
        HttpClient.newHttpClient());
  }

  private String validResponse() {
    return """
        {
          "wfm": {
            "schemaVersion": "1.0",
            "modelType": "WORKFLOW_AST",
            "workflow": {"id": "login", "title": "Login", "language": "en"},
            "extensions": {"nodeKinds": [], "transitionKinds": []},
            "ast": {
              "actors": [],
              "variables": [],
              "nodes": [
                {"id": "N1", "role": "START", "kind": "START", "title": "Start"},
                {"id": "N2", "role": "END", "kind": "END", "title": "End"}
              ],
              "transitions": [
                {"id": "T1", "from": "N1", "to": "N2", "semantic": "DEFAULT"}
              ],
              "annotations": []
            }
          },
          "flowchart": {
            "nodes": [
              {"id": "N1", "label": "Start", "type": "START"},
              {"id": "N2", "label": "End", "type": "END"}
            ],
            "edges": [
              {"id": "T1", "source": "N1", "target": "N2", "label": null, "type": "DEFAULT"}
            ],
            "mermaid": "flowchart LR\\n  N1([\\"Start\\"])\\n  N1 --> N2\\n"
          },
          "metadata": {
            "model": "deepseek/deepseek-chat",
            "promptVersion": "wfm-v1-python-001",
            "generationMode": "AI",
            "wfmSource": "python-ai-generator",
            "flowchartSource": "python-flowchart-mapper",
            "validationStatus": "PASSED",
            "normalizationStatus": "PASSED",
            "mappingStatus": "PASSED",
            "warnings": []
          }
        }
        """;
  }
}
