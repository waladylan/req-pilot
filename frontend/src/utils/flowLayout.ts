import { applyCalculatedNodeLayout } from "@/helpers/flowchart";
import type { FlowchartDTO } from "@/types/requirement";

export function applyAutoLayout(flowchart: FlowchartDTO): FlowchartDTO {
  return applyCalculatedNodeLayout(flowchart);
}
