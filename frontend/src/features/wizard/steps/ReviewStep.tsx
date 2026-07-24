import type { ReactNode } from "react";

import type { WizardState } from "../types";
import { StepHeader } from "./StepHeader";

export function ReviewStep({ state }: { state: WizardState }) {
  const totalLogFiles = state.nodes.reduce((sum, n) => sum + n.logFiles.length, 0);
  return (
    <div className="space-y-4">
      <StepHeader title="Review" subtitle="Everything look right? Saving persists this configuration and it's immediately searchable." />
      <div className="grid gap-3 sm:grid-cols-2">
        <SummaryCard label="Name">{state.name || "—"}</SummaryCard>
        <SummaryCard label="Nodes">{state.nodes.length}</SummaryCard>
        <SummaryCard label="Log outputs">{totalLogFiles}</SummaryCard>
        <SummaryCard label="Fields">{state.fields.length}</SummaryCard>
      </div>
      {state.description && (
        <div className="rounded-xl border bg-muted/30 p-3">
          <div className="mb-1 text-[10px] uppercase tracking-widest text-muted-foreground">Description</div>
          <div className="text-sm">{state.description}</div>
        </div>
      )}
    </div>
  );
}

function SummaryCard({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-xl border bg-background p-3">
      <div className="text-[10px] uppercase tracking-widest text-muted-foreground">{label}</div>
      <div className="mt-1 font-display text-lg font-semibold tabular-nums">{children}</div>
    </div>
  );
}
