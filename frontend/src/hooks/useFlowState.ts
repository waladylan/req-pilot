import { useCallback, useMemo, useState } from "react";
import type { Connection, EdgeChange, NodeChange, XYPosition } from "@xyflow/react";

import { DEFAULT_EDGE_SEMANTIC, DEFAULT_NODE_KIND } from "@/constants";
import {
  addFlowNodeByKind,
  applyFlowEdgeChanges,
  applyFlowNodeChanges,
  connectFlowNodes,
  deleteFlowEdge,
  deleteFlowNode,
  duplicateFlowNode,
  updateFlowEdgeLabel,
  updateFlowEdgeSemantic,
  updateFlowNodeKind,
  updateFlowNodeLabel,
  updateFlowNodeMetadata,
  type RequirementFlowEdge,
  type RequirementFlowNode,
} from "@/helpers/flowchart";
import { applyAutoLayout } from "@/utils/flowLayout";
import type {
  FlowEdgeSemantic,
  FlowNodeDTO,
  FlowNodeKind,
  FlowchartDTO,
} from "@/types/requirement";

type FlowStateChangeOptions = {
  markFlowEdited?: boolean;
  resetTestCases?: boolean;
};

type UseFlowStateParams = {
  flowchart?: FlowchartDTO;
  onFlowchartChange: (flowchart: FlowchartDTO, options?: FlowStateChangeOptions) => void;
};

const EMPTY_FLOWCHART: FlowchartDTO = {
  nodes: [],
  edges: [],
  mermaid: "flowchart LR\n",
};

