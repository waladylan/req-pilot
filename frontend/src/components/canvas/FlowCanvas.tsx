import {
  Background,
  ConnectionMode,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  applyEdgeChanges as applyReactFlowEdgeChanges,
  applyNodeChanges as applyReactFlowNodeChanges,
  type Connection,
  type EdgeChange,
  type NodeChange,
} from "@xyflow/react";
import type { DragEvent } from "react";
import { useCallback, useEffect, useRef, useState } from "react";

import { NODE_KIND_REGISTRY } from "@/constants";
import {
  createReactFlowEdges,
  createReactFlowNodes,
  type RequirementFlowEdge,
  type RequirementFlowNode,
} from "@/helpers/flowchart";
import { nodeKindToWfmRole } from "@/helpers/react-flow-to-wfm";
import type { FlowNodeKind, FlowchartDTO, WfmNodeRole } from "@/types/requirement";

import { CustomFlowNode } from "./CustomFlowNode";

const REQUIREMENT_NODE_TYPES = {
  requirementNode: CustomFlowNode,
};

type FlowCanvasProps = {
  fitViewSignal: number;
  flowchart?: FlowchartDTO;
  onAddNode: (nodeRole: WfmNodeRole, position?: { x: number; y: number }) => void;
  onClearSelection: () => void;
  onConnect: (connection: Connection) => void;
  onEdgeSelect: (edgeId: string) => void;
  onEdgesChange: (changes: EdgeChange<RequirementFlowEdge>[]) => void;
  onNodeLabelChange: (nodeId: string, label: string) => void;
  onNodeSelect: (nodeId: string) => void;
  onNodesChange: (changes: NodeChange<RequirementFlowNode>[]) => void;
  selectedEdgeId?: string;
  showBackground: boolean;
  showMiniMap: boolean;
  snapToGrid: boolean;
};

export function FlowCanvas(props: FlowCanvasProps) {
  return (
    <ReactFlowProvider>
      <FlowCanvasInner {...props} />
    </ReactFlowProvider>
  );
}

