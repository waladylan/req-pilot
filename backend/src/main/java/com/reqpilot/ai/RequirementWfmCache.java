package com.reqpilot.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.config.AiProperties;
import com.reqpilot.config.AnalyzerMode;
import com.reqpilot.model.RequirementWfmCacheEntry;
import com.reqpilot.repository.RequirementWfmCacheRepository;
import com.reqpilot.wfm.WfmDocument;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementWfmCache {

  private final AiProperties aiProperties;
  private final ObjectMapper objectMapper;
  private final RequirementWfmCacheRepository repository;

  public RequirementWfmCache(
      AiProperties aiProperties, ObjectMapper objectMapper, RequirementWfmCacheRepository repository) {
    this.aiProperties = aiProperties;
    this.objectMapper = objectMapper;
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public Optional<WfmDocument> findValid(RequirementCacheKey cacheKey) {
    if (!aiProperties.cache().enabled()) {
      return Optional.empty();
    }

    return repository
        .findByCacheKey(cacheKey.value())
        .filter(this::notExpired)
        .map(this::deserializeWfm);
  }

  @Transactional
  public void save(
      RequirementCacheKey cacheKey,
      AnalyzerMode analyzerMode,
      String provider,
      String model,
      String promptVersion,
      WfmDocument wfm,
      String metadataJson) {
    if (!aiProperties.cache().enabled()) {
      return;
    }

    String wfmJson = serialize(wfm);
    Instant expiresAt = Instant.now().plus(aiProperties.cache().ttlDays(), ChronoUnit.DAYS);
    RequirementWfmCacheEntry entry =
        repository
            .findByCacheKey(cacheKey.value())
            .orElseGet(
                () ->
                    new RequirementWfmCacheEntry(
                        cacheKey.value(),
                        cacheKey.normalizedRequirementHash(),
                        analyzerMode.name(),
                        provider,
                        model,
                        promptVersion,
                        wfmJson,
                        metadataJson,
                        expiresAt));
    entry.updateWfm(wfmJson, metadataJson, expiresAt);
    repository.save(entry);
  }

  private boolean notExpired(RequirementWfmCacheEntry entry) {
    return entry.getExpiresAt() == null || entry.getExpiresAt().isAfter(Instant.now());
  }

  private WfmDocument deserializeWfm(RequirementWfmCacheEntry entry) {
    try {
      return objectMapper.readValue(entry.getWfmJson(), WfmDocument.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cached WFM JSON is invalid", exception);
    }
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize cache value", exception);
    }
  }
}
