type TestCasePriorityRegistryItem = {
  label: string;
};

export const TEST_CASE_PRIORITY_REGISTRY = {
  HIGH: {
    label: "High",
  },
  MEDIUM: {
    label: "Medium",
  },
  LOW: {
    label: "Low",
  },
} as const satisfies Record<string, TestCasePriorityRegistryItem>;
