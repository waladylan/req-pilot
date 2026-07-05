package com.reqpilot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "requirements",
    indexes = {
      @Index(name = "idx_requirements_project_id", columnList = "project_id"),
      @Index(name = "idx_requirements_created_at", columnList = "created_at"),
      @Index(name = "idx_requirements_order_index", columnList = "order_index"),
      @Index(name = "idx_requirements_status", columnList = "status")
    })
public class Requirement {

  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(name = "requirement_text", nullable = false, length = 10000)
  private String requirementText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RequirementStatus status = RequirementStatus.DRAFT;

  @Column(name = "order_index", nullable = false)
  private int orderIndex;

  @Column(name = "wfm_version", nullable = false, length = 16)
  private String wfmVersion = "2.0";

  @Column(name = "wfm_json", columnDefinition = "text")
  private String wfmJson;

  @Column(name = "flowchart_json", columnDefinition = "text")
  private String flowchartJson;

  @Column(name = "metadata_json", columnDefinition = "text")
  private String metadataJson;

  @Column(name = "test_cases_json", columnDefinition = "text")
  private String testCasesJson;

  @Column(name = "test_case_metadata_json", columnDefinition = "text")
  private String testCaseMetadataJson;

  @Column(name = "test_cases_generated_at")
  private Instant testCasesGeneratedAt;

  @Column(name = "test_cases_updated_at")
  private Instant testCasesUpdatedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Requirement() {}

  public Requirement(Project project, String title, String requirementText, int orderIndex) {
    this.project = project;
    this.title = title;
    this.requirementText = requirementText;
    this.orderIndex = orderIndex;
  }

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Project getProject() {
    return project;
  }

  public UUID getProjectId() {
    return project.getId();
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getRequirementText() {
    return requirementText;
  }

  public void setRequirementText(String requirementText) {
    this.requirementText = requirementText;
  }

  public RequirementStatus getStatus() {
    return status;
  }

  public void setStatus(RequirementStatus status) {
    this.status = status;
  }

  public int getOrderIndex() {
    return orderIndex;
  }

  public void setOrderIndex(int orderIndex) {
    this.orderIndex = orderIndex;
  }

  public String getWfmVersion() {
    return wfmVersion;
  }

  public void setWfmVersion(String wfmVersion) {
    this.wfmVersion = wfmVersion;
  }

  public String getWfmJson() {
    return wfmJson;
  }

  public void setWfmJson(String wfmJson) {
    this.wfmJson = wfmJson;
  }

  public String getFlowchartJson() {
    return flowchartJson;
  }

  public void setFlowchartJson(String flowchartJson) {
    this.flowchartJson = flowchartJson;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public String getTestCasesJson() {
    return testCasesJson;
  }

  public void setTestCasesJson(String testCasesJson) {
    this.testCasesJson = testCasesJson;
  }

  public String getTestCaseMetadataJson() {
    return testCaseMetadataJson;
  }

  public void setTestCaseMetadataJson(String testCaseMetadataJson) {
    this.testCaseMetadataJson = testCaseMetadataJson;
  }

  public Instant getTestCasesGeneratedAt() {
    return testCasesGeneratedAt;
  }

  public void setTestCasesGeneratedAt(Instant testCasesGeneratedAt) {
    this.testCasesGeneratedAt = testCasesGeneratedAt;
  }

  public Instant getTestCasesUpdatedAt() {
    return testCasesUpdatedAt;
  }

  public void setTestCasesUpdatedAt(Instant testCasesUpdatedAt) {
    this.testCasesUpdatedAt = testCasesUpdatedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
