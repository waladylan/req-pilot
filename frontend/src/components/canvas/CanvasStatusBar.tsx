import { AlertCircle, TriangleAlert } from "lucide-react";

type CanvasStatusBarProps = {
  errorMessage?: string;
  flowDirty: boolean;
  hasFlow: boolean;
  requirementDirty: boolean;
  syncStatus: string;
  warningMessage?: string;
};

export function CanvasStatusBar({
  errorMessage,
  flowDirty,
  hasFlow,
  requirementDirty,
  syncStatus,
  warningMessage,
}: CanvasStatusBarProps) {
  return (
    <div className="pointer-events-none fixed bottom-3 left-1/2 z-30 flex max-w-[calc(100vw-1rem)] -translate-x-1/2 flex-wrap items-center justify-center gap-2 rounded-full border border-border/80 bg-comp-bg/95 px-3 py-1.5 text-[11px] shadow-[0_12px_34px_rgba(15,23,42,0.12)] backdrop-blur-md">
      {errorMessage ? (
        <span className="flex items-center gap-1 font-medium text-destructive">
          <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
          {errorMessage}
        </span>
      ) : warningMessage ? (
        <span className="flex items-center gap-1 font-medium text-amber-700">
          <TriangleAlert className="h-3.5 w-3.5" aria-hidden="true" />
          {warningMessage}
        </span>
      ) : (
        <span className="font-medium text-foreground">
          {hasFlow ? syncStatus : "No flow generated yet"}
        </span>
      )}
      <span className="h-3.5 w-px bg-border" aria-hidden="true" />
      <span className="text-muted-foreground">Req {requirementDirty ? "dirty" : "clean"}</span>
      <span className="text-muted-foreground">Flow {flowDirty ? "dirty" : "clean"}</span>
      <span className="hidden h-3.5 w-px bg-border md:block" aria-hidden="true" />
    </div>
  );
}
