import type { HTMLAttributes, ReactNode } from "react";
import { createContext, useContext, useEffect, useId } from "react";
import { createPortal } from "react-dom";

import { cn } from "@/lib/utils";

type DialogContextValue = {
  descriptionId: string;
  onOpenChange: (open: boolean) => void;
  titleId: string;
};

const DialogContext = createContext<DialogContextValue | null>(null);

type DialogProps = {
  children: ReactNode;
  onOpenChange: (open: boolean) => void;
  open: boolean;
};

export function Dialog({ children, onOpenChange, open }: DialogProps) {
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onOpenChange(false);
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onOpenChange, open]);

  if (!open) {
    return null;
  }

  return createPortal(
    <DialogContext.Provider value={{ descriptionId, onOpenChange, titleId }}>
      <div
        className="pointer-events-auto fixed inset-0 z-[70] grid place-items-center bg-black/35 p-4 backdrop-blur-sm"
        onMouseDown={(event) => {
          if (event.currentTarget === event.target) {
            onOpenChange(false);
          }
        }}
      >
        {children}
      </div>
    </DialogContext.Provider>,
    document.body,
  );
}

type DialogContentProps = HTMLAttributes<HTMLDivElement>;

export function DialogContent({ className, ...props }: DialogContentProps) {
  const context = useDialogContext();

  return (
    <div
      aria-describedby={context.descriptionId}
      aria-labelledby={context.titleId}
      className={cn(
        "w-full max-w-lg rounded-xl border border-border/80 bg-comp-bg p-5 text-foreground shadow-[0_24px_70px_rgba(15,23,42,0.24)] outline-none",
        className,
      )}
      onMouseDown={(event) => event.stopPropagation()}
      role="dialog"
      aria-modal="true"
      {...props}
    />
  );
}

export function DialogHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("space-y-1.5", className)} {...props} />;
}

export function DialogTitle({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  const context = useDialogContext();

  return (
    <h2
      className={cn("text-base font-semibold leading-none tracking-normal", className)}
      id={context.titleId}
      {...props}
    />
  );
}

export function DialogDescription({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  const context = useDialogContext();

  return (
    <p
      className={cn("text-sm leading-5 text-muted-foreground", className)}
      id={context.descriptionId}
      {...props}
    />
  );
}

export function DialogFooter({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end", className)}
      {...props}
    />
  );
}

function useDialogContext(): DialogContextValue {
  const context = useContext(DialogContext);
  if (!context) {
    throw new Error("Dialog components must be rendered inside <Dialog />.");
  }
  return context;
}
