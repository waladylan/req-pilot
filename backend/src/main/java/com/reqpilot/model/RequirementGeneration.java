package com.reqpilot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requirement_generations")
public class RequirementGeneration {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, length = 10000)
  private String requirementText;

  @Column(nullable = false, columnDefinition = "text")
  private String flowchartJson;

  @Column(nullable = false, columnDefinition = "text")
  private String mermaid;

  @Column(columnDefinition = "text")
  private String testCasesJson;

  @Column(nullable = false)
  private Instant createdAt;

  protected RequirementGeneration() {}

  private RequirementGeneration(
      String requirementText, String flowchartJson, String mermaid, String testCasesJson) {
    this.requirementText = requirementText;
    this.flowchartJson = flowchartJson;
    this.mermaid = mermaid;
    this.testCasesJson = testCasesJson;
    this.createdAt = Instant.now();
  }

  public static RequirementGeneration forFlow(
      String requirementText, String flowchartJson, String mermaid) {
    return new RequirementGeneration(requirementText, flowchartJson, mermaid, null);
  }

  public static RequirementGeneration forTestCases(
      String requirementText, String flowchartJson, String mermaid, String testCasesJson) {
    return new RequirementGeneration(requirementText, flowchartJson, mermaid, testCasesJson);
  }

  public UUID getId() {
    return id;
  }
}
