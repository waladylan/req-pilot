import { useCallback, useState } from "react";

export type LastSyncSource = "requirement" | "flow" | "wfm" | "canvas";
export type SyncStatus = "Requirement is source" | "Flow edited" | "WFM edited" | "Canvas edited";

type UseRequirementSyncParams = {
  resetGeneratedArtifacts: () => void;
  setRequirement: (requirement: string) => void;
};

type MarkRequirementEditedOptions = {
  resetArtifacts?: boolean;
};

export function useRequirementSync({
  resetGeneratedArtifacts,
  setRequirement,
}: UseRequirementSyncParams) {
  const [requirementDirty, setRequirementDirty] = useState(false);
  const [flowDirty, setFlowDirty] = useState(false);
  const [lastSyncSource, setLastSyncSource] = useState<LastSyncSource>("requirement");
  const [syncStatus, setSyncStatus] = useState<SyncStatus>("Requirement is source");

  const markRequirementEdited = useCallback(
    (value: string, options: MarkRequirementEditedOptions = {}) => {
      setRequirement(value);
      if (options.resetArtifacts ?? true) {
        resetGeneratedArtifacts();
        setFlowDirty(false);
      }
      setRequirementDirty(true);
      setLastSyncSource("requirement");
      setSyncStatus("Requirement is source");
    },
    [resetGeneratedArtifacts, setRequirement],
  );

  const markRequirementAsSource = useCallback(() => {
    setRequirementDirty(false);
    setFlowDirty(false);
    setLastSyncSource("requirement");
    setSyncStatus("Requirement is source");
  }, []);

  const markRequirementSaved = useCallback(() => {
    setRequirementDirty(false);
    setSyncStatus(flowDirty ? "Flow edited" : "Requirement is source");
    setLastSyncSource(flowDirty ? "flow" : "requirement");
  }, [flowDirty]);

  const markFlowEdited = useCallback(() => {
    setFlowDirty(true);
    setLastSyncSource("flow");
    setSyncStatus("Flow edited");
  }, []);

  const markWfmEdited = useCallback(() => {
    setFlowDirty(true);
    setLastSyncSource("wfm");
    setSyncStatus("WFM edited");
  }, []);

  const markCanvasEdited = useCallback(() => {
    setFlowDirty(true);
    setLastSyncSource("canvas");
    setSyncStatus("Canvas edited");
  }, []);

  return {
    flowDirty,
    lastSyncSource,
    markRequirementAsSource,
    markCanvasEdited,
    markFlowEdited,
    markRequirementEdited,
    markRequirementSaved,
    markWfmEdited,
    requirementDirty,
    syncStatus,
  };
}
