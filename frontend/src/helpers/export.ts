import { EXPORT_FORMAT_REGISTRY } from "@/constants";
import type { TestCaseDTO } from "@/types/requirement";

function downloadTextFile(filename: string, content: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function escapeCsvCell(value: string): string {
  return `"${value.replaceAll('"', '""')}"`;
}

export function exportMermaid(definition: string) {
  const format = EXPORT_FORMAT_REGISTRY.mermaid;
  downloadTextFile(format.filename, definition, format.mimeType);
}

export function exportTestCasesCsv(testCases: TestCaseDTO[]) {
  const header = ["Test case ID", "Title", "Preconditions", "Steps", "Expected result", "Priority"];

  const rows = testCases.map((testCase) => [
    testCase.id,
    testCase.title,
    testCase.preconditions,
    testCase.steps.map((step, index) => `${index + 1}. ${step}`).join("\n"),
    testCase.expectedResult,
    testCase.priority,
  ]);

  const csv = [header, ...rows]
    .map((row) => row.map((cell) => escapeCsvCell(cell)).join(","))
    .join("\n");

  const format = EXPORT_FORMAT_REGISTRY.csv;
  downloadTextFile(format.filename, csv, format.mimeType);
}
