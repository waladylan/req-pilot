import { buildMermaidFromFlowchart } from "@/helpers/flowchart";
import { buildMermaidFromWfm, wfmToFlowchartDTO } from "@/helpers/wfm-to-react-flow";
import type { FlowchartDTO, GenerateTestCasesPayload, WfmDocument } from "@/types/requirement";

type CreateGenerateTestCasesPayloadParams = {
  flowchart?: FlowchartDTO;
  requirement: string;
  wfm?: WfmDocument;
};

export function createGenerateTestCasesPayload({
  flowchart,
  requirement,
  wfm,
}: CreateGenerateTestCasesPayloadParams): GenerateTestCasesPayload {
  if (wfm) {
    return {
      requirement,
      wfm,
    };
  }

  if (!flowchart) {
    return { requirement };
  }

  return {
    requirement,
    flowchart: {
      ...flowchart,
      mermaid: buildMermaidFromFlowchart(flowchart),
    },
  };
}

export function getRenderableFlowchartForTestCases(
  flowchart: FlowchartDTO | undefined,
  wfm: WfmDocument | undefined,
): FlowchartDTO | undefined {
  if (!wfm) {
    return flowchart;
  }

  return {
    ...wfmToFlowchartDTO(wfm),
    mermaid: buildMermaidFromWfm(wfm),
  };
}
