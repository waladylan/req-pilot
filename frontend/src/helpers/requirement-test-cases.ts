import type {
  RequirementResourceDTO,
  RequirementTestCasesResponseDTO,
} from "@/types/project.types";

export function applyRequirementTestCaseResult(
  requirements: RequirementResourceDTO[],
  response: RequirementTestCasesResponseDTO,
): RequirementResourceDTO[] {
  const nextRequirement = mergeRequirementTestCaseResult(
    response.requirement,
    response,
  );
  let matched = false;

  const nextRequirements = requirements.map((requirement) => {
    if (requirement.id !== response.requirement.id) {
      return requirement;
    }

    matched = true;
    return mergeRequirementTestCaseResult(requirement, response);
  });

  return matched ? nextRequirements : [...nextRequirements, nextRequirement];
}

export function selectRequirementTestCaseSet(
  requirements: RequirementResourceDTO[],
  selectedRequirementId: string | null,
): RequirementResourceDTO["testCaseSet"] {
  return (
    requirements.find((requirement) => requirement.id === selectedRequirementId)?.testCaseSet ??
    null
  );
}

function mergeRequirementTestCaseResult(
  currentRequirement: RequirementResourceDTO,
  response: RequirementTestCasesResponseDTO,
): RequirementResourceDTO {
  const incoming = response.requirement;

  return {
    ...currentRequirement,
    ...incoming,
    flowchart: incoming.flowchart ?? currentRequirement.flowchart,
    metadata: incoming.metadata ?? currentRequirement.metadata,
    requirementText: incoming.requirementText ?? currentRequirement.requirementText,
    testCaseMetadata:
      response.metadata ?? incoming.testCaseMetadata ?? currentRequirement.testCaseMetadata,
    testCaseSet:
      response.testCaseSet ?? incoming.testCaseSet ?? currentRequirement.testCaseSet,
    testCasesGeneratedAt:
      incoming.testCasesGeneratedAt ?? currentRequirement.testCasesGeneratedAt,
    testCasesUpdatedAt: incoming.testCasesUpdatedAt ?? currentRequirement.testCasesUpdatedAt,
    wfm: incoming.wfm ?? currentRequirement.wfm,
    wfmVersion: incoming.wfmVersion ?? currentRequirement.wfmVersion,
  };
}
