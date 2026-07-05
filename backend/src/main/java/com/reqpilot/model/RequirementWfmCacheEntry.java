package com.reqpilot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requirement_wfm_cache")
public class RequirementWfmCacheEntry {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, length = 128, unique = true)
  private String cacheKey;

  @Column(nullable = false, length = 128)
  private String normalizedRequirementHash;

  @Column(nullable = false, length = 32)
  private String analyzerMode;

  @Column(length = 32)
  private String provider;

  @Column(length = 128)
  private String model;

  @Column(nullable = false, length = 128)
  private String promptVersion;

  @Column(nullable = false, columnDefinition = "text")
  private String wfmJson;

  @Column(columnDefinition = "text")
  private String metadataJson;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column
  private Instant expiresAt;

  protected RequirementWfmCacheEntry() {}

  public RequirementWfmCacheEntry(
      String cacheKey,
      String normalizedRequirementHash,
      String analyzerMode,
      String provider,
      String model,
      String promptVersion,
      String wfmJson,
      String metadataJson,
      Instant expiresAt) {
    this.cacheKey = cacheKey;
    this.normalizedRequirementHash = normalizedRequirementHash;
    this.analyzerMode = analyzerMode;
    this.provider = provider;
    this.model = model;
    this.promptVersion = promptVersion;
    this.wfmJson = wfmJson;
    this.metadataJson = metadataJson;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public String getCacheKey() {
    return cacheKey;
  }

  public String getWfmJson() {
    return wfmJson;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void updateWfm(String wfmJson, String metadataJson, Instant expiresAt) {
    this.wfmJson = wfmJson;
    this.metadataJson = metadataJson;
    this.expiresAt = expiresAt;
    this.updatedAt = Instant.now();
  }
}
