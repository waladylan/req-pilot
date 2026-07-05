type SidebarTabRegistryItem = {
  iconName: "fileText" | "shapes" | "listChecks" | "settings";
  label: string;
};

type ToolbarActionRegistryItem = {
  label?: string;
  shortcut?: string;
  title: string;
};

type CanvasToolRegistryItem = {
  label: string;
};

type ExportFormatRegistryItem = {
  filename: string;
  label: string;
  mimeType: string;
  shortLabel: string;
  title: string;
};

export const SIDEBAR_TAB_REGISTRY = {
  requirement: {
    iconName: "fileText",
    label: "Requirement",
  },
  nodes: {
    iconName: "shapes",
    label: "Nodes",
  },
  testCases: {
    iconName: "listChecks",
    label: "Test Cases",
  },
  settings: {
    iconName: "settings",
    label: "Settings",
  },
} as const satisfies Record<string, SidebarTabRegistryItem>;

export const SIDEBAR_TAB_KEYS = {
  nodes: "nodes",
  requirement: "requirement",
  settings: "settings",
  testCases: "testCases",
} as const satisfies Record<keyof typeof SIDEBAR_TAB_REGISTRY, keyof typeof SIDEBAR_TAB_REGISTRY>;

export const SIDEBAR_TAB_ORDER = [
  SIDEBAR_TAB_KEYS.requirement,
  SIDEBAR_TAB_KEYS.nodes,
  SIDEBAR_TAB_KEYS.testCases,
  SIDEBAR_TAB_KEYS.settings,
] as const satisfies ReadonlyArray<keyof typeof SIDEBAR_TAB_REGISTRY>;

export const TOOLBAR_ACTION_REGISTRY = {
  toggleSidebar: {
    label: "Sidebar",
    title: "Toggle sidebar",
  },
  generateFlow: {
    label: "Generate",
    title: "Generate flow",
  },
  generateTestCases: {
    label: "Tests",
    title: "Generate test cases",
  },
  autoLayout: {
    label: "Layout",
    title: "Auto layout",
  },
  fitView: {
    label: "Fit",
    title: "Fit view",
  },
  undo: {
    shortcut: "Ctrl/Cmd+Z",
    title: "Undo",
  },
  redo: {
    shortcut: "Ctrl/Cmd+Shift+Z",
    title: "Redo",
  },
  toggleLegend: {
    title: "Toggle legend",
  },
} as const satisfies Record<string, ToolbarActionRegistryItem>;

export const CANVAS_TOOL_REGISTRY = {
  miniMap: {
    label: "Minimap",
  },
  background: {
    label: "Grid background",
  },
  legend: {
    label: "Legend",
  },
  snapToGrid: {
    label: "Snap to grid",
  },
} as const satisfies Record<string, CanvasToolRegistryItem>;

export const EXPORT_FORMAT_REGISTRY = {
  mermaid: {
    filename: "requirement-flow.mmd",
    label: "Mermaid",
    mimeType: "text/plain;charset=utf-8",
    shortLabel: "MMD",
    title: "Export Mermaid",
  },
  csv: {
    filename: "requirement-test-cases.csv",
    label: "CSV",
    mimeType: "text/csv;charset=utf-8",
    shortLabel: "CSV",
    title: "Export test cases CSV",
  },
} as const satisfies Record<string, ExportFormatRegistryItem>;