export function useFlowState({ flowchart, onFlowchartChange }: UseFlowStateParams) {
  const [past, setPast] = useState<FlowchartDTO[]>([]);
  const [future, setFuture] = useState<FlowchartDTO[]>([]);
  const [selectedNodeId, setSelectedNodeId] = useState("");
  const [selectedEdgeId, setSelectedEdgeId] = useState("");

  const selectedNode = useMemo(
    () => flowchart?.nodes.find((node) => node.id === selectedNodeId),
    [flowchart?.nodes, selectedNodeId],
  );
  const selectedEdge = useMemo(
    () => flowchart?.edges.find((edge) => edge.id === selectedEdgeId),
    [flowchart?.edges, selectedEdgeId],
  );

  const commitFlowchart = useCallback(
    (
      nextFlowchart: FlowchartDTO,
      options: FlowStateChangeOptions & {
        pushHistory?: boolean;
      } = {},
    ) => {
      if (options.pushHistory ?? true) {
        setPast((items) => (flowchart ? [...items, flowchart] : items));
        setFuture([]);
      }

      onFlowchartChange(nextFlowchart, {
        markFlowEdited: options.markFlowEdited ?? true,
        resetTestCases: options.resetTestCases ?? true,
      });
    },
    [flowchart, onFlowchartChange],
  );

  const replaceFromRequirement = useCallback(
    (nextFlowchart: FlowchartDTO) => {
      setPast([]);
      setFuture([]);
      setSelectedNodeId("");
      setSelectedEdgeId("");
      onFlowchartChange(nextFlowchart, {
        markFlowEdited: false,
        resetTestCases: true,
      });
    },
    [onFlowchartChange],
  );

  const undo = useCallback(() => {
    setPast((items) => {
      const previous = items[items.length - 1];
      if (!previous || !flowchart) {
        return items;
      }

      setFuture((futureItems) => [flowchart, ...futureItems]);
      onFlowchartChange(previous, {
        markFlowEdited: true,
        resetTestCases: true,
      });
      return items.slice(0, -1);
    });
  }, [flowchart, onFlowchartChange]);

  const redo = useCallback(() => {
    setFuture((items) => {
      const next = items[0];
      if (!next || !flowchart) {
        return items;
      }

      setPast((pastItems) => [...pastItems, flowchart]);
      onFlowchartChange(next, {
        markFlowEdited: true,
        resetTestCases: true,
      });
      return items.slice(1);
    });
  }, [flowchart, onFlowchartChange]);

  const selectNode = useCallback((nodeId: string) => {
    setSelectedNodeId(nodeId);
    setSelectedEdgeId("");
  }, []);

  const selectEdge = useCallback((edgeId: string) => {
    setSelectedEdgeId(edgeId);
    setSelectedNodeId("");
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedNodeId("");
    setSelectedEdgeId("");
  }, []);

  const applyNodeChanges = useCallback(
    (changes: NodeChange<RequirementFlowNode>[]) => {
      if (!flowchart) {
        return;
      }

      const shouldPersist = changes.some(
        (change) => change.type !== "select" && change.type !== "dimensions",
      );
      if (!shouldPersist) {
        return;
      }

      commitFlowchart(applyFlowNodeChanges(flowchart, changes), {
        pushHistory: changes.some(
          (change) =>
            change.type === "remove" ||
            change.type === "add" ||
            change.type === "replace" ||
            (change.type === "position" && !change.dragging),
        ),
        resetTestCases: changes.some(
          (change) => !["select", "dimensions", "position"].includes(change.type),
        ),
        markFlowEdited: changes.some(
          (change) => !["select", "dimensions", "position"].includes(change.type),
        ),
      });
    },
    [commitFlowchart, flowchart],
  );

  const applyEdgeChanges = useCallback(
    (changes: EdgeChange<RequirementFlowEdge>[]) => {
      if (!flowchart) {
        return;
      }

      const shouldPersist = changes.some((change) => change.type !== "select");
      if (!shouldPersist) {
        return;
      }

      commitFlowchart(applyFlowEdgeChanges(flowchart, changes), {
        pushHistory: changes.some(
          (change) =>
            change.type === "remove" || change.type === "add" || change.type === "replace",
        ),
        resetTestCases: changes.some((change) => change.type !== "select"),
      });
    },
    [commitFlowchart, flowchart],
  );

  const connectNodes = useCallback(
    (connection: Connection, semantic: FlowEdgeSemantic = DEFAULT_EDGE_SEMANTIC) => {
      commitFlowchart(connectFlowNodes(flowchart ?? EMPTY_FLOWCHART, connection, semantic), {
        resetTestCases: true,
      });
    },
    [commitFlowchart, flowchart],
  );

  const addNode = useCallback(
    (nodeKind: FlowNodeKind = DEFAULT_NODE_KIND, position?: XYPosition) => {
      commitFlowchart(addFlowNodeByKind(flowchart ?? EMPTY_FLOWCHART, nodeKind, position), {
        resetTestCases: true,
      });
    },
    [commitFlowchart, flowchart],
  );

  const deleteSelected = useCallback(() => {
    if (!flowchart) {
      return;
    }

    if (selectedNodeId) {
      commitFlowchart(deleteFlowNode(flowchart, selectedNodeId), {
        resetTestCases: true,
      });
      clearSelection();
      return;
    }

    if (selectedEdgeId) {
      commitFlowchart(deleteFlowEdge(flowchart, selectedEdgeId), {
        resetTestCases: true,
      });
      clearSelection();
    }
  }, [clearSelection, commitFlowchart, flowchart, selectedEdgeId, selectedNodeId]);

  const duplicateSelectedNode = useCallback(() => {
    if (!flowchart || !selectedNodeId) {
      return;
    }

    commitFlowchart(duplicateFlowNode(flowchart, selectedNodeId), {
      resetTestCases: true,
    });
  }, [commitFlowchart, flowchart, selectedNodeId]);

  const updateNodeLabel = useCallback(
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

  const updateNodeKind = useCallback(
    (nodeId: string, nodeKind: FlowNodeKind) => {
      if (!flowchart) {
        return;
      }

      commitFlowchart(updateFlowNodeKind(flowchart, nodeId, nodeKind), {
        resetTestCases: true,
      });
    },
    [commitFlowchart, flowchart],
  );

  const updateNodeMetadata = useCallback(
    (
      nodeId: string,
      metadata: Pick<FlowNodeDTO, "description" | "precondition" | "expectedResult">,
    ) => {
      if (!flowchart) {
        return;
      }

      commitFlowchart(updateFlowNodeMetadata(flowchart, nodeId, metadata), {
        resetTestCases: true,
      });
    },
    [commitFlowchart, flowchart],
  );

  const updateEdgeLabel = useCallback(
    (edgeId: string, label: string) => {
      if (!flowchart) {
        return;
      }

      commitFlowchart(updateFlowEdgeLabel(flowchart, edgeId, label), {
        resetTestCases: true,
      });
    },
    [commitFlowchart, flowchart],
  );

  const updateEdgeSemantic = useCallback(
    (edgeId: string, semantic: FlowEdgeSemantic) => {
      if (!flowchart) {
        return;
      }

      commitFlowchart(updateFlowEdgeSemantic(flowchart, edgeId, semantic), {
        resetTestCases: true,
      });
    },
    [commitFlowchart, flowchart],
  );

  const deleteNodeById = useCallback(
    (nodeId: string) => {
      if (!flowchart) {
        return;
      }

      commitFlowchart(deleteFlowNode(flowchart, nodeId), {
        resetTestCases: true,
      });
      clearSelection();
    },
    [clearSelection, commitFlowchart, flowchart],
  );

  const deleteEdgeById = useCallback(
    (edgeId: string) => {
      if (!flowchart) {
        return;
      }

      commitFlowchart(deleteFlowEdge(flowchart, edgeId), {
        resetTestCases: true,
      });
      clearSelection();
    },
    [clearSelection, commitFlowchart, flowchart],
  );

  const autoLayout = useCallback(() => {
    if (!flowchart) {
      return;
    }

    commitFlowchart(applyAutoLayout(flowchart), {
      resetTestCases: false,
      markFlowEdited: false,
    });
  }, [commitFlowchart, flowchart]);

  return {
    addNode,
    applyEdgeChanges,
    applyNodeChanges,
    autoLayout,
    canRedo: future.length > 0,
    canUndo: past.length > 0,
    clearSelection,
    connectNodes,
    deleteEdgeById,
    deleteNodeById,
    deleteSelected,
    duplicateSelectedNode,
    redo,
    replaceFromRequirement,
    selectEdge,
    selectedEdge,
    selectedEdgeId,
    selectedNode,
    selectedNodeId,
    selectNode,
    undo,
    updateEdgeLabel,
    updateEdgeSemantic,
    updateNodeKind,
    updateNodeLabel,
    updateNodeMetadata,
  };
}
