import {
  ChevronRight,
  FileText,
  FolderPlus,
  ListChecks,
  PanelLeftClose,
  Plus,
  Save,
  Settings,
  Shapes,
} from "lucide-react";
import type { ReactNode } from "react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import {
  CANVAS_TOOL_REGISTRY,
  SAMPLE_REQUIREMENTS,
  SIDEBAR_TAB_KEYS,
  SIDEBAR_TAB_ORDER,
  SIDEBAR_TAB_REGISTRY,
} from "@/constants";
import {
  getRequirementValidationMessage,
  REQUIREMENT_TEXTAREA_PLACEHOLDER,
} from "@/helpers/requirement-editor";
import type { LastSyncSource } from "@/hooks/useRequirementSync";
import { cn } from "@/lib/utils";
import type { ProjectDTO, RequirementResourceDTO } from "@/types/project.types";
import type { WfmNodeRole } from "@/types/requirement";

import { NodePalette } from "./NodePalette";

type SidebarTab = (typeof SIDEBAR_TAB_ORDER)[number];

type FloatingSidebarProps = {
  canGenerateFlow: boolean;
  canGenerateTestCases: boolean;
  flowDirty: boolean;
  isGeneratingFlow: boolean;
  isGeneratingTestCases: boolean;
  isLoadingWorkspace: boolean;
  isOpen: boolean;
  isSavingRequirement: boolean;
  lastSyncSource: LastSyncSource;
  onAddNode: (nodeRole: WfmNodeRole) => void;
  onCreateProject: () => void;
  onCreateRequirement: () => void;
  onGenerateFlow: () => void;
  onGenerateTestCases: () => void;
  onProjectChange: (projectId: string) => void;
  onRequirementChange: (value: string) => void;
  onRequirementSelect: (requirementId: string) => void;
  onSampleChange: (sampleIndex: string) => void;
  onSaveRequirement: () => void;
  projects: ProjectDTO[];
  requirements: RequirementResourceDTO[];
  selectedProjectId: string | null;
  selectedRequirementId: string | null;
  selectedSampleIndex: string;
  onToggle: () => void;
  requirement: string;
  requirementDirty: boolean;
  setShowBackground: (value: boolean) => void;
  setShowLegend: (value: boolean) => void;
  setShowMiniMap: (value: boolean) => void;
  setSnapToGrid: (value: boolean) => void;
  showBackground: boolean;
  showLegend: boolean;
  showMiniMap: boolean;
  snapToGrid: boolean;
  syncStatus: string;
  testCaseSet?: RequirementResourceDTO["testCaseSet"];
};

const sidebarTabIcons = {
  fileText: <FileText className="h-4 w-4" />,
  listChecks: <ListChecks className="h-4 w-4" />,
  settings: <Settings className="h-4 w-4" />,
  shapes: <Shapes className="h-4 w-4" />,
} as const satisfies Record<(typeof SIDEBAR_TAB_REGISTRY)[SidebarTab]["iconName"], ReactNode>;

const VISIBLE_SIDEBAR_TAB_ORDER = SIDEBAR_TAB_ORDER;

