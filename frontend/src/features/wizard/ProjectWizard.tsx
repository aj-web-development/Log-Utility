import { useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeft, ArrowRight, Check } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ApiRequestError, projectApi } from "@/lib/api";
import type { ProjectDetailResponse, ProjectRequest } from "@/lib/types";
import { consumeWizardPrefill } from "./wizardPrefill";
import { emptyWizardState, newKey, wizardStateFromDetail, wizardStateToRequest, type WizardField, type WizardState } from "./types";
import { DetailsStep } from "./steps/DetailsStep";
import { NodesStep } from "./steps/NodesStep";
import { SampleLineStep } from "./steps/SampleLineStep";
import { LinePatternStep } from "./steps/LinePatternStep";
import { FieldsStep } from "./steps/FieldsStep";
import { ReviewStep } from "./steps/ReviewStep";

const STEPS = [
  { key: "details", label: "Details" },
  { key: "nodes", label: "Nodes" },
  { key: "sample", label: "Sample line" },
  { key: "pattern", label: "Line pattern" },
  { key: "fields", label: "Fields" },
  { key: "review", label: "Review" },
] as const;
type StepKey = (typeof STEPS)[number]["key"];

function applyUploadPrefill(state: WizardState): WizardState {
  const prefill = consumeWizardPrefill();
  if (!prefill) return state;
  const { logback } = prefill;

  const nodes = state.nodes.map((n, ni) =>
    ni === 0
      ? {
          ...n,
          logFiles: n.logFiles.map((f, fi) =>
            fi === 0
              ? {
                  ...f,
                  backupPathPattern: logback.backupPathPattern ?? f.backupPathPattern,
                  liveLogPath: logback.liveLogPathHint ?? f.liveLogPath,
                  backupRootPath: logback.backupRootHint ?? f.backupRootPath,
                }
              : f,
          ),
        }
      : n,
  );

  const fields: WizardField[] = logback.mdcFields.map((m) => ({
    _key: newKey(),
    key: m.suggestedKey,
    label: m.suggestedLabel,
    mdcKey: m.mdcKey,
    matchType: "EXACT_TOKEN",
    linePrefix: m.linePrefix,
  }));

  return { ...state, nodes, fields: fields.length ? fields : state.fields };
}

type ProjectWizardProps = { mode: "create" } | { mode: "edit"; project: ProjectDetailResponse };

export function ProjectWizard(props: ProjectWizardProps) {
  const [step, setStep] = useState<StepKey>("details");
  const idx = STEPS.findIndex((s) => s.key === step);
  const [state, setState] = useState<WizardState>(() =>
    props.mode === "edit" ? wizardStateFromDetail(props.project) : applyUploadPrefill(emptyWizardState()),
  );
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();

  const next = () => setStep(STEPS[Math.min(idx + 1, STEPS.length - 1)].key);
  const prev = () => setStep(STEPS[Math.max(idx - 1, 0)].key);

  async function submit() {
    setSubmitting(true);
    setSubmitError(null);
    const body: ProjectRequest = wizardStateToRequest(state);
    try {
      if (props.mode === "edit") {
        await projectApi.update(props.project.id, body);
      } else {
        await projectApi.create(body);
      }
      navigate({ to: "/admin/projects" });
    } catch (e) {
      setSubmitError(e instanceof ApiRequestError ? e.message : "Could not save this project.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-[11px] uppercase tracking-widest text-muted-foreground">Wizard</div>
          <h1 className="mt-1 font-display text-2xl font-semibold tracking-tight">{props.mode === "edit" ? `Edit ${props.project.name}` : "New project"}</h1>
        </div>
        <Button asChild variant="ghost" size="sm">
          <Link to="/admin/projects">Cancel</Link>
        </Button>
      </div>

      <div className="relative rounded-2xl border bg-card p-4 shadow-card">
        <ol className="grid grid-cols-3 gap-2 sm:grid-cols-6">
          {STEPS.map((s, i) => {
            const done = i < idx;
            const current = i === idx;
            return (
              <li key={s.key} className="flex flex-col items-center gap-1">
                <button type="button" onClick={() => setStep(s.key)} className="flex w-full items-center gap-2">
                  <div
                    className={cn(
                      "grid h-7 w-7 shrink-0 place-items-center rounded-full text-[11px] font-semibold transition-all",
                      done && "bg-primary text-primary-foreground",
                      current && "gradient-signal text-primary-foreground shadow-glow scale-110",
                      !done && !current && "bg-muted text-muted-foreground",
                    )}
                  >
                    {done ? <Check className="h-3.5 w-3.5" /> : i + 1}
                  </div>
                  {i < STEPS.length - 1 && (
                    <div className="hidden h-px flex-1 bg-border sm:block">
                      <div className="h-px bg-primary transition-all duration-500" style={{ width: done ? "100%" : "0%" }} />
                    </div>
                  )}
                </button>
                <div className={cn("truncate text-[11px]", current ? "font-medium text-foreground" : "text-muted-foreground")}>{s.label}</div>
              </li>
            );
          })}
        </ol>
      </div>

      <div key={step} className="animate-step-in rounded-2xl border bg-card p-6 shadow-card">
        {step === "details" && <DetailsStep state={state} setState={setState} />}
        {step === "nodes" && <NodesStep state={state} setState={setState} />}
        {step === "sample" && <SampleLineStep state={state} setState={setState} />}
        {step === "pattern" && <LinePatternStep state={state} setState={setState} />}
        {step === "fields" && <FieldsStep state={state} setState={setState} />}
        {step === "review" && <ReviewStep state={state} />}
      </div>

      {submitError && <div className="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">{submitError}</div>}

      <div className="flex items-center justify-between">
        <Button variant="ghost" onClick={prev} disabled={idx === 0}>
          <ArrowLeft className="mr-1 h-4 w-4" /> Back
        </Button>
        {idx < STEPS.length - 1 ? (
          <Button onClick={next} className="gradient-signal font-medium text-primary-foreground shadow-glow hover:opacity-90">
            Continue <ArrowRight className="ml-1 h-4 w-4" />
          </Button>
        ) : (
          <Button onClick={submit} disabled={submitting} className="gradient-signal font-medium text-primary-foreground shadow-glow hover:opacity-90">
            <Check className="mr-1 h-4 w-4" /> {props.mode === "edit" ? "Save changes" : "Create project"}
          </Button>
        )}
      </div>
    </div>
  );
}
