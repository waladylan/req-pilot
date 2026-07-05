import type { TextareaHTMLAttributes } from "react";

import { cn } from "@/lib/utils";

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement>;

export function Textarea({ className, ...props }: TextareaProps) {
  return (
    <textarea
      className={cn(
        "min-h-80 w-full resize-none rounded-lg border border-border bg-comp-bg p-4 text-sm leading-6 text-foreground shadow-panel outline-none transition placeholder:text-muted-foreground focus:border-brand focus:ring-4 focus:ring-brand/10",
        className,
      )}
      {...props}
    />
  );
}