export function FloatingSidebar({
  canGenerateFlow,
  canGenerateTestCases,
  flowDirty,
  isGeneratingFlow,
  isGeneratingTestCases,
  isLoadingWorkspace,
  isSavingRequirement,
  isOpen,
  lastSyncSource,
  onAddNode,
  onCreateProject,
  onCreateRequirement,
  onGenerateFlow,
  onGenerateTestCases,
  onProjectChange,
  onRequirementChange,
  onRequirementSelect,
  onSampleChange,
  onSaveRequirement,
  projects,
  requirements,
  selectedProjectId,
  selectedRequirementId,
  selectedSampleIndex,
  onToggle,
  requirement,
  requirementDirty,
  setShowBackground,
  setShowLegend,
  setShowMiniMap,
  setSnapToGrid,
  showBackground,
  showLegend,
  showMiniMap,
  snapToGrid,
  syncStatus,
  testCaseSet,
}: FloatingSidebarProps) {
  const [activeTab, setActiveTab] = useState<SidebarTab>(SIDEBAR_TAB_KEYS.requirement);

  return (
    <>
      <button
        aria-label="Open workspace sidebar"
        className={cn(
          "pointer-events-auto fixed left-3 top-24 z-40 flex h-20 w-9 items-center justify-center rounded-full border border-border/80 bg-comp-bg/95 text-muted-foreground shadow-[0_12px_32px_rgba(15,23,42,0.14)] backdrop-blur-md transition-all duration-300 ease-out hover:text-foreground",
          isOpen ? "pointer-events-none -translate-x-5 opacity-0" : "translate-x-0 opacity-100",
        )}
        onClick={onToggle}
        title="Open sidebar"
        type="button"
      >
        <ChevronRight className="h-4 w-4" aria-hidden="true" />
      </button>

      <aside
        className={cn(
          "pointer-events-auto fixed left-3 top-16 z-40 flex h-[calc(100vh-5rem)] w-[min(336px,calc(100vw-1.5rem))] flex-col overflow-hidden rounded-xl border border-border/80 bg-comp-bg/95 shadow-[0_18px_50px_rgba(15,23,42,0.16)] backdrop-blur-md transition-[transform,opacity] duration-300 ease-out will-change-transform",
          isOpen
            ? "translate-x-0 opacity-100"
            : "pointer-events-none -translate-x-[calc(100%+1rem)] opacity-0",
        )}
      >
        <div className="flex items-center justify-between border-b border-border/70 px-3.5 py-2.5">
          <div className="min-w-0">
            <h2 className="text-sm font-semibold leading-5">Workspace</h2>
            <p className="truncate text-[11px] text-muted-foreground">{syncStatus}</p>
          </div>
          <button
            className="rounded-full p-1.5 text-muted-foreground transition hover:bg-muted hover:text-foreground"
            onClick={onToggle}
            title="Collapse sidebar"
            type="button"
          >
            <PanelLeftClose className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <div
          className="grid border-b border-border/70 bg-muted/30 px-1 py-1"
          style={{
            gridTemplateColumns: `repeat(${VISIBLE_SIDEBAR_TAB_ORDER.length}, minmax(0, 1fr))`,
          }}
        >
          {VISIBLE_SIDEBAR_TAB_ORDER.map((tabId) => {
            const tab = SIDEBAR_TAB_REGISTRY[tabId];
            return (
              <button
                key={tabId}
                className={cn(
                  "flex min-w-0 flex-col items-center gap-0.5 rounded-md px-1.5 py-2 text-[10px] font-medium text-muted-foreground transition hover:bg-muted hover:text-foreground",
                  activeTab === tabId && "bg-comp-bg text-foreground shadow-sm",
                )}
                onClick={() => setActiveTab(tabId)}
                title={tab.label}
                type="button"
              >
                {sidebarTabIcons[tab.iconName]}
                <span className="max-w-full truncate">{tab.label}</span>
              </button>
            );
          })}
        </div>

        <div className="flex-1 overflow-auto p-3">
          {activeTab === SIDEBAR_TAB_KEYS.requirement ? (
            <RequirementTab
              flowDirty={flowDirty}
              canGenerateFlow={canGenerateFlow}
              isGeneratingFlow={isGeneratingFlow}
              isLoadingWorkspace={isLoadingWorkspace}
              isSavingRequirement={isSavingRequirement}
              lastSyncSource={lastSyncSource}
              onCreateProject={onCreateProject}
              onCreateRequirement={onCreateRequirement}
              onGenerateFlow={onGenerateFlow}
              onProjectChange={onProjectChange}
              onRequirementChange={onRequirementChange}
              onRequirementSelect={onRequirementSelect}
              onSampleChange={onSampleChange}
              onSaveRequirement={onSaveRequirement}
              projects={projects}
              requirement={requirement}
              requirementDirty={requirementDirty}
              requirements={requirements}
              selectedProjectId={selectedProjectId}
              selectedRequirementId={selectedRequirementId}
              selectedSampleIndex={selectedSampleIndex}
              syncStatus={syncStatus}
            />
          ) : null}

          {activeTab === SIDEBAR_TAB_KEYS.nodes ? <NodePalette onAddNode={onAddNode} /> : null}

          {activeTab === SIDEBAR_TAB_KEYS.testCases ? (
            <TestCasesTab
              canGenerateTestCases={canGenerateTestCases}
              isGeneratingTestCases={isGeneratingTestCases}
              onGenerateTestCases={onGenerateTestCases}
              selectedRequirementId={selectedRequirementId}
              testCaseSet={testCaseSet}
            />
          ) : null}

          {activeTab === SIDEBAR_TAB_KEYS.settings ? (
            <SettingsTab
              setShowBackground={setShowBackground}
              setShowLegend={setShowLegend}
              setShowMiniMap={setShowMiniMap}
              setSnapToGrid={setSnapToGrid}
              showBackground={showBackground}
              showLegend={showLegend}
              showMiniMap={showMiniMap}
              snapToGrid={snapToGrid}
            />
          ) : null}
        </div>
      </aside>
    </>
  );
}

