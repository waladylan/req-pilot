package com.reqpilot.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(
    String provider,
    String model,
    String apiKey,
    int timeoutMs,
    int maxOutputTokens,
    double temperature,
    boolean fallbackToRuleBased,
    Cache cache,
    String promptVersion,
    OpenRouter openrouter) {

  public AiProperties {
    if (provider == null || provider.isBlank()) {
      provider = "openrouter";
    }
    if (model == null || model.isBlank()) {
      model = "deepseek/deepseek-chat";
    }
    if (timeoutMs <= 0) {
      timeoutMs = 60000;
    }
    if (maxOutputTokens <= 0) {
      maxOutputTokens = 4096;
    }
    if (temperature < 0) {
      temperature = 0.2;
    }
    if (cache == null) {
      cache = new Cache(true, 30);
    }
    if (promptVersion == null || promptVersion.isBlank()) {
      promptVersion = "requirement-to-wfm-v1";
    }
    if (openrouter == null) {
      openrouter = new OpenRouter(null, null, null, null, null, null, null);
    }
  }

  public String effectiveProvider() {
    return provider == null || provider.isBlank() ? "openrouter" : provider.trim();
  }

  public String effectiveModel() {
    if ("openrouter".equalsIgnoreCase(effectiveProvider())) {
      return openrouter.defaultModel();
    }
    return model;
  }

  public int effectiveMaxTokens() {
    if ("openrouter".equalsIgnoreCase(effectiveProvider())) {
      return openrouter.maxCompletionTokens();
    }
    return maxOutputTokens;
  }

  public double effectiveTemperature() {
    if ("openrouter".equalsIgnoreCase(effectiveProvider())) {
      return openrouter.temperature();
    }
    return temperature;
  }

  public int effectiveTimeoutMs() {
    if ("openrouter".equalsIgnoreCase(effectiveProvider())) {
      return openrouter.timeoutSeconds() * 1000;
    }
    return timeoutMs;
  }

  public record Cache(boolean enabled, int ttlDays) {

    public Cache {
      if (ttlDays <= 0) {
        ttlDays = 30;
      }
    }
  }

  public record OpenRouter(
      String baseUrl,
      String apiKey,
      String defaultModel,
      List<String> fallbackModels,
      Double temperature,
      Integer maxCompletionTokens,
      Integer timeoutSeconds) {

    public OpenRouter {
      if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "https://openrouter.ai/api/v1";
      }
      if (defaultModel == null || defaultModel.isBlank()) {
        defaultModel = "deepseek/deepseek-chat";
      }
      fallbackModels =
          fallbackModels == null
              ? List.of("qwen/qwen3-32b:nitro", "deepseek/deepseek-chat-v3-0324")
              : fallbackModels.stream().filter((model) -> model != null && !model.isBlank()).map(String::trim).toList();
      if (temperature == null || temperature < 0) {
        temperature = 0.2;
      }
      if (maxCompletionTokens == null || maxCompletionTokens <= 0) {
        maxCompletionTokens = 4096;
      }
      if (timeoutSeconds == null || timeoutSeconds <= 0) {
        timeoutSeconds = 60;
      }
    }
  }
}
