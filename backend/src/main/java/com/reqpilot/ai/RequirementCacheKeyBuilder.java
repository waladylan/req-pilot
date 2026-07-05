package com.reqpilot.ai;

import com.reqpilot.config.AnalyzerMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RequirementCacheKeyBuilder {

  public RequirementCacheKey build(
      String requirement,
      AnalyzerMode analyzerMode,
      String provider,
      String model,
      String promptVersion) {
    String normalizedRequirement = normalizeRequirement(requirement);
    String requirementHash = sha256(normalizedRequirement);
    String keyPayload =
        String.join(
            "\n",
            normalizedRequirement,
            analyzerMode.name(),
            normalizePart(provider),
            normalizePart(model),
            normalizePart(promptVersion));
    return new RequirementCacheKey(sha256(keyPayload), requirementHash);
  }

  private String normalizeRequirement(String requirement) {
    return (requirement == null ? "" : requirement)
        .trim()
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  private String normalizePart(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte item : hash) {
        builder.append(String.format("%02x", item));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