type RequirementTabProps = Pick<
  FloatingSidebarProps,
  | "flowDirty"
  | "canGenerateFlow"
  | "isGeneratingFlow"
  | "isLoadingWorkspace"
  | "isSavingRequirement"
  | "lastSyncSource"
  | "onCreateProject"
  | "onCreateRequirement"
  | "onGenerateFlow"
  | "onProjectChange"
  | "onRequirementChange"
  | "onRequirementSelect"
  | "onSampleChange"
  | "onSaveRequirement"
  | "projects"
  | "requirement"
  | "requirementDirty"
  | "requirements"
  | "selectedProjectId"
  | "selectedRequirementId"
  | "selectedSampleIndex"
  | "syncStatus"
>;

function RequirementTab({
  canGenerateFlow,
  flowDirty,
  isGeneratingFlow,
  isLoadingWorkspace,
  isSavingRequirement,
  lastSyncSource,
  onCreateProject,
  onCreateRequirement,
  onGenerateFlow,
  onProjectChange,
  onRequirementChange,
  onRequirementSelect,
  onSampleChange,
  onSaveRequirement,
  projects,
  requirement,
  requirementDirty,
  requirements,
  selectedProjectId,
  selectedRequirementId,
  selectedSampleIndex,
  syncStatus,
}: RequirementTabProps) {
  const validationMessage = getRequirementValidationMessage(requirement);

  return (
    <div className="space-y-3">
      <section className="space-y-2 rounded-lg border border-border/80 bg-muted/25 p-2.5">
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs font-semibold text-muted-foreground">Project</span>
          <Button
            className="h-7 rounded-md px-2 text-[11px]"
            icon={<FolderPlus className="h-3.5 w-3.5" aria-hidden="true" />}
            onClick={onCreateProject}
            type="button"
            variant="secondary"
          >
            New
          </Button>
        </div>
        <Select
          aria-label="Project"
          className="h-8 w-full rounded-md px-2.5 text-xs"
          disabled={isLoadingWorkspace || projects.length === 0}
          onChange={(event) => onProjectChange(event.target.value)}
          value={selectedProjectId ?? ""}
        >
          {projects.length === 0 ? <option value="">No projects</option> : null}
          {projects.map((project) => (
            <option key={project.id} value={project.id}>
              {project.name}
            </option>
          ))}
        </Select>
      </section>

      <section className="space-y-2 rounded-lg border border-border/80 bg-muted/25 p-2.5">
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs font-semibold text-muted-foreground">Requirements</span>
          <Button
            className="h-7 rounded-md px-2 text-[11px]"
            disabled={!selectedProjectId || isLoadingWorkspace}
            icon={<Plus className="h-3.5 w-3.5" aria-hidden="true" />}
            onClick={onCreateRequirement}
            type="button"
            variant="secondary"
          >
            New
          </Button>
        </div>
        <div className="max-h-36 space-y-1 overflow-auto pr-0.5">
          {requirements.length === 0 ? (
            <div className="rounded-md border border-dashed border-border/90 px-3 py-2 text-[11px] text-muted-foreground">
              No requirements
            </div>
          ) : (
            requirements.map((item) => (
              <button
                key={item.id}
                className={cn(
                  "flex w-full items-center justify-between gap-2 rounded-md border px-2.5 py-2 text-left text-xs transition hover:bg-muted/50",
                  selectedRequirementId === item.id
                    ? "border-brand/50 bg-brand/10 text-foreground"
                    : "border-border/70 bg-comp-bg/70 text-muted-foreground",
                )}
                onClick={() => onRequirementSelect(item.id)}
                type="button"
              >
                <span className="min-w-0 flex-1 truncate font-medium">{item.title}</span>
                <span className="shrink-0 rounded-full bg-muted px-1.5 py-0.5 text-[10px] uppercase">
                  {item.status.toLowerCase()}
                </span>
              </button>
            ))
          )}
        </div>
      </section>

      <label className="block space-y-1.5 text-xs">
        <span className="font-semibold text-muted-foreground">Use sample</span>
        <Select
          className="h-9 w-full rounded-md text-sm"
          onChange={(event) => onSampleChange(event.target.value)}
          value={selectedSampleIndex}
        >
          <option value="" disabled>
            Sample Requirements
          </option>
          {SAMPLE_REQUIREMENTS.map((sample, index) => (
            <option key={sample.label} value={index}>
              {sample.label}
            </option>
          ))}
        </Select>
      </label>

      <label className="block space-y-1.5 text-xs">
        <div className="flex items-center justify-between gap-2">
          <span className="font-semibold text-muted-foreground">Custom requirement</span>
          <button
            className="rounded-md px-2 py-1 text-[11px] font-medium text-muted-foreground transition hover:bg-muted hover:text-foreground disabled:pointer-events-none disabled:opacity-40"
            disabled={!requirement || isGeneratingFlow || isSavingRequirement}
            onClick={() => onRequirementChange("")}
            type="button"
          >
            Clear
          </button>
        </div>
        <Textarea
          aria-label="Custom requirement"
          className="min-h-64 rounded-md text-sm shadow-none"
          onChange={(event) => onRequirementChange(event.target.value)}
          placeholder={REQUIREMENT_TEXTAREA_PLACEHOLDER}
          disabled={!selectedRequirementId || isSavingRequirement || isGeneratingFlow}
          value={requirement}
        />
      </label>
      {validationMessage ? (
        <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] font-medium text-amber-800">
          {validationMessage}
        </div>
      ) : null}
      <div className="rounded-md border border-border/80 bg-muted/35 px-3 py-2 text-[11px] leading-5 text-muted-foreground">
        <div className="font-semibold text-foreground">{syncStatus}</div>
        <div>Last source: {lastSyncSource}</div>
        <div>Requirement dirty: {requirementDirty ? "yes" : "no"}</div>
        <div>Flow dirty: {flowDirty ? "yes" : "no"}</div>
      </div>
      <Button
        className="h-9 w-full"
        disabled={!selectedRequirementId || isSavingRequirement || isGeneratingFlow}
        icon={isSavingRequirement ? <Spinner /> : <Save className="h-4 w-4" aria-hidden="true" />}
        onClick={onSaveRequirement}
        type="button"
        variant="secondary"
      >
        Save Requirement
      </Button>
      <Button
        className="h-9 w-full"
        disabled={!canGenerateFlow || isGeneratingFlow || isSavingRequirement}
        icon={isGeneratingFlow ? <Spinner /> : undefined}
        onClick={onGenerateFlow}
        type="button"
      >
        Generate Flow
      </Button>
    </div>
  );
}

