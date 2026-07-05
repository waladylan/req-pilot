import {
  Background,
  ConnectionMode,
  Controls,
  Handle,
  MiniMap,
  Position,
  ReactFlow,
  type Connection,
  type EdgeChange,
  type NodeChange,
  type NodeProps,
  type NodeTypes,
} from "@xyflow/react";
import { AlertTriangle, Plus, Trash2, Unlink } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { NodeLabelEditor } from "@/components/canvas/CustomFlowNode";
import { Button } from "@/components/ui/button";
import { Panel } from "@/components/ui/panel";
import { NODE_KIND_ORDER, NODE_KIND_REGISTRY, NODE_TYPE_REGISTRY } from "@/constants";
import {
  addFlowNode,
  applyFlowEdgeChanges,
  applyFlowNodeChanges,
  connectFlowNodes,
  createReactFlowEdges,
  createReactFlowNodes,
  deleteFlowEdge,
  deleteFlowNode,
  shouldPersistEdgeChanges,
  shouldPersistNodeChanges,
  shouldResetTestsForEdgeChanges,
  shouldResetTestsForNodeChanges,
  updateFlowNodeLabel,
  type RequirementFlowEdge,
  type RequirementFlowNode,
} from "@/helpers/flowchart";
import { cn } from "@/lib/utils";
import type { FlowNodeKind, FlowchartDTO } from "@/types/requirement";

type FlowchartPreviewProps = {
  flowchart?: FlowchartDTO;
  onFlowchartChange: (
    flowchart: FlowchartDTO,
    options?: {
      markFlowEdited?: boolean;
      resetTestCases?: boolean;
    },
  ) => void;
};

function EditableFlowNode({ id, data, selected }: NodeProps<RequirementFlowNode>) {
  const nodeKindConfig = NODE_KIND_REGISTRY[data.nodeKind];
  const nodeTypeConfig = NODE_TYPE_REGISTRY[data.nodeType];
  const canReceiveInput = nodeTypeConfig.canReceiveInput;
  const canCreateOutput = nodeTypeConfig.canCreateOutput;
  const labelInput = (
    <NodeLabelEditor
      label={data.label}
      nodeKind={data.nodeKind}
      rows={data.labelRows}
      onChange={(label) => data.onLabelChange?.(id, label)}
    />
  );
  const frameStyle = {
    minHeight: data.canvasSize.height,
    width: data.canvasSize.width,
  };
  const typeText = (
    <div className="mt-1 text-center text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
      {nodeKindConfig.label}
    </div>
  );

  const body = (() => {
    if (nodeKindConfig.shape === "diamond") {
      return (
        <div className="relative" style={frameStyle}>
          <div
            className={cn(
              "absolute inset-5 rotate-45 border shadow-sm transition",
              nodeKindConfig.className,
              selected && "ring-2 ring-brand ring-offset-2",
            )}
          />
          <div className="absolute inset-0 flex items-center justify-center px-10">
            <div className="w-full">
              {labelInput}
              {typeText}
            </div>
          </div>
        </div>
      );
    }

    if (nodeKindConfig.shape === "parallelogram") {
      return (
        <div className="relative" style={frameStyle}>
          <div
            className={cn(
              "absolute inset-0 -skew-x-12 rounded-md border shadow-sm transition",
              nodeKindConfig.className,
              selected && "ring-2 ring-brand ring-offset-2",
            )}
          />
          <div className="relative px-5 py-3">
            {labelInput}
            {typeText}
          </div>
        </div>
      );
    }

    const roundedClass = nodeKindConfig.shape === "pill" ? "rounded-full" : "rounded-md";
    return (
      <div
        className={cn(
          "border px-4 py-3 shadow-sm transition",
          roundedClass,
          nodeKindConfig.className,
          selected && "ring-2 ring-brand ring-offset-2",
        )}
        style={frameStyle}
      >
        {nodeKindConfig.showWarningIcon ? (
          <div className="mb-1 flex justify-center">
            <AlertTriangle className="h-4 w-4" aria-hidden="true" />
          </div>
        ) : null}
        {labelInput}
        {typeText}
      </div>
    );
  })();

  return (
    <div className="relative">
      {canReceiveInput ? (
        <Handle
          className="!h-3 !w-3 !border-2 !border-white !bg-slate-500"
          position={Position.Left}
          type="target"
        />
      ) : null}
      {body}
      {canCreateOutput ? (
        <Handle
          className="!h-3 !w-3 !border-2 !border-white !bg-brand"
          position={Position.Right}
          type="source"
        />
      ) : null}
    </div>
  );
}

