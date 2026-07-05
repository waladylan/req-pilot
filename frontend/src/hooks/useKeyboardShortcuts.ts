import { useEffect } from "react";

type UseKeyboardShortcutsParams = {
  deleteSelected: () => void;
  duplicateSelectedNode: () => void;
  redo: () => void;
  undo: () => void;
};

export function useKeyboardShortcuts({
  deleteSelected,
  duplicateSelectedNode,
  redo,
  undo,
}: UseKeyboardShortcutsParams) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target;
      if (
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement ||
        target instanceof HTMLSelectElement
      ) {
        return;
      }

      const isModifierPressed = event.metaKey || event.ctrlKey;

      if ((event.key === "Delete" || event.key === "Backspace") && !isModifierPressed) {
        event.preventDefault();
        deleteSelected();
        return;
      }

      if (isModifierPressed && event.key.toLowerCase() === "d") {
        event.preventDefault();
        duplicateSelectedNode();
        return;
      }

      if (isModifierPressed && event.key.toLowerCase() === "z" && event.shiftKey) {
        event.preventDefault();
        redo();
        return;
      }

      if (isModifierPressed && event.key.toLowerCase() === "z") {
        event.preventDefault();
        undo();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [deleteSelected, duplicateSelectedNode, redo, undo]);
}