type SettingsTabProps = Pick<
  FloatingSidebarProps,
  | "setShowBackground"
  | "setShowLegend"
  | "setShowMiniMap"
  | "setSnapToGrid"
  | "showBackground"
  | "showLegend"
  | "showMiniMap"
  | "snapToGrid"
>;

function SettingsTab({
  setShowBackground,
  setShowLegend,
  setShowMiniMap,
  setSnapToGrid,
  showBackground,
  showLegend,
  showMiniMap,
  snapToGrid,
}: SettingsTabProps) {
  return (
    <div className="space-y-2">
      <ToggleRow
        checked={showMiniMap}
        label={CANVAS_TOOL_REGISTRY.miniMap.label}
        onChange={setShowMiniMap}
      />
      <ToggleRow
        checked={showBackground}
        label={CANVAS_TOOL_REGISTRY.background.label}
        onChange={setShowBackground}
      />
      <ToggleRow
        checked={showLegend}
        label={CANVAS_TOOL_REGISTRY.legend.label}
        onChange={setShowLegend}
      />
      <ToggleRow
        checked={snapToGrid}
        label={CANVAS_TOOL_REGISTRY.snapToGrid.label}
        onChange={setSnapToGrid}
      />
    </div>
  );
}

type TestCasesTabProps = Pick<
  FloatingSidebarProps,
  "canGenerateTestCases" | "isGeneratingTestCases" | "onGenerateTestCases" | "selectedRequirementId" | "testCaseSet"
>;

