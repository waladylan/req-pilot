package com.reqpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.config.AiProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenRouterProviderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenRouterProvider provider = new OpenRouterProvider(aiProperties(), objectMapper, null);

  @Test
  void parsesSuccessfulResponseWithUsage() {
    AiResponse response =
        provider.parseSuccessfulResponse(
            """
            {
              "id": "gen-1",
              "model": "deepseek/deepseek-chat",
              "choices": [{"message": {"content": "{\\"summary\\":\\"ok\\"}"}}],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 20,
                "total_tokens": 30
              }
            }
            """,
            "fallback-model");

    assertThat(response.provider()).isEqualTo("OPENROUTER");
    assertThat(response.model()).isEqualTo("deepseek/deepseek-chat");
    assertThat(response.promptTokens()).isEqualTo(10);
    assertThat(response.completionTokens()).isEqualTo(20);
    assertThat(response.totalTokens()).isEqualTo(30);
    assertThat(response.rawResponseId()).isEqualTo("gen-1");
  }

  @Test
  void mapsEmptyResponseToInvalidResponse() {
    assertThatThrownBy(
            () ->
                provider.parseSuccessfulResponse(
                    """
                    {"choices": [{"message": {"content": ""}}]}
                    """,
                    "fallback-model"))
        .isInstanceOf(AiProviderException.class)
        .satisfies(
            (exception) ->
                assertThat(((AiProviderException) exception).errorType())
                    .isEqualTo(AiErrorType.INVALID_RESPONSE));
  }

  @Test
  void mapsProviderRateLimitMetadata() {
    OpenRouterError error =
        new OpenRouterError(
            429,
            "Provider returned error",
            Map.of("error_type", "rate_limit_exceeded", "provider_code", "429"));

    assertThat(provider.errorType(429, error)).isEqualTo(AiErrorType.PROVIDER_RATE_LIMIT);
  }

  @Test
  void mapsOpenRouterRateLimitWhenMetadataIsMissing() {
    assertThat(provider.errorType(429, new OpenRouterError(429, "Rate limit", Map.of())))
        .isEqualTo(AiErrorType.OPENROUTER_RATE_LIMIT);
  }

  @Test
  void requestBodyUsesModelsArrayWhenFallbackModelsAreConfigured() throws Exception {
    AiProperties properties = aiProperties();
    List<String> models =
        List.of(
            properties.openrouter().defaultModel(),
            properties.openrouter().fallbackModels().get(0),
            properties.openrouter().fallbackModels().get(1));

    JsonNode body =
        objectMapper.readTree(
            provider.requestBody(
                new AiRequest("TEST", "system", "user", properties.openrouter().defaultModel(), null, null),
                models));

    assertThat(body.has("models")).isTrue();
    assertThat(body.has("model")).isFalse();
    assertThat(body.path("models").path(0).asText()).isEqualTo("deepseek/deepseek-chat");
    assertThat(body.path("models").path(1).asText()).isEqualTo("qwen/qwen3-32b:nitro");
    assertThat(body.path("models").path(2).asText()).isEqualTo("deepseek/deepseek-chat-v3-0324");
  }

  @Test
  void requestBodyUsesSingleModelWhenFallbackModelsAreEmpty() throws Exception {
    JsonNode body =
        objectMapper.readTree(
            provider.requestBody(
                new AiRequest("TEST", "system", "user", "deepseek/deepseek-chat", null, null),
                List.of("deepseek/deepseek-chat")));

    assertThat(body.has("model")).isTrue();
    assertThat(body.has("models")).isFalse();
    assertThat(body.path("model").asText()).isEqualTo("deepseek/deepseek-chat");
  }

  private AiProperties aiProperties() {
    return new AiProperties(
        "openrouter",
        "deepseek/deepseek-chat",
        "",
        60000,
        4096,
        0.2,
        true,
        new AiProperties.Cache(true, 30),
        "requirement-to-wfm-v1",
        new AiProperties.OpenRouter(
            "https://openrouter.ai/api/v1",
            "test-key",
            "deepseek/deepseek-chat",
            List.of("qwen/qwen3-32b:nitro", "deepseek/deepseek-chat-v3-0324"),
            0.2,
            4096,
            60));
  }
}
