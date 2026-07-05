import type { Connection } from "@xyflow/react";
import type { FormEvent } from "react";
import { useCallback, useEffect, useState } from "react";

import {
  createProject,
  createRequirement,
  generateRequirementFlow,
  generateRequirementTestCases,
  listProjects,
  listRequirements,
  updateRequirement,
} from "@/api/projects/api";
import { CanvasStatusBar } from "@/components/canvas/CanvasStatusBar";
import { FloatingSidebar } from "@/components/canvas/FloatingSidebar";
import { FloatingToolbar } from "@/components/canvas/FloatingToolbar";
import { FlowCanvas } from "@/components/canvas/FlowCanvas";
import { FlowInspector } from "@/components/canvas/FlowInspector";
import { FlowLegend } from "@/components/canvas/FlowLegend";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import {
  DECISION_BRANCH_EDGE_OPTIONS,
  DEFAULT_EDGE_SEMANTIC,
  EDGE_REGISTRY,
  NODE_KIND_REGISTRY,
  NODE_TYPE_REGISTRY,
  SAMPLE_REQUIREMENTS,
} from "@/constants";
import { exportMermaid } from "@/helpers/export";
import {
  buildMermaidFromFlowchart,
  ensureFlowchartNodePositions,
  getEdgeSemanticFromSourceHandle,
} from "@/helpers/flowchart";
import {
  getManualRequirementSelection,
  getRequirementGenerateInput,
  selectRequirementSample,
  shouldEnableRequirementGenerate,
} from "@/helpers/requirement-editor";
import { applyRequirementTestCaseResult } from "@/helpers/requirement-test-cases";
import { wfmRoleToFlowNodeKind } from "@/helpers/react-flow-to-wfm";
import { useFlowState } from "@/hooks/useFlowState";
import { useKeyboardShortcuts } from "@/hooks/useKeyboardShortcuts";
import { useRequirementSync } from "@/hooks/useRequirementSync";
import { getApiErrorMessage } from "@/lib/axios";
import { useRequirementWorkspaceStore } from "@/stores/useRequirementWorkspaceStore";
import type { ProjectDTO, RequirementResourceDTO } from "@/types/project.types";
import type { FlowEdgeSemantic, FlowchartDTO, WfmNodeRole } from "@/types/requirement";

