import {
  EDGE_RENDER_ORDER,
  EDGE_RENDER_REGISTRY,
  NODE_RENDER_ORDER,
  NODE_RENDER_REGISTRY,
} from "@/constants";
import { MiniNodeShape } from "./CustomFlowNode";

type FlowLegendProps = {
  onToggle: () => void;
  visible: boolean;
};

export function FlowLegend({ onToggle, visible }: FlowLegendProps) {
  if (!visible) {
    return (
      <button
        className="pointer-events-auto fixed bottom-3 left-3 z-30 rounded-full border border-border/80 bg-comp-bg/95 px-3 py-1.5 text-[11px] font-semibold shadow-[0_12px_34px_rgba(15,23,42,0.12)] backdrop-blur-md transition hover:bg-muted"
        onClick={onToggle}
        title="Show legend"
        type="button"
      >
        Legend
      </button>
    );
  }

  return (
    <div className="pointer-events-auto fixed bottom-3 left-3 z-30 w-64 rounded-xl border border-border/80 bg-comp-bg/95 p-3 shadow-[0_16px_45px_rgba(15,23,42,0.14)] backdrop-blur-md">
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Legend
        </h2>
        <button
          className="rounded-full px-2 py-1 text-[11px] font-medium text-muted-foreground transition hover:bg-muted hover:text-foreground"
          onClick={onToggle}
          title="Hide legend"
          type="button"
        >
          Hide
        </button>
      </div>
      <div className="space-y-1.5">
        <div className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
          Nodes
        </div>
        {NODE_RENDER_ORDER.map((nodeRole) => (
          <div key={nodeRole} className="flex items-center gap-2.5 rounded-md px-1.5 py-1 text-xs">
            <MiniNodeShape nodeKind={NODE_RENDER_REGISTRY[nodeRole].flowNodeKind} />
            <div className="min-w-0">
              <div className="font-semibold text-foreground">
                {NODE_RENDER_REGISTRY[nodeRole].label}
              </div>
              <div className="truncate text-[11px] text-muted-foreground">
                {NODE_RENDER_REGISTRY[nodeRole].description}
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="mt-3 space-y-1.5 border-t border-border/70 pt-3">
        <div className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
          Edges
        </div>
        {EDGE_RENDER_ORDER.map((semantic) => (
          <div key={semantic} className="flex items-center gap-2.5 rounded-md px-1.5 py-1 text-xs">
            <span
              className="h-0.5 w-9 rounded-full"
              style={{ backgroundColor: EDGE_RENDER_REGISTRY[semantic].color }}
            />
            <span className="font-medium text-foreground">
              {EDGE_RENDER_REGISTRY[semantic].label}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