function LegendShape({ nodeKind }: { nodeKind: FlowNodeKind }) {
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

export default function FlowchartPreview({ flowchart, onFlowchartChange }: FlowchartPreviewProps) {
  const { t } = useTranslation();
  const [nodes, setNodes] = useState<RequirementFlowNode[]>([]);
  const [edges, setEdges] = useState<RequirementFlowEdge[]>([]);
  const [selectedNodeId, setSelectedNodeId] = useState("");
  const [selectedEdgeId, setSelectedEdgeId] = useState("");

  const nodeTypes = useMemo<NodeTypes>(() => ({ requirementNode: EditableFlowNode }), []);

  const commitFlowchart = useCallback(
    (
      nextFlowchart: FlowchartDTO,
      options: {
        markFlowEdited?: boolean;
        resetTestCases?: boolean;
      } = {},
    ) => {
      onFlowchartChange(nextFlowchart, {
        markFlowEdited: options.markFlowEdited ?? true,
        resetTestCases: options.resetTestCases ?? true,
      });
    },
    [onFlowchartChange],
  );

  const handleNodeLabelChange = useCallback(
    (nodeId: string, label: string) => {
      if (!flowchart) {
        return;
      }

      commitFlowchart(updateFlowNodeLabel(flowchart, nodeId, label), {
        resetTestCases: true,
      });
    },
    [commitFlowchart, flowchart],
  );

  useEffect(() => {
    const animationFrameId = window.requestAnimationFrame(() => {
      if (!flowchart) {
        setNodes([]);
        setEdges([]);
        setSelectedNodeId("");
        setSelectedEdgeId("");
        return;
      }

      setNodes(createReactFlowNodes(flowchart, handleNodeLabelChange));
      setEdges(createReactFlowEdges(flowchart));
    });

    return () => window.cancelAnimationFrame(animationFrameId);
  }, [flowchart, handleNodeLabelChange]);

  const handleNodesChange = useCallback(
    (changes: NodeChange<RequirementFlowNode>[]) => {
      setNodes((currentNodes) => {
        const nextNodes = createReactFlowNodes(
          applyFlowNodeChanges(
            {
              nodes: currentNodes.map((node) => ({
                id: node.id,
                label: node.data.label,
                nodeKind: node.data.nodeKind,
                type: node.data.nodeType,
                position: node.position,
              })),
              edges: edges.map((edge) => ({
                id: edge.id,
                source: edge.source,
                target: edge.target,
                label:
                  typeof edge.data?.label === "string"
                    ? edge.data.label
                    : typeof edge.label === "string"
                      ? edge.label
                      : undefined,
                type: edge.data?.semantic,
              })),
              mermaid: flowchart?.mermaid,
            },
            changes,
          ),
          handleNodeLabelChange,
        );

        if (flowchart && shouldPersistNodeChanges(changes)) {
          commitFlowchart(
            {
              nodes: nextNodes.map((node) => ({
                id: node.id,
                label: node.data.label,
                nodeKind: node.data.nodeKind,
                type: node.data.nodeType,
                position: node.position,
              })),
              edges: edges.map((edge) => ({
                id: edge.id,
                source: edge.source,
                target: edge.target,
                label:
                  typeof edge.data?.label === "string"
                    ? edge.data.label
                    : typeof edge.label === "string"
                      ? edge.label
                      : undefined,
                type: edge.data?.semantic,
              })),
              mermaid: flowchart.mermaid,
            },
            {
              resetTestCases: shouldResetTestsForNodeChanges(changes),
            },
          );
        }

        return nextNodes;
      });
    },
    [commitFlowchart, edges, flowchart, handleNodeLabelChange],
  );

  const handleEdgesChange = useCallback(
    (changes: EdgeChange<RequirementFlowEdge>[]) => {
      setEdges((currentEdges) => {
        const currentFlowchart: FlowchartDTO = {
          nodes: nodes.map((node) => ({
            id: node.id,
            label: node.data.label,
            nodeKind: node.data.nodeKind,
            type: node.data.nodeType,
            position: node.position,
          })),
          edges: currentEdges.map((edge) => ({
            id: edge.id,
            source: edge.source,
            target: edge.target,
            label:
              typeof edge.data?.label === "string"
                ? edge.data.label
                : typeof edge.label === "string"
                  ? edge.label
                  : undefined,
            type: edge.data?.semantic,
          })),
          mermaid: flowchart?.mermaid,
        };
        const nextFlowchart = applyFlowEdgeChanges(currentFlowchart, changes);
        const nextEdges = createReactFlowEdges(nextFlowchart);

        if (flowchart && shouldPersistEdgeChanges(changes)) {
          commitFlowchart(nextFlowchart, {
            resetTestCases: shouldResetTestsForEdgeChanges(changes),
          });
        }

        return nextEdges;
      });
    },
    [commitFlowchart, flowchart, nodes],
  );

  const handleConnect = useCallback(
    (connection: Connection) => {
      if (!flowchart) {
        return;
      }

      commitFlowchart(connectFlowNodes(flowchart, connection), {
        resetTestCases: true,
      });
    },
    [commitFlowchart, flowchart],
  );

  const handleAddNode = () => {
    if (!flowchart) {
      return;
    }

    commitFlowchart(addFlowNode(flowchart), {
      resetTestCases: true,
    });
  };

  const handleDeleteNode = () => {
    if (!flowchart || !selectedNodeId) {
      return;
    }

    commitFlowchart(deleteFlowNode(flowchart, selectedNodeId), {
      resetTestCases: true,
    });
    setSelectedNodeId("");
  };

  const handleDeleteEdge = () => {
    if (!flowchart || !selectedEdgeId) {
      return;
    }

    commitFlowchart(deleteFlowEdge(flowchart, selectedEdgeId), {
      resetTestCases: true,
    });
    setSelectedEdgeId("");
  };

  return (
    <Panel className="flex min-h-[430px] flex-col overflow-hidden">
      <div className="flex flex-col gap-3 border-b border-border px-5 py-4 lg:flex-row lg:items-center lg:justify-between">
        <h2 className="text-sm font-semibold">{t("flowchartPreview")}</h2>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            className="h-8 px-3 text-xs"
            disabled={!flowchart}
            icon={<Plus className="h-3.5 w-3.5" />}
            onClick={handleAddNode}
            variant="secondary"
          >
            {t("addNode")}
          </Button>
          <Button
            type="button"
            className="h-8 px-3 text-xs"
            disabled={!selectedNodeId}
            icon={<Trash2 className="h-3.5 w-3.5" />}
            onClick={handleDeleteNode}
            variant="ghost"
          >
            {t("deleteNode")}
          </Button>
          <Button
            type="button"
            className="h-8 px-3 text-xs"
            disabled={!selectedEdgeId}
            icon={<Unlink className="h-3.5 w-3.5" />}
            onClick={handleDeleteEdge}
            variant="ghost"
          >
            {t("deleteEdge")}
          </Button>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-border bg-muted/40 px-5 py-3 text-xs text-muted-foreground">
        {NODE_KIND_ORDER.map((nodeKind) => (
          <div key={nodeKind} className="flex items-center gap-2">
            <LegendShape nodeKind={nodeKind} />
            <span>{NODE_KIND_REGISTRY[nodeKind].label}</span>
          </div>
        ))}
      </div>
      <div className="h-[520px] overflow-hidden">
        {flowchart ? (
          <ReactFlow
            connectionMode={ConnectionMode.Loose}
            deleteKeyCode={["Backspace", "Delete"]}
            edges={edges}
            edgesReconnectable
            fitView
            fitViewOptions={{ padding: 0.2 }}
            nodeTypes={nodeTypes}
            nodes={nodes}
            nodesConnectable
            nodesDraggable
            onConnect={handleConnect}
            onEdgeClick={(_, edge) => {
              setSelectedEdgeId(edge.id);
              setSelectedNodeId("");
            }}
            onEdgesChange={handleEdgesChange}
            onNodeClick={(_, node) => {
              setSelectedNodeId(node.id);
              setSelectedEdgeId("");
            }}
            onNodesChange={handleNodesChange}
            onPaneClick={() => {
              setSelectedEdgeId("");
              setSelectedNodeId("");
            }}
            proOptions={{ hideAttribution: true }}
          >
            <Background color="#cbd5e1" gap={18} />
            <Controls showInteractive={false} />
            <MiniMap
              maskColor="rgba(15, 23, 42, 0.08)"
              nodeColor={(node) => {
                const nodeKind = (node.data as RequirementFlowNode["data"]).nodeKind;
                return NODE_KIND_REGISTRY[nodeKind].miniMapColor;
              }}
              pannable
              zoomable
            />
          </ReactFlow>
        ) : (
          <div className="flex h-full items-center justify-center p-5">
            <p className="text-center text-sm text-muted-foreground">{t("emptyFlow")}</p>
          </div>
        )}
      </div>
    </Panel>
  );
}
