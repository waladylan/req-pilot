import { beforeEach, describe, expect, it, vi, type Mock } from "vitest";

import { axiosClient } from "@/lib/axios";

import {
  createProject,
  createRequirement,
  generateRequirementFlow,
  generateRequirementTestCases,
  getRequirementTestCases,
  listProjects,
  listRequirements,
  updateRequirement,
} from "./api";

vi.mock("@/lib/axios", () => ({
  axiosClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe("project and saved requirement API client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists projects through the project API", async () => {
    getMock().mockResolvedValueOnce({ data: [{ id: "project-1", name: "Default Project" }] });

    await expect(listProjects()).resolves.toEqual([{ id: "project-1", name: "Default Project" }]);

    expect(getMock()).toHaveBeenCalledWith("/api/projects");
  });

  it("creates a project with name and description", async () => {
    postMock().mockResolvedValueOnce({ data: { id: "project-1", name: "Commerce" } });

    await createProject({ description: "Checkout flows", name: "Commerce" });

    expect(postMock()).toHaveBeenCalledWith("/api/projects", {
      description: "Checkout flows",
      name: "Commerce",
    });
  });

  it("lists requirements for a selected project", async () => {
    getMock().mockResolvedValueOnce({ data: [{ id: "requirement-1", title: "Login" }] });

    await listRequirements("project-1");

    expect(getMock()).toHaveBeenCalledWith("/api/projects/project-1/requirements");
  });

  it("creates and updates requirements through saved requirement endpoints", async () => {
    postMock().mockResolvedValueOnce({ data: { id: "requirement-1", title: "Login" } });
    putMock().mockResolvedValueOnce({ data: { id: "requirement-1", title: "Login" } });

    await createRequirement("project-1", {
      requirementText: "User logs in",
      title: "Login",
    });
    await updateRequirement("requirement-1", {
      requirementText: "User enters credentials",
    });

    expect(postMock()).toHaveBeenCalledWith("/api/projects/project-1/requirements", {
      requirementText: "User logs in",
      title: "Login",
    });
    expect(putMock()).toHaveBeenCalledWith("/api/requirements/requirement-1", {
      requirementText: "User enters credentials",
    });
  });

  it("generates flow for a saved requirement with the long-running timeout", async () => {
    postMock().mockResolvedValueOnce({
      data: {
        flowchart: { edges: [], mermaid: "flowchart LR\n", nodes: [] },
        metadata: { generationMode: "AI" },
        requirement: { id: "requirement-1" },
        wfm: { wfmVersion: "2.0" },
      },
    });

    await generateRequirementFlow("requirement-1");

    expect(postMock()).toHaveBeenCalledWith(
      "/api/requirements/requirement-1/generate-flow",
      undefined,
      { timeout: 180000 },
    );
  });

  it("generates and loads test cases for a saved requirement", async () => {
    postMock().mockResolvedValueOnce({
      data: {
        metadata: { generationStatus: "PASSED" },
        requirement: { id: "requirement-1" },
        testCaseSet: { testCases: [{ id: "TC-001" }] },
      },
    });
    getMock().mockResolvedValueOnce({
      data: {
        metadata: { generationStatus: "PASSED" },
        requirement: { id: "requirement-1" },
        testCaseSet: { testCases: [{ id: "TC-001" }] },
      },
    });

    await generateRequirementTestCases("requirement-1");
    await getRequirementTestCases("requirement-1");

    expect(postMock()).toHaveBeenCalledWith(
      "/api/requirements/requirement-1/generate-test-cases",
      undefined,
      { timeout: 180000 },
    );
    expect(getMock()).toHaveBeenCalledWith("/api/requirements/requirement-1/test-cases");
  });
});

function getMock() {
  return axiosClient.get as Mock;
}

function postMock() {
  return axiosClient.post as Mock;
}

function putMock() {
  return axiosClient.put as Mock;
}
