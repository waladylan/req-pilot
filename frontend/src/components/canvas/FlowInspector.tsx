import { PanelRightClose, Trash2 } from "lucide-react";
import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  DEFAULT_NODE_KIND,
  EDGE_INSPECTOR_ORDER,
  EDGE_REGISTRY,
  EDGE_RENDER_ORDER,
  EDGE_RENDER_REGISTRY,
  NODE_KIND_ORDER,
  NODE_KIND_REGISTRY,
  NODE_RENDER_ORDER,
  NODE_RENDER_REGISTRY,
} from "@/constants";
import { getEdgeLabel, getNodeTitle, resolveEdgeSemantic } from "@/helpers/flowchart";
import { cn } from "@/lib/utils";
import type {
  FlowEdgeDTO,
  FlowEdgeSemantic,
  FlowNodeDTO,
  FlowNodeKind,
  WfmNode,
  WfmNodeRole,
  WfmTransition,
  WfmTransitionSemantic,
} from "@/types/requirement";

type FlowInspectorProps = {
  onClose: () => void;
  onDeleteEdge: (edgeId: string) => void;
  onDeleteNode: (nodeId: string) => void;
  onUpdateEdgeLabel: (edgeId: string, label: string) => void;
  onUpdateEdgeSemantic: (edgeId: string, semantic: FlowEdgeSemantic) => void;
  onUpdateWfmEdgeCondition?: (edgeId: string, condition: string) => void;
  onUpdateWfmEdgeDescription?: (edgeId: string, description: string) => void;
  onUpdateWfmEdgeSemantic?: (edgeId: string, semantic: WfmTransitionSemantic) => void;
  onUpdateWfmNodeKind?: (nodeId: string, kind: string) => void;
  onUpdateWfmNodeMetadata?: (
    nodeId: string,
    metadata: Pick<WfmNode, "actorId" | "description">,
  ) => void;
  onUpdateWfmNodeRole?: (nodeId: string, role: WfmNodeRole) => void;
  onUpdateWfmNodeTitle?: (nodeId: string, title: string) => void;
  onUpdateNodeKind: (nodeId: string, nodeKind: FlowNodeKind) => void;
  onUpdateNodeLabel: (nodeId: string, label: string) => void;
  onUpdateNodeMetadata: (
    nodeId: string,
    metadata: Pick<FlowNodeDTO, "description" | "precondition" | "expectedResult">,
  ) => void;
  selectedEdge?: FlowEdgeDTO;
  selectedWfmNode?: WfmNode;
  selectedWfmTransition?: WfmTransition;
  selectedNode?: FlowNodeDTO;
};

export function FlowInspector({
  onClose,
  onDeleteEdge,
  onDeleteNode,
  onUpdateEdgeLabel,
  onUpdateEdgeSemantic,
  onUpdateWfmEdgeCondition,
  onUpdateWfmEdgeDescription,
  onUpdateWfmEdgeSemantic,
  onUpdateWfmNodeKind,
  onUpdateWfmNodeMetadata,
  onUpdateWfmNodeRole,
  onUpdateWfmNodeTitle,
  onUpdateNodeKind,
  onUpdateNodeLabel,
  onUpdateNodeMetadata,
  selectedEdge,
  selectedWfmNode,
  selectedWfmTransition,
  selectedNode,
}: FlowInspectorProps) {
  const isOpen = Boolean(selectedNode || selectedEdge);

  return (
    <aside
      aria-hidden={!isOpen}
      className={cn(
        "pointer-events-auto fixed bottom-4 right-3 top-16 z-40 w-[min(320px,calc(100vw-1.5rem))] overflow-auto rounded-xl border border-border/80 bg-comp-bg/95 p-3.5 shadow-[0_18px_50px_rgba(15,23,42,0.16)] backdrop-blur-md transition-[transform,opacity] duration-300 ease-out will-change-transform max-lg:top-auto max-lg:max-h-[45vh]",
        isOpen
          ? "translate-x-0 translate-y-0 opacity-100"
          : "pointer-events-none translate-x-[calc(100%+1rem)] opacity-0 max-lg:translate-x-0 max-lg:translate-y-[calc(100%+1rem)]",
      )}
    >
      {selectedNode ? (
        <NodeInspector
          node={selectedNode}
          onClose={onClose}
          onDeleteNode={onDeleteNode}
          onUpdateWfmNodeKind={onUpdateWfmNodeKind}
          onUpdateWfmNodeMetadata={onUpdateWfmNodeMetadata}
          onUpdateWfmNodeRole={onUpdateWfmNodeRole}
          onUpdateWfmNodeTitle={onUpdateWfmNodeTitle}
          onUpdateNodeKind={onUpdateNodeKind}
          onUpdateNodeLabel={onUpdateNodeLabel}
          onUpdateNodeMetadata={onUpdateNodeMetadata}
          wfmNode={selectedWfmNode}
        />
      ) : null}

      {selectedEdge ? (
        <EdgeInspector
          edge={selectedEdge}
          onClose={onClose}
          onDeleteEdge={onDeleteEdge}
          onUpdateEdgeLabel={onUpdateEdgeLabel}
          onUpdateEdgeSemantic={onUpdateEdgeSemantic}
          onUpdateWfmEdgeCondition={onUpdateWfmEdgeCondition}
          onUpdateWfmEdgeDescription={onUpdateWfmEdgeDescription}
          onUpdateWfmEdgeSemantic={onUpdateWfmEdgeSemantic}
          wfmTransition={selectedWfmTransition}
        />
      ) : null}

      {!selectedNode && !selectedEdge ? (
        <div className="text-xs text-muted-foreground">
          <div className="font-semibold text-foreground">Inspector</div>
          <div className="mt-0.5">Select a node or edge to edit properties.</div>
        </div>
      ) : null}
    </aside>
  );
}

