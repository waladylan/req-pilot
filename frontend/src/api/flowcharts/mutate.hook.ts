import { useMutation } from "@tanstack/react-query";

import type { GenerateFlowPayload, GenerateFlowResponseDTO } from "@/types/requirement";

import { generateFlowchartFromRequirement } from "./api";

export function useGenerateFlow() {
  const { mutateAsync, isPending, error } = useMutation<
    GenerateFlowResponseDTO,
    Error,
    GenerateFlowPayload
  >({
    mutationFn: ({ requirement, options }) => generateFlowchartFromRequirement(requirement, options),
  });

  return {
    generateFlow: mutateAsync,
    isPending,
    error,
  };
}
