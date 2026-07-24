import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { WizardState } from "../types";
import { StepHeader } from "./StepHeader";

type SetState = (fn: (s: WizardState) => WizardState) => void;

export function DetailsStep({ state, setState }: { state: WizardState; setState: SetState }) {
  return (
    <div className="space-y-4">
      <StepHeader title="Project details" subtitle="Give the project a name and a one-line description." />
      <div className="space-y-1.5">
        <Label htmlFor="wizard-name" className="text-xs">
          Name
        </Label>
        <Input id="wizard-name" value={state.name} onChange={(e) => setState((s) => ({ ...s, name: e.target.value }))} className="font-mono" />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="wizard-description" className="text-xs">
          Description
        </Label>
        <Textarea
          id="wizard-description"
          value={state.description}
          onChange={(e) => setState((s) => ({ ...s, description: e.target.value }))}
          rows={3}
        />
      </div>
    </div>
  );
}
