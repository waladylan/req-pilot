import type { WfmDocument } from "@/types/requirement";

const UI_ONLY_FIELDS = new Set([
  "x",
  "y",
  "position",
  "color",
  "shape",
  "width",
  "height",
  "selected",
  "dragging",
  "sourceHandle",
  "targetHandle",
  "reactFlowType",
  "edgeLabel",
  "style",
  "className",
]);

export function wfmContainsUiOnlyFields(wfm: WfmDocument): boolean {
  return (
    hasUiOnlyField(wfm.ast.nodes.flatMap((node) => [node.data ?? {}, node])) ||
    hasUiOnlyField(wfm.ast.transitions.flatMap((transition) => [transition.data ?? {}, transition]))
  );
}

function hasUiOnlyField(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.some(hasUiOnlyField);
  }

  if (value && typeof value === "object") {
    return Object.entries(value).some(([key, nestedValue]) => {
      if (UI_ONLY_FIELDS.has(key)) {
        return true;
      }
      return hasUiOnlyField(nestedValue);
    });
  }

  return false;
}
