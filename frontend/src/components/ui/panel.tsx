import type { HTMLAttributes } from "react";

import { cn } from "@/lib/utils";

type PanelProps = HTMLAttributes<HTMLDivElement>;

export function Panel({ className, ...props }: PanelProps) {
  return (
    <section
      className={cn("rounded-lg border border-border bg-comp-bg shadow-panel", className)}
      {...props}
    />
  );
}
