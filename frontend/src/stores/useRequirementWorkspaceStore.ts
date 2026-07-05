import { create } from "zustand";

import { SAMPLE_REQUIREMENTS } from "@/constants";
import type { FlowchartDTO, TestCaseDTO, WorkflowCanvasViewState } from "@/types/requirement";

interface RequirementWorkspaceState {
  canvasViewState?: WorkflowCanvasViewState;
  requirement: string;
  flowchart?: FlowchartDTO;
  testCases: TestCaseDTO[];
  wfm?: unknown;
  setCanvasViewState: (canvasViewState?: WorkflowCanvasViewState) => void;
  setRequirement: (requirement: string) => void;
  setFlowchart: (flowchart?: FlowchartDTO) => void;
  setTestCases: (testCases: TestCaseDTO[]) => void;
  setWfm: (wfm?: unknown) => void;
  resetGeneratedArtifacts: () => void;
}

export const useRequirementWorkspaceStore = create<RequirementWorkspaceState>((set) => ({
  canvasViewState: undefined,
  requirement: SAMPLE_REQUIREMENTS[0]?.value ?? "",
  flowchart: undefined,
  testCases: [],
  wfm: undefined,
  setCanvasViewState: (canvasViewState) => set({ canvasViewState }),
  setRequirement: (requirement) => set({ requirement }),
  setFlowchart: (flowchart) => set({ flowchart }),
  setTestCases: (testCases) => set({ testCases }),
  setWfm: (wfm) => set({ wfm }),
  resetGeneratedArtifacts: () =>
    set({ canvasViewState: undefined, flowchart: undefined, testCases: [], wfm: undefined }),
}));
