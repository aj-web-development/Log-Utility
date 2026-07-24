import { Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { MatchType } from "@/lib/types";
import { emptyField, type WizardState } from "../types";
import { StepHeader } from "./StepHeader";

type SetState = (fn: (s: WizardState) => WizardState) => void;
const MATCH_TYPES: MatchType[] = ["EXACT_TOKEN", "SUBSTRING", "REGEX"];

export function FieldsStep({ state, setState }: { state: WizardState; setState: SetState }) {
  function updateField(i: number, patch: Partial<WizardState["fields"][number]>) {
    setState((s) => ({ ...s, fields: s.fields.map((f, j) => (i === j ? { ...f, ...patch } : f)) }));
  }

  return (
    <div className="space-y-4">
      <StepHeader
        title="Fields"
        subtitle="Searchable dimensions extracted from each line (trace id, session id, ...). Match type controls how a search value is compared against the line."
      />
      <div className="space-y-2">
        {state.fields.map((f, i) => (
          <div key={f._key} className="grid grid-cols-1 gap-2 rounded-xl border bg-background p-3 sm:grid-cols-[1fr_1fr_1fr_auto_1fr_auto]">
            <LabeledInput label="Key" value={f.key} onChange={(v) => updateField(i, { key: v })} />
            <LabeledInput label="Label" value={f.label} onChange={(v) => updateField(i, { label: v })} />
            <LabeledInput label="MDC key" value={f.mdcKey ?? ""} onChange={(v) => updateField(i, { mdcKey: v })} />
            <div className="space-y-1.5">
              <Label className="text-xs">Match</Label>
              <Select value={f.matchType} onValueChange={(v: MatchType) => updateField(i, { matchType: v })}>
                <SelectTrigger className="text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MATCH_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <LabeledInput label="Line prefix" value={f.linePrefix ?? ""} onChange={(v) => updateField(i, { linePrefix: v })} />
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 self-end text-muted-foreground hover:text-destructive"
              onClick={() => setState((s) => ({ ...s, fields: s.fields.filter((_, j) => j !== i) }))}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
      </div>
      <Button variant="outline" size="sm" onClick={() => setState((s) => ({ ...s, fields: [...s.fields, emptyField()] }))}>
        <Plus className="mr-1 h-3.5 w-3.5" /> Add field
      </Button>
    </div>
  );
}

function LabeledInput({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="space-y-1.5">
      <Label className="text-xs">{label}</Label>
      <Input value={value} onChange={(e) => onChange(e.target.value)} className="font-mono text-xs" />
    </div>
  );
}
