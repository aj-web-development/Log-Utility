import { useState } from "react";
import { Loader2, Sparkles } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, projectApi } from "@/lib/api";
import type { HighlightSegment } from "@/lib/types";
import type { WizardState } from "../types";
import { StepHeader } from "./StepHeader";

type SetState = (fn: (s: WizardState) => WizardState) => void;

export function SampleLineStep({ state, setState }: { state: WizardState; setState: SetState }) {
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [segments, setSegments] = useState<HighlightSegment[] | null>(null);

  async function analyze() {
    if (!state.sampleLine.trim()) return;
    setAnalyzing(true);
    setError(null);
    try {
      const analysis = await projectApi.analyzeSampleLine(state.sampleLine);
      setSegments(analysis.segments);
      setState((s) => ({
        ...s,
        linePattern: {
          timestampPattern: analysis.suggestedTimestampPattern ?? s.linePattern.timestampPattern,
          timestampRegexOrPosition: analysis.suggestedTimestampRegex ?? s.linePattern.timestampRegexOrPosition,
          levelPattern: analysis.suggestedLevelPattern ?? s.linePattern.levelPattern,
          loggerPattern: analysis.suggestedLoggerPattern ?? s.linePattern.loggerPattern,
        },
      }));
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "Could not analyze that line.");
    } finally {
      setAnalyzing(false);
    }
  }

  return (
    <div className="space-y-4">
      <StepHeader title="Sample log line" subtitle="Paste one representative line - we'll suggest the timestamp/level/logger patterns from it." />
      <Textarea
        value={state.sampleLine}
        onChange={(e) => setState((s) => ({ ...s, sampleLine: e.target.value }))}
        rows={4}
        className="font-mono text-xs leading-relaxed"
        placeholder="2026-07-24 09:41:03.812 [http-nio-8080-exec-4] INFO  c.example.OrderService - Order accepted"
      />
      <Button type="button" variant="outline" size="sm" onClick={analyze} disabled={analyzing || !state.sampleLine.trim()}>
        {analyzing ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : <Sparkles className="mr-1.5 h-3.5 w-3.5" />}
        Analyze
      </Button>
      {error && <div className="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">{error}</div>}
      {segments && (
        <div className="rounded-xl border bg-muted/40 p-3 text-xs">
          <div className="mb-1 text-[10px] uppercase tracking-widest text-muted-foreground">Preview - patterns below prefilled from this</div>
          <div className="font-mono whitespace-pre-wrap break-words">
            {segments.map((seg, i) => (
              <span key={i} className={seg.label ? "rounded bg-primary/15 px-0.5 text-primary" : undefined} title={seg.label ?? undefined}>
                {seg.text}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