type NodeInspectorProps = {
  node: FlowNodeDTO;
  onClose: () => void;
  onDeleteNode: (nodeId: string) => void;
  onUpdateWfmNodeKind?: (nodeId: string, kind: string) => void;
  onUpdateWfmNodeMetadata?: (
    nodeId: string,
    metadata: Pick<WfmNode, "actorId" | "description">,
  ) => void;
  onUpdateWfmNodeRole?: (nodeId: string, role: WfmNodeRole) => void;
  onUpdateWfmNodeTitle?: (nodeId: string, title: string) => void;
  onUpdateNodeKind: (nodeId: string, nodeKind: FlowNodeKind) => void;
  onUpdateNodeLabel: (nodeId: string, label: string) => void;
  onUpdateNodeMetadata: (
    nodeId: string,
    metadata: Pick<FlowNodeDTO, "description" | "precondition" | "expectedResult">,
  ) => void;
  wfmNode?: WfmNode;
};

function NodeInspector({
  node,
  onClose,
  onDeleteNode,
  onUpdateWfmNodeKind,
  onUpdateWfmNodeMetadata,
  onUpdateWfmNodeRole,
  onUpdateWfmNodeTitle,
  onUpdateNodeKind,
  onUpdateNodeLabel,
  onUpdateNodeMetadata,
  wfmNode,
}: NodeInspectorProps) {
  const title = getNodeTitle(node) || wfmNode?.title || "";

  return (
    <div className="space-y-3">
      <InspectorHeader onClose={onClose} subtitle={node.id} title="Node Inspector" />

      <Field label="Title">
        <input
          className="h-9 w-full rounded-md border border-border/80 bg-comp-bg px-3 text-sm outline-none transition focus:border-brand"
          onChange={(event) => {
            onUpdateNodeLabel(node.id, event.target.value);
            onUpdateWfmNodeTitle?.(node.id, event.target.value);
          }}
          value={title}
        />
      </Field>

      {wfmNode ? (
        <>
          <Field label="Role">
            <Select
              className="h-9 w-full rounded-md text-sm"
              onChange={(event) =>
                onUpdateWfmNodeRole?.(node.id, event.target.value as WfmNodeRole)
              }
              value={wfmNode.role}
            >
              {NODE_RENDER_ORDER.map((role) => (
                <option key={role} value={role}>
                  {NODE_RENDER_REGISTRY[role].label}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Kind">
            <input
              className="h-9 w-full rounded-md border border-border/80 bg-comp-bg px-3 text-sm outline-none transition focus:border-brand"
              onChange={(event) => onUpdateWfmNodeKind?.(node.id, event.target.value)}
              value={wfmNode.kind}
            />
          </Field>
        </>
      ) : (
        <Field label="Node kind">
          <Select
            className="h-9 w-full rounded-md text-sm"
            onChange={(event) => onUpdateNodeKind(node.id, event.target.value as FlowNodeKind)}
            value={node.nodeKind ?? DEFAULT_NODE_KIND}
          >
            {NODE_KIND_ORDER.map((nodeKind) => (
              <option key={nodeKind} value={nodeKind}>
                {NODE_KIND_REGISTRY[nodeKind].label}
              </option>
            ))}
          </Select>
        </Field>
      )}

      <Field label="Description">
        <Textarea
          className="min-h-20 rounded-md text-sm shadow-none"
          onChange={(event) => {
            onUpdateNodeMetadata(node.id, {
              description: event.target.value,
              expectedResult: node.expectedResult,
              precondition: node.precondition,
            });
            if (wfmNode && onUpdateWfmNodeMetadata) {
              onUpdateWfmNodeMetadata(node.id, {
                actorId: wfmNode.actorId,
                description: event.target.value,
              });
            }
          }}
          value={node.description ?? wfmNode?.description ?? ""}
        />
      </Field>

      {wfmNode ? (
        <Field label="Actor ID">
          <input
            className="h-9 w-full rounded-md border border-border/80 bg-comp-bg px-3 text-sm outline-none transition focus:border-brand"
            onChange={(event) =>
              onUpdateWfmNodeMetadata?.(node.id, {
                actorId: event.target.value,
                description: wfmNode.description,
              })
            }
            value={wfmNode.actorId ?? ""}
          />
        </Field>
      ) : (
        <>
          <Field label="Precondition">
            <Textarea
              className="min-h-16 rounded-md text-sm shadow-none"
              onChange={(event) =>
                onUpdateNodeMetadata(node.id, {
                  description: node.description,
                  expectedResult: node.expectedResult,
                  precondition: event.target.value,
                })
              }
              value={node.precondition ?? ""}
            />
          </Field>

          <Field label="Expected result">
            <Textarea
              className="min-h-16 rounded-md text-sm shadow-none"
              onChange={(event) =>
                onUpdateNodeMetadata(node.id, {
                  description: node.description,
                  expectedResult: event.target.value,
                  precondition: node.precondition,
                })
              }
              value={node.expectedResult ?? ""}
            />
          </Field>
        </>
      )}

      <Button
        className="h-9 w-full"
        icon={<Trash2 className="h-4 w-4" />}
        onClick={() => onDeleteNode(node.id)}
        type="button"
        variant="danger"
      >
        Delete node
      </Button>
    </div>
  );
}

type EdgeInspectorProps = {
  edge: FlowEdgeDTO;
  onClose: () => void;
  onDeleteEdge: (edgeId: string) => void;
  onUpdateEdgeLabel: (edgeId: string, label: string) => void;
  onUpdateEdgeSemantic: (edgeId: string, semantic: FlowEdgeSemantic) => void;
  onUpdateWfmEdgeCondition?: (edgeId: string, condition: string) => void;
  onUpdateWfmEdgeDescription?: (edgeId: string, description: string) => void;
  onUpdateWfmEdgeSemantic?: (edgeId: string, semantic: WfmTransitionSemantic) => void;
  wfmTransition?: WfmTransition;
};

function EdgeInspector({
  edge,
  onClose,
  onDeleteEdge,
  onUpdateEdgeLabel,
  onUpdateEdgeSemantic,
  onUpdateWfmEdgeCondition,
  onUpdateWfmEdgeDescription,
  onUpdateWfmEdgeSemantic,
  wfmTransition,
}: EdgeInspectorProps) {
  const semantic = wfmTransition?.semantic ?? resolveEdgeSemantic(edge);
  const label = getEdgeLabel(edge) || wfmTransition?.condition || "";

  return (
    <div className="space-y-3">
      <InspectorHeader
        onClose={onClose}
        subtitle={`${edge.source} -> ${edge.target}`}
        title="Edge Inspector"
      />

      <Field label="Label">
        <input
          className="h-9 w-full rounded-md border border-border/80 bg-comp-bg px-3 text-sm outline-none transition focus:border-brand"
          onChange={(event) => {
            onUpdateEdgeLabel(edge.id, event.target.value);
            onUpdateWfmEdgeCondition?.(edge.id, event.target.value);
          }}
          value={label}
        />
      </Field>

      <Field label="Semantic">
        <Select
          className="h-9 w-full rounded-md text-sm"
          onChange={(event) =>
            wfmTransition && onUpdateWfmEdgeSemantic
              ? onUpdateWfmEdgeSemantic(edge.id, event.target.value as WfmTransitionSemantic)
              : onUpdateEdgeSemantic(edge.id, event.target.value as FlowEdgeSemantic)
          }
          value={semantic}
        >
          {(wfmTransition ? EDGE_RENDER_ORDER : EDGE_INSPECTOR_ORDER).map((option) => (
            <option key={option} value={option}>
              {wfmTransition ? EDGE_RENDER_REGISTRY[option].label : EDGE_REGISTRY[option].label}
            </option>
          ))}
        </Select>
      </Field>

      {wfmTransition ? (
        <>
          <Field label="Description">
            <Textarea
              className="min-h-16 rounded-md text-sm shadow-none"
              onChange={(event) => onUpdateWfmEdgeDescription?.(edge.id, event.target.value)}
              value={wfmTransition.description ?? ""}
            />
          </Field>
        </>
      ) : null}

      <Button
        className="h-9 w-full"
        icon={<Trash2 className="h-4 w-4" />}
        onClick={() => onDeleteEdge(edge.id)}
        type="button"
        variant="danger"
      >
        Delete edge
      </Button>
    </div>
  );
}

function InspectorHeader({
  onClose,
  subtitle,
  title,
}: {
  onClose: () => void;
  subtitle: string;
  title: string;
}) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <h2 className="text-sm font-semibold leading-5">{title}</h2>
        <p className="truncate text-[11px] text-muted-foreground">{subtitle}</p>
      </div>
      <button
        className="rounded-full p-1.5 text-muted-foreground transition hover:bg-muted hover:text-foreground"
        onClick={onClose}
        title="Collapse inspector"
        type="button"
      >
        <PanelRightClose className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="block space-y-1.5 text-xs">
      <span className="font-semibold text-muted-foreground">{label}</span>
      {children}
    </label>
  );
}