export default function RequirementPilotPage() {
  const [errorMessage, setErrorMessage] = useState("");
  const [warningMessages, setWarningMessages] = useState<string[]>([]);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [showLegend, setShowLegend] = useState(false);
  const [showMiniMap, setShowMiniMap] = useState(false);
  const [showBackground, setShowBackground] = useState(true);
  const [snapToGrid, setSnapToGrid] = useState(false);
  const [fitViewSignal, setFitViewSignal] = useState(0);
  const [selectedSampleIndex, setSelectedSampleIndex] = useState("0");
  const [pendingDecisionConnection, setPendingDecisionConnection] = useState<Connection | null>(
    null,
  );
  const [projects, setProjects] = useState<ProjectDTO[]>([]);
  const [requirements, setRequirements] = useState<RequirementResourceDTO[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [selectedRequirementId, setSelectedRequirementId] = useState<string | null>(null);
  const [isLoadingWorkspace, setIsLoadingWorkspace] = useState(false);
  const [isSavingRequirement, setIsSavingRequirement] = useState(false);
  const [isGeneratingSavedFlow, setIsGeneratingSavedFlow] = useState(false);
  const [isGeneratingTestCases, setIsGeneratingTestCases] = useState(false);
  const [isProjectDialogOpen, setIsProjectDialogOpen] = useState(false);
  const [isRequirementDialogOpen, setIsRequirementDialogOpen] = useState(false);
  const [projectName, setProjectName] = useState("");
  const [projectDialogError, setProjectDialogError] = useState("");
  const [newRequirementText, setNewRequirementText] = useState("");
  const [requirementDialogError, setRequirementDialogError] = useState("");
  const { flowchart, requirement, setFlowchart, setRequirement, setTestCases, setWfm } =
    useRequirementWorkspaceStore();
  const {
    flowDirty,
    lastSyncSource,
    markCanvasEdited,
    markFlowEdited,
    markRequirementAsSource,
    markRequirementEdited,
    markRequirementSaved,
    requirementDirty,
    syncStatus,
  } = useRequirementSync({
    resetGeneratedArtifacts: () => setTestCases([]),
    setRequirement,
  });

  const renderedFlowchart = flowchart;
  const selectedRequirement = requirements.find(
    (item) => item.id === selectedRequirementId,
  );
  const canGenerateFlow =
    Boolean(selectedRequirementId) &&
    shouldEnableRequirementGenerate(requirement, isGeneratingSavedFlow);
  const canGenerateTestCases =
    Boolean(selectedRequirementId) &&
    Boolean(selectedRequirement?.wfm) &&
    selectedRequirement?.wfmVersion === "2.0" &&
    !isGeneratingTestCases;

  const handleFlowchartChange = useCallback(
    (
      nextFlowchart: FlowchartDTO,
      options: {
        markFlowEdited?: boolean;
        resetTestCases?: boolean;
      } = {},
    ) => {
      setFlowchart(nextFlowchart);

      if (options.resetTestCases ?? true) {
        setTestCases([]);
      }

      if (options.markFlowEdited ?? true) {
        setSelectedSampleIndex("");
        markFlowEdited();
        return;
      }

      if (options.resetTestCases === false) {
        markCanvasEdited();
      }
    },
    [markCanvasEdited, markFlowEdited, setFlowchart, setTestCases],
  );

  const flowState = useFlowState({
    flowchart: renderedFlowchart,
    onFlowchartChange: handleFlowchartChange,
  });
  const clearFlowSelection = flowState.clearSelection;

  const applyRequirementSelection = useCallback(
    (nextRequirement?: RequirementResourceDTO) => {
      setSelectedRequirementId(nextRequirement?.id ?? null);
      setSelectedSampleIndex("");
      setRequirement(nextRequirement?.requirementText ?? "");
      setWfm(nextRequirement?.wfm);
      setTestCases([]);
      setWarningMessages(extractWarnings(nextRequirement?.metadata));

      const nextFlowchart = nextRequirement?.flowchart
        ? ensureFlowchartNodePositions(nextRequirement.flowchart)
        : undefined;
      setFlowchart(nextFlowchart);
      clearFlowSelection();
      markRequirementAsSource();

      if (nextFlowchart) {
        setFitViewSignal((value) => value + 1);
      }
    },
    [
      clearFlowSelection,
      markRequirementAsSource,
      setFlowchart,
      setRequirement,
      setTestCases,
      setWfm,
    ],
  );

  const loadProjectRequirements = useCallback(
    async (projectId: string, preferredRequirementId?: string) => {
      const nextRequirements = await listRequirements(projectId);
      setRequirements(nextRequirements);
      const nextRequirement =
        nextRequirements.find((item) => item.id === preferredRequirementId) ?? nextRequirements[0];
      applyRequirementSelection(nextRequirement);
    },
    [applyRequirementSelection],
  );

  useEffect(() => {
    let isCancelled = false;

    async function loadWorkspace() {
      setIsLoadingWorkspace(true);
      try {
        setErrorMessage("");
        const nextProjects = await listProjects();
        if (isCancelled) {
          return;
        }

        setProjects(nextProjects);
        const nextProject = nextProjects[0];
        setSelectedProjectId(nextProject?.id ?? null);

        if (nextProject) {
          const nextRequirements = await listRequirements(nextProject.id);
          if (isCancelled) {
            return;
          }
          setRequirements(nextRequirements);
          applyRequirementSelection(nextRequirements[0]);
        } else {
          setRequirements([]);
          applyRequirementSelection(undefined);
        }
      } catch (error) {
        if (!isCancelled) {
          setErrorMessage(getApiErrorMessage(error, "Unable to load projects."));
        }
      } finally {
        if (!isCancelled) {
          setIsLoadingWorkspace(false);
        }
      }
    }

    void loadWorkspace();

    return () => {
      isCancelled = true;
    };
  }, [applyRequirementSelection]);

  const handleAddNode = useCallback(
    (nodeRole: WfmNodeRole, position?: { x: number; y: number }) => {
      flowState.addNode(wfmRoleToFlowNodeKind(nodeRole), position);
    },
    [flowState],
  );

  const handleUpdateNodeTitle = useCallback(
    (nodeId: string, title: string) => {
      flowState.updateNodeLabel(nodeId, title);
    },
    [flowState],
  );

  const handleDeleteNode = useCallback(
    (nodeId: string) => {
      flowState.deleteNodeById(nodeId);
      flowState.clearSelection();
    },
    [flowState],
  );

  const handleDeleteEdge = useCallback(
    (edgeId: string) => {
      flowState.deleteEdgeById(edgeId);
      flowState.clearSelection();
    },
    [flowState],
  );

  const handleDeleteSelected = useCallback(() => {
    flowState.deleteSelected();
  }, [flowState]);

  useKeyboardShortcuts({
    deleteSelected: handleDeleteSelected,
    duplicateSelectedNode: flowState.duplicateSelectedNode,
    redo: flowState.redo,
    undo: flowState.undo,
  });

  const handleRequirementChange = (value: string) => {
    setSelectedSampleIndex(getManualRequirementSelection());
    markRequirementEdited(value, { resetArtifacts: false });
    setErrorMessage("");
    setWarningMessages([]);
  };

  const handleSidebarSampleChange = (sampleIndex: string) => {
    const selectedSample = selectRequirementSample(SAMPLE_REQUIREMENTS, sampleIndex);
    if (!selectedSample) {
      return;
    }

    setSelectedSampleIndex(selectedSample.selectedSampleIndex);
    markRequirementEdited(selectedSample.requirementText, { resetArtifacts: false });
    setErrorMessage("");
    setWarningMessages([]);
  };

  const handleProjectChange = async (projectId: string) => {
    if (!projectId || projectId === selectedProjectId) {
      return;
    }

    try {
      setErrorMessage("");
      setIsLoadingWorkspace(true);
      setSelectedProjectId(projectId);
      await loadProjectRequirements(projectId);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, "Unable to load project requirements."));
    } finally {
      setIsLoadingWorkspace(false);
    }
  };

  const handleRequirementSelect = (requirementId: string) => {
    const nextRequirement = requirements.find((item) => item.id === requirementId);
    if (!nextRequirement) {
      return;
    }

    setErrorMessage("");
    applyRequirementSelection(nextRequirement);
  };

  const openCreateProjectDialog = () => {
    setProjectName("");
    setProjectDialogError("");
    setIsProjectDialogOpen(true);
  };

  const handleCreateProjectSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedName = projectName.trim();
    if (!trimmedName) {
      setProjectDialogError("Project name is required.");
      return;
    }

    try {
      setErrorMessage("");
      setProjectDialogError("");
      setIsLoadingWorkspace(true);
      const nextProject = await createProject({ name: trimmedName });
      setProjects((items) => [...items, nextProject]);
      setSelectedProjectId(nextProject.id);
      setRequirements([]);
      applyRequirementSelection(undefined);
      setIsProjectDialogOpen(false);
      setProjectName("");
    } catch (error) {
      const message = getApiErrorMessage(error, "Unable to create project.");
      setProjectDialogError(message);
      setErrorMessage(message);
    } finally {
      setIsLoadingWorkspace(false);
    }
  };

  const openCreateRequirementDialog = () => {
    if (!selectedProjectId) {
      setErrorMessage("Create or select a project before adding a requirement.");
      return;
    }

    setNewRequirementText(requirement.trim() || SAMPLE_REQUIREMENTS[0]?.value || "");
    setRequirementDialogError("");
    setIsRequirementDialogOpen(true);
  };

  const handleCreateRequirementSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!selectedProjectId) {
      setRequirementDialogError("Create or select a project before adding a requirement.");
      return;
    }

    const requirementText = newRequirementText.trim();
    if (!requirementText) {
      setRequirementDialogError("Requirement text is required.");
      return;
    }

    try {
      setErrorMessage("");
      setRequirementDialogError("");
      setIsLoadingWorkspace(true);
      const nextRequirement = await createRequirement(selectedProjectId, {
        requirementText,
        title: suggestRequirementTitle(requirementText),
      });
      setRequirements((items) => sortRequirements([...items, nextRequirement]));
      applyRequirementSelection(nextRequirement);
      setIsRequirementDialogOpen(false);
      setNewRequirementText("");
    } catch (error) {
      const message = getApiErrorMessage(error, "Unable to create requirement.");
      setRequirementDialogError(message);
      setErrorMessage(message);
    } finally {
      setIsLoadingWorkspace(false);
    }
  };

  const handleSaveRequirement = async () => {
    if (!selectedRequirementId) {
      setErrorMessage("Select a requirement before saving.");
      return;
    }

    const requirementText = getRequirementGenerateInput(requirement);
    if (!requirementText) {
      setErrorMessage("Enter requirement text before saving.");
      return;
    }

    try {
      setErrorMessage("");
      setIsSavingRequirement(true);
      const savedRequirement = await updateRequirement(selectedRequirementId, {
        requirementText,
        title: selectedRequirement?.title ?? suggestRequirementTitle(requirementText),
      });
      setRequirements((items) => upsertRequirement(items, savedRequirement));
      setRequirement(savedRequirement.requirementText);
      markRequirementSaved();
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, "Unable to save requirement."));
    } finally {
      setIsSavingRequirement(false);
    }
  };

  const handleGenerateFlow = async () => {
    if (!selectedRequirementId) {
      setErrorMessage("Select or create a requirement before generating.");
      return;
    }

    const requirementForGenerate = getRequirementGenerateInput(requirement);
    if (!requirementForGenerate) {
      setErrorMessage("Enter a requirement before generating.");
      return;
    }

    try {
      setErrorMessage("");
      setWarningMessages([]);
      setIsGeneratingSavedFlow(true);
      await updateRequirement(selectedRequirementId, {
        requirementText: requirementForGenerate,
        title: selectedRequirement?.title ?? suggestRequirementTitle(requirementForGenerate),
      });
      const response = await generateRequirementFlow(selectedRequirementId);
      const nextFlowchart = ensureFlowchartNodePositions(response.flowchart);
      flowState.replaceFromRequirement(nextFlowchart);
      setRequirement(response.requirement.requirementText);
      setWfm(response.wfm);
      setRequirements((items) => upsertRequirement(items, response.requirement));
      markRequirementAsSource();
      setTestCases([]);
      setWarningMessages(extractWarnings(response.metadata));
      setFitViewSignal((value) => value + 1);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, "Unable to generate flow."));
    } finally {
      setIsGeneratingSavedFlow(false);
    }
  };

  const handleGenerateTestCases = async () => {
    if (!selectedRequirementId) {
      setErrorMessage("Select a requirement before generating test cases.");
      return;
    }
    if (!selectedRequirement?.wfm || selectedRequirement.wfmVersion !== "2.0") {
      setErrorMessage("Generate flow first before generating test cases.");
      return;
    }

    const previousRequirementText = requirement;
    const previousFlowchart = renderedFlowchart;
    const previousWfm = selectedRequirement.wfm;

    try {
      setErrorMessage("");
      setWarningMessages([]);
      setIsGeneratingTestCases(true);
      const response = await generateRequirementTestCases(selectedRequirementId);
      setRequirements((items) => applyRequirementTestCaseResult(items, response));
      setWarningMessages(extractWarnings(response.metadata));
      setRequirement(previousRequirementText);
      setWfm(previousWfm);
      setFlowchart(previousFlowchart);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, "Unable to generate test cases."));
    } finally {
      setIsGeneratingTestCases(false);
    }
  };

  const handleAutoLayout = () => {
    flowState.autoLayout();
    setFitViewSignal((value) => value + 1);
  };

  const handleConnectWithSemantic = useCallback(
    (connection: Connection, semantic: FlowEdgeSemantic) => {
      flowState.connectNodes(connection, semantic);
    },
    [flowState],
  );

  const handleConnect = useCallback(
    (connection: Connection) => {
      const semanticFromHandle = getEdgeSemanticFromSourceHandle(connection.sourceHandle);
      if (semanticFromHandle) {
        handleConnectWithSemantic(connection, semanticFromHandle);
        return;
      }

      const isDecisionSource = isDecisionFlowNode(connection.source, renderedFlowchart);

      if (isDecisionSource) {
        setPendingDecisionConnection(connection);
        return;
      }

      handleConnectWithSemantic(connection, DEFAULT_EDGE_SEMANTIC);
    },
    [handleConnectWithSemantic, renderedFlowchart],
  );

  const handleDecisionBranchSelect = (semantic: FlowEdgeSemantic) => {
    if (!pendingDecisionConnection) {
      return;
    }

    handleConnectWithSemantic(pendingDecisionConnection, semantic);
    setPendingDecisionConnection(null);
  };

  return (
    <div className="fixed inset-0 overflow-hidden bg-background text-foreground">
      <FlowCanvas
        fitViewSignal={fitViewSignal}
        flowchart={renderedFlowchart}
        onAddNode={handleAddNode}
        onClearSelection={flowState.clearSelection}
        onConnect={handleConnect}
        onEdgeSelect={flowState.selectEdge}
        onEdgesChange={flowState.applyEdgeChanges}
        onNodeLabelChange={handleUpdateNodeTitle}
        onNodeSelect={flowState.selectNode}
        onNodesChange={flowState.applyNodeChanges}
        selectedEdgeId={flowState.selectedEdgeId}
        showBackground={showBackground}
        showMiniMap={showMiniMap}
        snapToGrid={snapToGrid}
      />

      <FloatingToolbar
        canGenerateFlow={canGenerateFlow}
        canRedo={flowState.canRedo}
        canUndo={flowState.canUndo}
        hasFlow={Boolean(renderedFlowchart)}
        isGeneratingFlow={isGeneratingSavedFlow}
        onAutoLayout={handleAutoLayout}
        onExportMermaid={() =>
          renderedFlowchart && exportMermaid(buildMermaidFromFlowchart(renderedFlowchart))
        }
        onFitView={() => setFitViewSignal((value) => value + 1)}
        onGenerateFlow={handleGenerateFlow}
        onRedo={flowState.redo}
        onToggleLegend={() => setShowLegend((value) => !value)}
        onToggleSidebar={() => setIsSidebarOpen((value) => !value)}
        onUndo={flowState.undo}
      />

      <FloatingSidebar
        canGenerateFlow={canGenerateFlow}
        canGenerateTestCases={canGenerateTestCases}
        flowDirty={flowDirty}
        isGeneratingFlow={isGeneratingSavedFlow}
        isGeneratingTestCases={isGeneratingTestCases}
        isLoadingWorkspace={isLoadingWorkspace}
        isSavingRequirement={isSavingRequirement}
        isOpen={isSidebarOpen}
        lastSyncSource={lastSyncSource}
        onAddNode={handleAddNode}
        onCreateProject={openCreateProjectDialog}
        onCreateRequirement={openCreateRequirementDialog}
        onGenerateFlow={handleGenerateFlow}
        onGenerateTestCases={handleGenerateTestCases}
        onProjectChange={handleProjectChange}
        onRequirementChange={handleRequirementChange}
        onRequirementSelect={handleRequirementSelect}
        onSampleChange={handleSidebarSampleChange}
        onSaveRequirement={handleSaveRequirement}
        projects={projects}
        requirements={requirements}
        selectedProjectId={selectedProjectId}
        selectedRequirementId={selectedRequirementId}
        selectedSampleIndex={selectedSampleIndex}
        onToggle={() => setIsSidebarOpen((value) => !value)}
        requirement={requirement}
        requirementDirty={requirementDirty}
        setShowBackground={setShowBackground}
        setShowLegend={setShowLegend}
        setShowMiniMap={setShowMiniMap}
        setSnapToGrid={setSnapToGrid}
        showBackground={showBackground}
        showLegend={showLegend}
        showMiniMap={showMiniMap}
        snapToGrid={snapToGrid}
        syncStatus={syncStatus}
        testCaseSet={selectedRequirement?.testCaseSet}
      />

      <FlowInspector
        onClose={flowState.clearSelection}
        onDeleteEdge={handleDeleteEdge}
        onDeleteNode={handleDeleteNode}
        onUpdateEdgeLabel={flowState.updateEdgeLabel}
        onUpdateEdgeSemantic={flowState.updateEdgeSemantic}
        onUpdateNodeKind={flowState.updateNodeKind}
        onUpdateNodeLabel={handleUpdateNodeTitle}
        onUpdateNodeMetadata={flowState.updateNodeMetadata}
        selectedEdge={flowState.selectedEdge}
        selectedNode={flowState.selectedNode}
      />

      <FlowLegend onToggle={() => setShowLegend((value) => !value)} visible={showLegend} />

      <CreateProjectDialog
        errorMessage={projectDialogError}
        isSubmitting={isLoadingWorkspace}
        onNameChange={setProjectName}
        onOpenChange={(open) => {
          if (!isLoadingWorkspace) {
            setIsProjectDialogOpen(open);
          }
        }}
        onSubmit={handleCreateProjectSubmit}
        open={isProjectDialogOpen}
        projectName={projectName}
      />

      <CreateRequirementDialog
        errorMessage={requirementDialogError}
        isSubmitting={isLoadingWorkspace}
        onOpenChange={(open) => {
          if (!isLoadingWorkspace) {
            setIsRequirementDialogOpen(open);
          }
        }}
        onSubmit={handleCreateRequirementSubmit}
        onTextChange={setNewRequirementText}
        open={isRequirementDialogOpen}
        requirementText={newRequirementText}
      />

      {pendingDecisionConnection ? (
        <DecisionBranchMenu
          onCancel={() => setPendingDecisionConnection(null)}
          onSelect={handleDecisionBranchSelect}
        />
      ) : null}

      <CanvasStatusBar
        errorMessage={errorMessage}
        flowDirty={flowDirty}
        hasFlow={Boolean(renderedFlowchart)}
        requirementDirty={requirementDirty}
        syncStatus={syncStatus}
        warningMessage={warningMessages[0]}
      />
    </div>
  );
}

