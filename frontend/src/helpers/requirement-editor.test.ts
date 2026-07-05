import { describe, expect, it } from "vitest";

import { SAMPLE_REQUIREMENTS } from "@/constants";
import {
  EMPTY_REQUIREMENT_MESSAGE,
  getManualRequirementSelection,
  getRequirementGenerateInput,
  getRequirementValidationMessage,
  REQUIREMENT_TEXTAREA_PLACEHOLDER,
  selectRequirementSample,
  shouldEnableRequirementGenerate,
} from "@/helpers/requirement-editor";

describe("requirement editor helpers", () => {
  it("selects a sample and returns its editable requirement text", () => {
    const selected = selectRequirementSample(SAMPLE_REQUIREMENTS, "1");

    expect(selected).toEqual({
      requirementText: SAMPLE_REQUIREMENTS[1].value,
      selectedSampleIndex: "1",
    });
  });

  it("rejects invalid sample selections", () => {
    expect(selectRequirementSample(SAMPLE_REQUIREMENTS, "")).toBeUndefined();
    expect(selectRequirementSample(SAMPLE_REQUIREMENTS, "custom")).toBeUndefined();
    expect(selectRequirementSample(SAMPLE_REQUIREMENTS, "999")).toBeUndefined();
  });

  it("marks manually edited text as a custom requirement selection", () => {
    expect(getManualRequirementSelection()).toBe("");
  });

  it("enables Generate only for non-empty editable requirement text", () => {
    expect(shouldEnableRequirementGenerate("Create a purchase request", false)).toBe(true);
    expect(shouldEnableRequirementGenerate("  ", false)).toBe(false);
    expect(shouldEnableRequirementGenerate("Create a purchase request", true)).toBe(false);
  });

  it("uses the current editable requirement text as the Generate input", () => {
    expect(getRequirementGenerateInput("  Custom requirement  ")).toBe("Custom requirement");
    expect(getRequirementGenerateInput("   ")).toBeUndefined();
  });

  it("returns validation copy for empty custom requirements", () => {
    expect(REQUIREMENT_TEXTAREA_PLACEHOLDER).toBe("Enter your requirement here...");
    expect(getRequirementValidationMessage("")).toBe(EMPTY_REQUIREMENT_MESSAGE);
    expect(getRequirementValidationMessage("   ")).toBe(EMPTY_REQUIREMENT_MESSAGE);
    expect(getRequirementValidationMessage("Feature: Login")).toBeUndefined();
  });
});
