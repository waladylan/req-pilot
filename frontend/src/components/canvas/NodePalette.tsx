import { NODE_RENDER_ORDER, NODE_RENDER_REGISTRY } from "@/constants";
import type { WfmNodeRole } from "@/types/requirement";
import { MiniNodeShape } from "./CustomFlowNode";

type NodePaletteProps = {
  onAddNode: (nodeRole: WfmNodeRole) => void;
};

export function NodePalette({ onAddNode }: NodePaletteProps) {
  return (
    <div className="space-y-1.5">
      {NODE_RENDER_ORDER.map((nodeRole) => (
        <button
          key={nodeRole}
          className="flex h-10 w-full items-center gap-2.5 rounded-md border border-border/80 bg-comp-bg/80 px-2.5 text-left text-sm shadow-sm transition hover:bg-muted hover:shadow"
          draggable
          onClick={() => onAddNode(nodeRole)}
          onDragStart={(event) => {
            event.dataTransfer.setData("application/req-pilot-node-role", nodeRole);
            event.dataTransfer.effectAllowed = "copy";
          }}
          type="button"
        >
          <MiniNodeShape nodeKind={NODE_RENDER_REGISTRY[nodeRole].flowNodeKind} />
          <span className="truncate font-medium">{NODE_RENDER_REGISTRY[nodeRole].label}</span>
        </button>
      ))}
    </div>
  );
}