type CreateProjectDialogProps = {
  errorMessage: string;
  isSubmitting: boolean;
  onNameChange: (value: string) => void;
  onOpenChange: (open: boolean) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  open: boolean;
  projectName: string;
};

function CreateProjectDialog({
  errorMessage,
  isSubmitting,
  onNameChange,
  onOpenChange,
  onSubmit,
  open,
  projectName,
}: CreateProjectDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={onSubmit}>
          <DialogHeader>
            <DialogTitle>Create Project</DialogTitle>
            <DialogDescription>Add a workspace for a group of requirements.</DialogDescription>
          </DialogHeader>

          <div className="mt-5 space-y-2">
            <label className="block text-xs font-semibold text-muted-foreground" htmlFor="project-name">
              Project name
            </label>
            <Input
              autoFocus
              disabled={isSubmitting}
              id="project-name"
              onChange={(event) => onNameChange(event.target.value)}
              placeholder="Commerce Platform"
              value={projectName}
            />
          </div>

          {errorMessage ? (
            <div className="mt-3 rounded-md border border-destructive/25 bg-destructive/10 px-3 py-2 text-xs font-medium text-destructive">
              {errorMessage}
            </div>
          ) : null}

          <DialogFooter>
            <Button
              disabled={isSubmitting}
              onClick={() => onOpenChange(false)}
              type="button"
              variant="secondary"
            >
              Cancel
            </Button>
            <Button disabled={isSubmitting} icon={isSubmitting ? <Spinner /> : null} type="submit">
              Create Project
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

type CreateRequirementDialogProps = {
  errorMessage: string;
  isSubmitting: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onTextChange: (value: string) => void;
  open: boolean;
  requirementText: string;
};

function CreateRequirementDialog({
  errorMessage,
  isSubmitting,
  onOpenChange,
  onSubmit,
  onTextChange,
  open,
  requirementText,
}: CreateRequirementDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <form onSubmit={onSubmit}>
          <DialogHeader>
            <DialogTitle>Create Requirement</DialogTitle>
            <DialogDescription>Enter the requirement text for the selected project.</DialogDescription>
          </DialogHeader>

          <div className="mt-5 space-y-2">
            <label
              className="block text-xs font-semibold text-muted-foreground"
              htmlFor="requirement-text"
            >
              Requirement
            </label>
            <Textarea
              autoFocus
              className="min-h-56 resize-y rounded-md p-3"
              disabled={isSubmitting}
              id="requirement-text"
              onChange={(event) => onTextChange(event.target.value)}
              placeholder="User can create a purchase request. Manager approves. If amount > 5000, finance approval is required."
              value={requirementText}
            />
          </div>

          {errorMessage ? (
            <div className="mt-3 rounded-md border border-destructive/25 bg-destructive/10 px-3 py-2 text-xs font-medium text-destructive">
              {errorMessage}
            </div>
          ) : null}

          <DialogFooter>
            <Button
              disabled={isSubmitting}
              onClick={() => onOpenChange(false)}
              type="button"
              variant="secondary"
            >
              Cancel
            </Button>
            <Button disabled={isSubmitting} icon={isSubmitting ? <Spinner /> : null} type="submit">
              Create Requirement
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function isDecisionFlowNode(nodeId: string | null | undefined, flowchart?: FlowchartDTO): boolean {
  const sourceNode = flowchart?.nodes.find((node) => node.id === nodeId);
  return (
    sourceNode?.type === NODE_KIND_REGISTRY.DECISION.defaultType ||
    sourceNode?.nodeKind === NODE_TYPE_REGISTRY.DECISION.nodeKind
  );
}

function suggestRequirementTitle(requirementText: string): string {
  const firstTextLine = requirementText
    .split("\n")
    .map((line) => line.trim())
    .find(Boolean);

  if (!firstTextLine) {
    return "New Requirement";
  }

  return firstTextLine.replace(/^feature:\s*/i, "").slice(0, 80) || "New Requirement";
}

function sortRequirements(items: RequirementResourceDTO[]): RequirementResourceDTO[] {
  return [...items].sort((first, second) => {
    const orderDelta = first.orderIndex - second.orderIndex;
    if (orderDelta !== 0) {
      return orderDelta;
    }

    return first.createdAt.localeCompare(second.createdAt);
  });
}

function upsertRequirement(
  items: RequirementResourceDTO[],
  nextRequirement: RequirementResourceDTO,
): RequirementResourceDTO[] {
  const index = items.findIndex((item) => item.id === nextRequirement.id);
  if (index < 0) {
    return sortRequirements([...items, nextRequirement]);
  }

  return sortRequirements([
    ...items.slice(0, index),
    nextRequirement,
    ...items.slice(index + 1),
  ]);
}

function extractWarnings(metadata: unknown): string[] {
  if (!metadata || typeof metadata !== "object") {
    return [];
  }

  const candidate = metadata as {
    validationWarnings?: unknown;
    warnings?: unknown;
  };
  return [...stringArray(candidate.warnings), ...stringArray(candidate.validationWarnings)].filter(
    (value, index, values) => values.indexOf(value) === index,
  );
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
    : [];
}

type DecisionBranchMenuProps = {
  onCancel: () => void;
  onSelect: (semantic: FlowEdgeSemantic) => void;
};

function DecisionBranchMenu({ onCancel, onSelect }: DecisionBranchMenuProps) {
  return (
    <div className="pointer-events-auto fixed left-1/2 top-16 z-50 w-[min(260px,calc(100vw-1.5rem))] -translate-x-1/2 rounded-xl border border-border/80 bg-comp-bg/95 p-3 shadow-[0_18px_50px_rgba(15,23,42,0.16)] backdrop-blur-md">
      <div className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        Decision branch
      </div>
      <div className="mt-1 text-sm font-medium text-foreground">Choose the branch semantic</div>
      <div className="mt-3 grid grid-cols-2 gap-2">
        {DECISION_BRANCH_EDGE_OPTIONS.map((semantic) => (
          <button
            key={semantic}
            className="h-8 rounded-md border text-xs font-semibold transition hover:brightness-95"
            onClick={() => onSelect(semantic)}
            style={{
              backgroundColor: `${EDGE_REGISTRY[semantic].color}14`,
              borderColor: `${EDGE_REGISTRY[semantic].color}40`,
              color: EDGE_REGISTRY[semantic].color,
            }}
            type="button"
          >
            {EDGE_REGISTRY[semantic].label}
          </button>
        ))}
      </div>
      <button
        className="mt-2 h-8 w-full rounded-md text-xs font-medium text-muted-foreground transition hover:bg-muted hover:text-foreground"
        onClick={onCancel}
        type="button"
      >
        Cancel
      </button>
    </div>
  );
}
