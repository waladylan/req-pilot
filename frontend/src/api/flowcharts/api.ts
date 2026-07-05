import { axiosClient } from "@/lib/axios";
import type {
  GenerateFlowPayload,
  GenerateFlowResponseDTO,
  ReactFlowDefinitionDTO,
  ReactFlowEdgeDTO,
  ReactFlowNodeDTO,
} from "@/types/requirement";
import type {
  FlowEdgeSemantic,
  FlowNodeKind,
  FlowNodeType,
  FlowchartDTO,
} from "@/types/flow.types";

const FLOWCHART_GENERATE_ENDPOINT = "/api/flowcharts/generate";
const DEFAULT_FLOWCHART_GENERATION_TIMEOUT_MS = 180000;
const FLOWCHART_GENERATION_TIMEOUT_MS = resolveFlowchartGenerationTimeout();

export async function generateFlowchartFromRequirement(
  requirement: string,
  options?: GenerateFlowPayload["options"],
): Promise<GenerateFlowResponseDTO> {
  if (!requirement.trim()) {
    throw new Error("Please enter a requirement.");
  }

  const res = await axiosClient.post<GenerateFlowResponseDTO>(
    FLOWCHART_GENERATE_ENDPOINT,
    {
      requirement,
      ...(options ? { options } : {}),
    },
    {
      timeout: FLOWCHART_GENERATION_TIMEOUT_MS,
    },
  );
  return res.data;
}

export function assertReactFlowResponse(response: GenerateFlowResponseDTO): void {
  if (response.format !== "REACT_FLOW") {
    throw new Error("Unsupported response format.");
  }

  if (!response.flowchart || response.flowchart.format !== "REACT_FLOW") {
    throw new Error("Backend returned invalid flowchart response.");
  }
}

export function reactFlowDefinitionToFlowchartDTO(
  definition: ReactFlowDefinitionDTO,
): FlowchartDTO {
  return {
    nodes: definition.nodes.map(toFlowNodeDTO),
    edges: definition.edges.map(toFlowEdgeDTO),
  };
}

function toFlowNodeDTO(node: ReactFlowNodeDTO): FlowchartDTO["nodes"][number] {
  const label = textValue(node.data?.label) ?? node.id;
  const type = normalizeNodeType(node.type);

  return {
    id: node.id,
    label,
    title: label,
    type,
    nodeKind: normalizeNodeKind(node.type, node.data?.kind, label, type),
    description: textValue(node.data?.description),
    position: node.position
      ? {
          x: Math.round(node.position.x),
          y: Math.round(node.position.y),
        }
      : undefined,
  };
}

function toFlowEdgeDTO(edge: ReactFlowEdgeDTO): FlowchartDTO["edges"][number] {
  const label =
    textValue(edge.label) ?? textValue(edge.data?.label) ?? textValue(edge.data?.condition);

  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label,
    type: normalizeEdgeSemantic(label),
  };
}

function normalizeNodeType(value: string | undefined): FlowNodeType {
  const normalized = value?.trim().toLowerCase();
  if (normalized === "start") {
    return "START";
  }
  if (normalized === "end") {
    return "END";
  }
  if (normalized === "decision") {
    return "DECISION";
  }
  return "ACTION";
}

function normalizeNodeKind(
  reactFlowType: string | undefined,
  kind: string | null | undefined,
  label: string,
  type: FlowNodeType,
): FlowNodeKind {
  const normalized = `${reactFlowType ?? ""} ${kind ?? ""} ${label}`.toLowerCase();
  if (type === "START" || type === "END") {
    return "START_END";
  }
  if (type === "DECISION") {
    return "DECISION";
  }
  if (/\b(input|output|upload|enter|nhập)\b/.test(normalized)) {
    return "INPUT_OUTPUT";
  }
  if (/\b(error|fail|failure|invalid|lỗi)\b/.test(normalized)) {
    return "ERROR";
  }
  return "ACTION";
}

function normalizeEdgeSemantic(value: unknown): FlowEdgeSemantic | undefined {
  const normalized = textValue(value)?.toLowerCase();
  if (!normalized) {
    return undefined;
  }
  if (/\b(cancel|rejected|reject|rejects|invalid|no|fail|failure|error|không)\b/.test(normalized)) {
    return normalized.includes("cancel") ? "CANCEL" : "NO";
  }
  if (/\b(yes|approve|approves|approved|valid|success|pass|có|đúng)\b/.test(normalized)) {
    return normalized.includes("success") ? "SUCCESS" : "YES";
  }
  if (/\b(retry|try again|thử lại)\b/.test(normalized)) {
    return "RETRY";
  }
  return undefined;
}

function textValue(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function resolveFlowchartGenerationTimeout(): number {
  const configured = Number(import.meta.env.VITE_FLOWCHART_GENERATION_TIMEOUT_MS);
  return Number.isFinite(configured) && configured > 0
    ? configured
    : DEFAULT_FLOWCHART_GENERATION_TIMEOUT_MS;
}
