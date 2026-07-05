export type RequirementSample = {
  label: string;
  value: string;
};

export const CUSTOM_REQUIREMENT_SAMPLE_KEY = "";
export const REQUIREMENT_TEXTAREA_PLACEHOLDER = "Enter your requirement here...";
export const EMPTY_REQUIREMENT_MESSAGE = "Enter a requirement to generate a flow.";

export function selectRequirementSample(
  samples: readonly RequirementSample[],
  sampleIndex: string,
): { requirementText: string; selectedSampleIndex: string } | undefined {
  if (sampleIndex.trim() === "") {
    return undefined;
  }

  const selected = Number(sampleIndex);
  if (!Number.isInteger(selected) || selected < 0) {
    return undefined;
  }

  const sample = samples[selected];
  if (!sample) {
    return undefined;
  }

  return {
    requirementText: sample.value,
    selectedSampleIndex: sampleIndex,
  };
}

export function getManualRequirementSelection(): string {
  return CUSTOM_REQUIREMENT_SAMPLE_KEY;
}

export function shouldEnableRequirementGenerate(
  requirementText: string,
  isGenerating: boolean,
): boolean {
  return requirementText.trim().length > 0 && !isGenerating;
}

export function getRequirementGenerateInput(requirementText: string): string | undefined {
  const trimmedRequirement = requirementText.trim();
  return trimmedRequirement.length > 0 ? trimmedRequirement : undefined;
}

export function getRequirementValidationMessage(requirementText: string): string | undefined {
  return requirementText.trim() ? undefined : EMPTY_REQUIREMENT_MESSAGE;
}
