import {
  Download,
  GitBranch,
  LayoutDashboard,
  PanelLeft,
  Redo2,
  Route,
  Undo2,
  ZoomIn,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { EXPORT_FORMAT_REGISTRY, TOOLBAR_ACTION_REGISTRY } from "@/constants";

type FloatingToolbarProps = {
  canGenerateFlow: boolean;
  canRedo: boolean;
  canUndo: boolean;
  hasFlow: boolean;
  isGeneratingFlow: boolean;
  onAutoLayout: () => void;
  onExportMermaid: () => void;
  onFitView: () => void;
  onGenerateFlow: () => void;
  onRedo: () => void;
  onToggleLegend: () => void;
  onToggleSidebar: () => void;
  onUndo: () => void;
};

export function FloatingToolbar({
  canGenerateFlow,
  canRedo,
  canUndo,
  hasFlow,
  isGeneratingFlow,
  onAutoLayout,
  onExportMermaid,
  onFitView,
  onGenerateFlow,
  onRedo,
  onToggleLegend,
  onToggleSidebar,
  onUndo,
}: FloatingToolbarProps) {
  const buttonClassName = "h-7 px-2.5 text-[11px]";
  const iconClassName = "h-3.5 w-3.5";

  return (
    <div className="pointer-events-auto fixed left-1/2 top-3 z-40 flex max-w-[calc(100vw-1rem)] -translate-x-1/2 items-center gap-1.5 rounded-xl border border-border/80 bg-comp-bg/95 px-2 py-1.5 shadow-[0_16px_45px_rgba(15,23,42,0.14)] backdrop-blur-md">
      <div className="flex items-center gap-1">
        <Button
          className={buttonClassName}
          icon={<PanelLeft className={iconClassName} />}
          onClick={onToggleSidebar}
          title={TOOLBAR_ACTION_REGISTRY.toggleSidebar.title}
          type="button"
          variant="ghost"
        >
          {TOOLBAR_ACTION_REGISTRY.toggleSidebar.label}
        </Button>
      </div>
      <div className="h-5 w-px bg-border" />
      <div className="flex items-center gap-1">
        <Button
          className={buttonClassName}
          disabled={!canGenerateFlow || isGeneratingFlow}
          icon={isGeneratingFlow ? <Spinner /> : <GitBranch className={iconClassName} />}
          onClick={onGenerateFlow}
          title={TOOLBAR_ACTION_REGISTRY.generateFlow.title}
          type="button"
        >
          {TOOLBAR_ACTION_REGISTRY.generateFlow.label}
        </Button>
      </div>
      <div className="h-5 w-px bg-border" />
      <div className="flex items-center gap-1">
        <Button
          className={buttonClassName}
          disabled={!hasFlow}
          icon={<LayoutDashboard className={iconClassName} />}
          onClick={onAutoLayout}
          title={TOOLBAR_ACTION_REGISTRY.autoLayout.title}
          type="button"
          variant="ghost"
        >
          {TOOLBAR_ACTION_REGISTRY.autoLayout.label}
        </Button>
        <Button
          className={buttonClassName}
          disabled={!hasFlow}
          icon={<ZoomIn className={iconClassName} />}
          onClick={onFitView}
          title={TOOLBAR_ACTION_REGISTRY.fitView.title}
          type="button"
          variant="ghost"
        >
          {TOOLBAR_ACTION_REGISTRY.fitView.label}
        </Button>
      </div>
      <div className="h-5 w-px bg-border" />
      <div className="hidden items-center gap-1 md:flex">
        <Button
          className={buttonClassName}
          disabled={!hasFlow}
          icon={<Download className={iconClassName} />}
          onClick={onExportMermaid}
          title={EXPORT_FORMAT_REGISTRY.mermaid.title}
          type="button"
          variant="ghost"
        >
          {EXPORT_FORMAT_REGISTRY.mermaid.shortLabel}
        </Button>
      </div>
      <div className="hidden h-5 w-px bg-border md:block" />
      <div className="flex items-center gap-1">
        <Button
          className="h-7 w-7 px-0"
          disabled={!canUndo}
          icon={<Undo2 className={iconClassName} />}
          onClick={onUndo}
          title={`${TOOLBAR_ACTION_REGISTRY.undo.title} (${TOOLBAR_ACTION_REGISTRY.undo.shortcut})`}
          type="button"
          variant="ghost"
        />
        <Button
          className="h-7 w-7 px-0"
          disabled={!canRedo}
          icon={<Redo2 className={iconClassName} />}
          onClick={onRedo}
          title={`${TOOLBAR_ACTION_REGISTRY.redo.title} (${TOOLBAR_ACTION_REGISTRY.redo.shortcut})`}
          type="button"
          variant="ghost"
        />
        <Button
          className="h-7 w-7 px-0"
          icon={<Route className={iconClassName} />}
          onClick={onToggleLegend}
          title={TOOLBAR_ACTION_REGISTRY.toggleLegend.title}
          type="button"
          variant="ghost"
        />
      </div>
    </div>
  );
}
