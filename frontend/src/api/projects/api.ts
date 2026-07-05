import { axiosClient } from "@/lib/axios";
import type {
  ProjectCreatePayload,
  ProjectDTO,
  RequirementCreatePayload,
  RequirementResourceDTO,
  RequirementTestCasesResponseDTO,
  RequirementUpdatePayload,
  SavedRequirementGenerationResponseDTO,
} from "@/types/project.types";

const SAVED_FLOW_GENERATION_TIMEOUT_MS = 180000;

export async function listProjects(): Promise<ProjectDTO[]> {
  const response = await axiosClient.get<ProjectDTO[]>("/api/projects");
  return response.data;
}

export async function createProject(payload: ProjectCreatePayload): Promise<ProjectDTO> {
  const response = await axiosClient.post<ProjectDTO>("/api/projects", payload);
  return response.data;
}

export async function updateProject(
  projectId: string,
  payload: ProjectCreatePayload,
): Promise<ProjectDTO> {
  const response = await axiosClient.put<ProjectDTO>(`/api/projects/${projectId}`, payload);
  return response.data;
}

export async function deleteProject(projectId: string): Promise<void> {
  await axiosClient.delete(`/api/projects/${projectId}`);
}

export async function listRequirements(projectId: string): Promise<RequirementResourceDTO[]> {
  const response = await axiosClient.get<RequirementResourceDTO[]>(
    `/api/projects/${projectId}/requirements`,
  );
  return response.data;
}

export async function createRequirement(
  projectId: string,
  payload: RequirementCreatePayload,
): Promise<RequirementResourceDTO> {
  const response = await axiosClient.post<RequirementResourceDTO>(
    `/api/projects/${projectId}/requirements`,
    payload,
  );
  return response.data;
}

export async function updateRequirement(
  requirementId: string,
  payload: RequirementUpdatePayload,
): Promise<RequirementResourceDTO> {
  const response = await axiosClient.put<RequirementResourceDTO>(
    `/api/requirements/${requirementId}`,
    payload,
  );
  return response.data;
}

export async function deleteRequirement(requirementId: string): Promise<void> {
  await axiosClient.delete(`/api/requirements/${requirementId}`);
}

export async function generateRequirementFlow(
  requirementId: string,
): Promise<SavedRequirementGenerationResponseDTO> {
  const response = await axiosClient.post<SavedRequirementGenerationResponseDTO>(
    `/api/requirements/${requirementId}/generate-flow`,
    undefined,
    { timeout: SAVED_FLOW_GENERATION_TIMEOUT_MS },
  );
  return response.data;
}

export async function generateRequirementTestCases(
  requirementId: string,
): Promise<RequirementTestCasesResponseDTO> {
  const response = await axiosClient.post<RequirementTestCasesResponseDTO>(
    `/api/requirements/${requirementId}/generate-test-cases`,
    undefined,
    { timeout: SAVED_FLOW_GENERATION_TIMEOUT_MS },
  );
  return response.data;
}

export async function getRequirementTestCases(
  requirementId: string,
): Promise<RequirementTestCasesResponseDTO> {
  const response = await axiosClient.get<RequirementTestCasesResponseDTO>(
    `/api/requirements/${requirementId}/test-cases`,
  );
  return response.data;
}
