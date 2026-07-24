import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { WizardState } from "../types";
import { StepHeader } from "./StepHeader";

type SetState = (fn: (s: WizardState) => WizardState) => void;

export function LinePatternStep({ state, setState }: { state: WizardState; setState: SetState }) {
  function set<K extends keyof WizardState["linePattern"]>(key: K, value: string) {
    setState((s) => ({ ...s, linePattern: { ...s.linePattern, [key]: value } }));
  }

  return (
    <div className="space-y-4">
      <StepHeader
        title="Line pattern"
        subtitle="How to recognize a new log entry vs. a continuation line (stack trace, wrapped message). Prefilled from the sample line's analysis - override anything that's wrong."
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Timestamp pattern" value={state.linePattern.timestampPattern} onChange={(v) => set("timestampPattern", v)} placeholder="yyyy-MM-dd HH:mm:ss.SSS" />
        <Field
          label="Timestamp regex/position"
          value={state.linePattern.timestampRegexOrPosition}
          onChange={(v) => set("timestampRegexOrPosition", v)}
          placeholder={String.raw`^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}`}
        />
        <Field
          label="Level pattern"
          value={state.linePattern.levelPattern ?? ""}
          onChange={(v) => set("levelPattern", v)}
          placeholder={String.raw`\b(TRACE|DEBUG|INFO|WARN|ERROR)\b`}
        />
        <Field
          label="Logger pattern"
          value={state.linePattern.loggerPattern ?? ""}
          onChange={(v) => set("loggerPattern", v)}
          placeholder={String.raw`[a-zA-Z_$][\w$]*(\.[a-zA-Z_$][\w$]*)+`}
        />
      </div>
    </div>
  );
}

function Field({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="space-y-1.5">
      <Label className="text-xs">{label}</Label>
      <Input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} className="font-mono text-xs" />
    </div>
  );
}