function FlowCanvasInner({
  fitViewSignal,
  flowchart,
  onAddNode,
  onClearSelection,
  onConnect,
  onEdgeSelect,
  onEdgesChange,
  onNodeLabelChange,
  onNodeSelect,
  onNodesChange,
  selectedEdgeId,
  showBackground,
  showMiniMap,
  snapToGrid,
}: FlowCanvasProps) {
  const { fitView, screenToFlowPosition } = useReactFlow();
  const onNodeLabelChangeRef = useRef(onNodeLabelChange);
  const [nodes, setNodes] = useState<RequirementFlowNode[]>([]);
  const [edges, setEdges] = useState<RequirementFlowEdge[]>([]);

  useEffect(() => {
    onNodeLabelChangeRef.current = onNodeLabelChange;
  }, [onNodeLabelChange]);

  const handleNodeLabelChange = useCallback((nodeId: string, label: string) => {
    onNodeLabelChangeRef.current(nodeId, label);
  }, []);

  useEffect(() => {
    const animationFrameId = window.requestAnimationFrame(() => {
      if (!flowchart) {
        setNodes([]);
        setEdges([]);
        return;
      }

      setNodes(createReactFlowNodes(flowchart, handleNodeLabelChange));
      setEdges(createReactFlowEdges(flowchart, selectedEdgeId));
    });

    return () => window.cancelAnimationFrame(animationFrameId);
  }, [flowchart, handleNodeLabelChange, selectedEdgeId]);

  useEffect(() => {
    if (fitViewSignal > 0) {
      window.requestAnimationFrame(() => fitView({ duration: 240, padding: 0.18 }));
    }
  }, [fitView, fitViewSignal]);

  const handleNodesChange = useCallback(
    (changes: NodeChange<RequirementFlowNode>[]) => {
      setNodes((currentNodes) => applyReactFlowNodeChanges(changes, currentNodes));

      if (shouldCommitNodeChanges(changes)) {
        onNodesChange(changes);
      }
    },
    [onNodesChange],
  );

  const handleEdgesChange = useCallback(
    (changes: EdgeChange<RequirementFlowEdge>[]) => {
      setEdges((currentEdges) => applyReactFlowEdgeChanges(changes, currentEdges));

      if (shouldCommitEdgeChanges(changes)) {
        onEdgesChange(changes);
      }
    },
    [onEdgesChange],
  );

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      const nodeRole = event.dataTransfer.getData("application/req-pilot-node-role") as
        WfmNodeRole | "";
      const legacyNodeKind = event.dataTransfer.getData("application/req-pilot-node-kind") as
        FlowNodeKind | "";
      const role = nodeRole || (legacyNodeKind ? nodeKindToWfmRole(legacyNodeKind) : "");
      if (!role) {
        return;
      }

      onAddNode(
        role,
        screenToFlowPosition({
          x: event.clientX,
          y: event.clientY,
        }),
      );
    },
    [onAddNode, screenToFlowPosition],
  );

  return (
    <div className="fixed inset-0 bg-background">
      <ReactFlow
        className="h-screen w-screen"
        connectionMode={ConnectionMode.Loose}
        deleteKeyCode={["Backspace", "Delete"]}
        edges={edges}
        edgesReconnectable
        edgesFocusable
        elementsSelectable
        fitView
        fitViewOptions={{ padding: 0.22 }}
        nodeTypes={REQUIREMENT_NODE_TYPES}
        nodes={nodes}
        nodesConnectable
        nodesDraggable
        onConnect={onConnect}
        onDragOver={(event) => {
          event.preventDefault();
          event.dataTransfer.dropEffect = "copy";
        }}
        onDrop={handleDrop}
        onEdgeClick={(_, edge) => onEdgeSelect(edge.id)}
        onEdgesChange={handleEdgesChange}
        onNodeClick={(_, node) => onNodeSelect(node.id)}
        onNodesChange={handleNodesChange}
        onPaneClick={onClearSelection}
        onSelectionChange={({ nodes: selectedNodes, edges: selectedEdges }) => {
          if (selectedNodes.length === 1) {
            onNodeSelect(selectedNodes[0].id);
            return;
          }

          if (selectedEdges.length === 1) {
            onEdgeSelect(selectedEdges[0].id);
            return;
          }

          // Controlled node/edge refreshes can briefly report an empty React Flow selection.
          // Pane clicks remain the explicit clear-selection path.
        }}
        proOptions={{ hideAttribution: true }}
        snapGrid={[20, 20]}
        snapToGrid={snapToGrid}
      >
        {showBackground ? <Background color="#cbd5e1" gap={18} /> : null}
        {/* <Controls
          className="!rounded-xl !border !border-border/80 !bg-comp-bg/95 !p-1 !shadow-[0_14px_38px_rgba(15,23,42,0.14)] !backdrop-blur-md [&_.react-flow__controls-button:hover]:!bg-muted [&_.react-flow__controls-button]:!h-7 [&_.react-flow__controls-button]:!w-7 [&_.react-flow__controls-button]:!border-border/80 [&_.react-flow__controls-button]:!bg-transparent"
          position="top-right"
          showInteractive={false}
        /> */}
        {showMiniMap ? (
          <MiniMap
            className="!rounded-xl !border !border-border/80 !bg-comp-bg/95 !shadow-[0_14px_38px_rgba(15,23,42,0.14)] !backdrop-blur-md"
            maskColor="rgba(15, 23, 42, 0.08)"
            nodeColor={(node) => {
              const nodeKind = (node.data as RequirementFlowNode["data"]).nodeKind;
              return NODE_KIND_REGISTRY[nodeKind].miniMapColor;
            }}
            pannable
            position="bottom-right"
            zoomable
          />
        ) : null}
      </ReactFlow>

      {!flowchart ? (
        <div className="pointer-events-none fixed inset-0 flex items-center justify-center">
          <div className="rounded-xl border border-border/80 bg-comp-bg/95 px-5 py-4 text-center shadow-[0_16px_45px_rgba(15,23,42,0.14)] backdrop-blur-md">
            <h1 className="text-base font-semibold">Requirement Pilot</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Add a requirement in the sidebar, then generate a flow.
            </p>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function shouldCommitNodeChanges(changes: NodeChange<RequirementFlowNode>[]) {
  return changes.some((change) => {
    if (change.type === "position") {
      return change.dragging !== true;
    }

    return change.type !== "select" && change.type !== "dimensions";
  });
}

function shouldCommitEdgeChanges(changes: EdgeChange<RequirementFlowEdge>[]) {
  return changes.some((change) => change.type !== "select");
}