function TestCasesTab({
  canGenerateTestCases,
  isGeneratingTestCases,
  onGenerateTestCases,
  selectedRequirementId,
  testCaseSet,
}: TestCasesTabProps) {
  const testCases = testCaseSet?.testCases ?? [];

  return (
    <div className="space-y-3">
      <div className="rounded-lg border border-border/80 bg-muted/25 p-2.5">
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="text-xs font-semibold text-foreground">Generated test cases</div>
            <div className="mt-0.5 text-[11px] text-muted-foreground">
              {testCaseSet ? `${testCases.length} cases from WFM v2` : "No saved test cases"}
            </div>
          </div>
          <Button
            className="h-8 shrink-0 rounded-md px-2.5 text-[11px]"
            disabled={!canGenerateTestCases || isGeneratingTestCases}
            icon={isGeneratingTestCases ? <Spinner /> : <ListChecks className="h-3.5 w-3.5" />}
            onClick={onGenerateTestCases}
            type="button"
            variant="secondary"
          >
            Generate
          </Button>
        </div>
        {!selectedRequirementId ? (
          <div className="mt-2 rounded-md border border-dashed border-border/80 px-3 py-2 text-[11px] text-muted-foreground">
            Select a requirement first.
          </div>
        ) : !canGenerateTestCases ? (
          <div className="mt-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] font-medium text-amber-800">
            Generate flow first before generating test cases.
          </div>
        ) : null}
      </div>

      {testCases.length === 0 ? (
        <div className="rounded-lg border border-dashed border-border/90 bg-comp-bg/70 px-3 py-8 text-center text-xs text-muted-foreground">
          Test cases generated from WFM v2 will appear here.
        </div>
      ) : (
        <div className="space-y-2">
          {testCases.map((testCase) => (
            <details
              key={testCase.id}
              className="group rounded-lg border border-border/80 bg-comp-bg/80 p-2.5 text-xs shadow-sm"
            >
              <summary className="flex cursor-pointer list-none items-start justify-between gap-2">
                <span className="min-w-0">
                  <span className="block font-semibold text-foreground">
                    {testCase.id} · {testCase.title}
                  </span>
                  <span className="mt-1 block truncate text-[11px] text-muted-foreground">
                    {testCase.type} · {testCase.priority} · {testCase.tags.join(", ")}
                  </span>
                </span>
                <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold uppercase text-muted-foreground">
                  {testCase.priority}
                </span>
              </summary>

              <div className="mt-3 space-y-3 border-t border-border/70 pt-3">
                <InfoList label="Preconditions" values={testCase.preconditions} />
                <div>
                  <div className="mb-1 font-semibold text-muted-foreground">Steps</div>
                  <ol className="space-y-1.5">
                    {testCase.steps.map((step) => (
                      <li key={step.stepNo} className="grid grid-cols-[1.5rem_1fr] gap-1">
                        <span className="text-muted-foreground">{step.stepNo}.</span>
                        <span>
                          {step.action}
                          <span className="block text-[11px] text-muted-foreground">
                            {step.expectedResult}
                          </span>
                        </span>
                      </li>
                    ))}
                  </ol>
                </div>
                <div>
                  <div className="mb-1 font-semibold text-muted-foreground">Expected result</div>
                  <p className="text-muted-foreground">{testCase.expectedResult}</p>
                </div>
                <InfoList label="Source nodes" values={testCase.sourcePath.nodeIds} />
                <InfoList label="Source transitions" values={testCase.sourcePath.transitionIds} />
              </div>
            </details>
          ))}
        </div>
      )}
    </div>
  );
}

function InfoList({ label, values }: { label: string; values: string[] }) {
  return (
    <div>
      <div className="mb-1 font-semibold text-muted-foreground">{label}</div>
      <div className="flex flex-wrap gap-1">
        {values.length === 0 ? (
          <span className="text-[11px] text-muted-foreground">None</span>
        ) : (
          values.map((value) => (
            <span key={value} className="rounded-full bg-muted px-2 py-0.5 text-[10px]">
              {value}
            </span>
          ))
        )}
      </div>
    </div>
  );
}

function ToggleRow({
  checked,
  label,
  onChange,
}: {
  checked: boolean;
  label: string;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="flex cursor-pointer items-center justify-between rounded-md border border-border/80 bg-comp-bg/70 px-3 py-2 text-sm transition hover:bg-muted/40">
      <span>{label}</span>
      <input
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        type="checkbox"
      />
    </label>
  );
}
