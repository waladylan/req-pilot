import { useTranslation } from "react-i18next";

import { Panel } from "@/components/ui/panel";
import type { TestCaseDTO, TestCasePriority } from "@/types/requirement";

type TestCasesTableProps = {
  testCases: TestCaseDTO[];
};

const priorityClassName: Record<TestCasePriority, string> = {
  HIGH: "bg-red-50 text-red-700 ring-red-200",
  MEDIUM: "bg-amber-50 text-amber-700 ring-amber-200",
  LOW: "bg-slate-50 text-slate-700 ring-slate-200",
};

export default function TestCasesTable({ testCases }: TestCasesTableProps) {
  const { t } = useTranslation();

  return (
    <Panel className="overflow-hidden">
      <div className="border-b border-border px-5 py-4">
        <h2 className="text-sm font-semibold">{t("testCases")}</h2>
      </div>
      {testCases.length === 0 ? (
        <div className="flex min-h-52 items-center justify-center p-5">
          <p className="text-center text-sm text-muted-foreground">{t("emptyTestCases")}</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-[900px] border-collapse text-left text-sm">
            <thead className="bg-muted text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="w-24 px-4 py-3 font-semibold">{t("id")}</th>
                <th className="w-56 px-4 py-3 font-semibold">{t("title")}</th>
                <th className="w-56 px-4 py-3 font-semibold">{t("preconditions")}</th>
                <th className="w-72 px-4 py-3 font-semibold">{t("steps")}</th>
                <th className="w-72 px-4 py-3 font-semibold">{t("expectedResult")}</th>
                <th className="w-28 px-4 py-3 font-semibold">{t("priority")}</th>
              </tr>
            </thead>
            <tbody>
              {testCases.map((testCase) => (
                <tr key={testCase.id} className="border-t border-border align-top">
                  <td className="px-4 py-4 font-medium">{testCase.id}</td>
                  <td className="px-4 py-4">{testCase.title}</td>
                  <td className="px-4 py-4 text-muted-foreground">{testCase.preconditions}</td>
                  <td className="px-4 py-4">
                    <ol className="space-y-1">
                      {testCase.steps.map((step, index) => (
                        <li key={`${testCase.id}-${step}`} className="grid grid-cols-[1.5rem_1fr]">
                          <span className="text-muted-foreground">{index + 1}.</span>
                          <span>{step}</span>
                        </li>
                      ))}
                    </ol>
                  </td>
                  <td className="px-4 py-4 text-muted-foreground">{testCase.expectedResult}</td>
                  <td className="px-4 py-4">
                    <span
                      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${priorityClassName[testCase.priority]}`}
                    >
                      {testCase.priority}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
