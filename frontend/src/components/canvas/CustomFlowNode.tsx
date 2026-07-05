import { Handle, Position, type NodeProps } from "@xyflow/react";
import { AlertTriangle } from "lucide-react";
import type { ReactNode } from "react";

import {
  EDGE_REGISTRY,
  FLOW_HANDLE_REGISTRY,
  FLOW_HANDLES,
  NODE_KIND_REGISTRY,
  NODE_TYPE_REGISTRY,
} from "@/constants";
import { cn } from "@/lib/utils";
import type { FlowEdgeSemantic, FlowHandleId, FlowNodeKind } from "@/types/requirement";

import type { FlowNodeCanvasSize, RequirementFlowNode } from "@/helpers/flowchart";

export function CustomFlowNode({ id, data, selected }: NodeProps<RequirementFlowNode>) {
  const nodeTypeConfig = NODE_TYPE_REGISTRY[data.nodeType];
  const nodeKindConfig = NODE_KIND_REGISTRY[data.nodeKind];
  const canReceiveInput = nodeTypeConfig.canReceiveInput;
  const canCreateOutput = nodeTypeConfig.canCreateOutput;
  const isDecisionNode = data.nodeKind === NODE_TYPE_REGISTRY.DECISION.nodeKind;
  const labelInput = (
    <NodeLabelEditor
      label={data.label}
      nodeKind={data.nodeKind}
      rows={data.labelRows}
      onChange={(label) => data.onLabelChange?.(id, label)}
    />
  );

  return (
    <div className="group relative">
      {canReceiveInput ? (
        <FlowHandle
          id={FLOW_HANDLES.INPUT}
          position={Position.Left}
          selected={selected}
          type="target"
        />
      ) : null}
      <NodeShapeFrame canvasSize={data.canvasSize} nodeKind={data.nodeKind} selected={selected}>
        {nodeKindConfig.showWarningIcon ? (
          <div className="mb-1 flex justify-center">
            <AlertTriangle className="h-4 w-4" aria-hidden="true" />
          </div>
        ) : null}
        {labelInput}
      </NodeShapeFrame>
      {canCreateOutput && isDecisionNode ? (
        <>
          <FlowHandle
            id={FLOW_HANDLES.YES}
            position={Position.Right}
            selected={selected}
            semantic={FLOW_HANDLE_REGISTRY[FLOW_HANDLES.YES].semantic}
            top="36%"
            type="source"
          />
          <FlowHandle
            id={FLOW_HANDLES.NO}
            position={Position.Right}
            selected={selected}
            semantic={FLOW_HANDLE_REGISTRY[FLOW_HANDLES.NO].semantic}
            top="64%"
            type="source"
          />
        </>
      ) : null}
      {canCreateOutput && !isDecisionNode ? (
        <FlowHandle
          id={FLOW_HANDLES.OUTPUT}
          position={Position.Right}
          selected={selected}
          type="source"
        />
      ) : null}
    </div>
  );
}

type NodeLabelEditorProps = {
  label: string;
  nodeKind: FlowNodeKind;
  onChange?: (label: string) => void;
  rows: number;
};

export function NodeLabelEditor({ label, nodeKind, onChange, rows }: NodeLabelEditorProps) {
  return (
    <textarea
      aria-label={`${nodeKind} label`}
      className="nodrag nowheel block w-full resize-none overflow-hidden border-0 bg-transparent p-0 text-center text-[13px] font-semibold leading-[18px] outline-none placeholder:text-muted-foreground/60"
      rows={rows}
      spellCheck={false}
      title={label}
      value={label}
      onChange={(event) => onChange?.(event.target.value)}
      onPointerDown={(event) => event.stopPropagation()}
    />
  );
}

type FlowHandleProps = {
  id: FlowHandleId;
  position: Position;
  selected?: boolean;
  semantic?: FlowEdgeSemantic;
  top?: string;
  type: "source" | "target";
};

function FlowHandle({ id, position, selected, semantic, top, type }: FlowHandleProps) {
  const color = semantic ? EDGE_REGISTRY[semantic].color : "#64748b";

  return (
    <Handle
      id={id}
      className={cn(
        "!h-3 !w-3 !border-2 !border-white !opacity-75 !shadow-sm !transition-opacity group-hover:!opacity-100",
        selected && "!opacity-100 ring-2 ring-white",
      )}
      position={position}
      style={{
        backgroundColor: color,
        top,
      }}
      title={FLOW_HANDLE_REGISTRY[id].label}
      type={type}
    />
  );
}

type NodeShapeFrameProps = {
  canvasSize: FlowNodeCanvasSize;
  children?: ReactNode;
  className?: string;
  nodeKind: FlowNodeKind;
  selected?: boolean;
};

export function NodeShapeFrame({
  canvasSize,
  children,
  className,
  nodeKind,
  selected,
}: NodeShapeFrameProps) {
  const nodeKindConfig = NODE_KIND_REGISTRY[nodeKind];
  const frameStyle = {
    minHeight: canvasSize.height,
    width: canvasSize.width,
  };

  if (nodeKindConfig.shape === "diamond") {
    return (
      <div className={cn("relative", className)} style={frameStyle}>
        <div
          className={cn(
            "absolute inset-5 rotate-45 border shadow-sm transition",
            nodeKindConfig.className,
            selected && "ring-2 ring-brand ring-offset-2",
          )}
        />
        <div className="absolute inset-0 flex items-center justify-center px-10">
          <div className="w-full">{children}</div>
        </div>
      </div>
    );
  }

  if (nodeKindConfig.shape === "parallelogram") {
    return (
      <div className={cn("relative", className)} style={frameStyle}>
        <div
          className={cn(
            "absolute inset-0 -skew-x-12 rounded-md border shadow-sm transition",
            nodeKindConfig.className,
            selected && "ring-2 ring-brand ring-offset-2",
          )}
        />
        <div className="relative px-5 py-3">{children}</div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "border px-4 py-3 shadow-sm transition",
        nodeKindConfig.shape === "pill" ? "rounded-full" : "rounded-md",
        nodeKindConfig.className,
        selected && "ring-2 ring-brand ring-offset-2",
        className,
      )}
      style={frameStyle}
    >
      {children}
    </div>
  );
}

export function MiniNodeShape({ nodeKind }: { nodeKind: FlowNodeKind }) {
  const nodeKindConfig = NODE_KIND_REGISTRY[nodeKind];

  if (nodeKindConfig.shape === "diamond") {
    return <span className={cn("h-4 w-4 rotate-45 border", nodeKindConfig.className)} />;
  }

  if (nodeKindConfig.shape === "parallelogram") {
    return (
      <span className={cn("h-4 w-6 -skew-x-12 rounded-sm border", nodeKindConfig.className)} />
    );
  }

  if (nodeKindConfig.shape === "error") {
    return (
      <span
        className={cn(
          "inline-flex h-4 w-6 items-center justify-center rounded-sm border",
          nodeKindConfig.className,
        )}
      >
        <AlertTriangle className="h-3 w-3" aria-hidden="true" />
      </span>
    );
  }

  return (
    <span
      className={cn(
        "h-4 w-6 border",
        nodeKindConfig.shape === "pill" ? "rounded-full" : "rounded-sm",
        nodeKindConfig.className,
      )}
    />
  );
}
