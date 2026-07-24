import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useRef, useState } from "react";
import { AlertCircle, ArrowRight, FileCode2, Loader2, UploadCloud, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { projectApi, ApiRequestError } from "@/lib/api";
import { setWizardPrefill } from "@/features/wizard/wizardPrefill";

export const Route = createFileRoute("/_shell/admin/projects/upload")({
  component: UploadPage,
});

function UploadPage() {
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  async function continueToWizard() {
    if (!file) return;
    setParsing(true);
    setError(null);
    try {
      const result = await projectApi.parseLogback(file);
      setWizardPrefill({ logback: result });
      navigate({ to: "/admin/projects/new" });
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "Could not parse that file.");
    } finally {
      setParsing(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 p-6">
      <div>
        <div className="text-[11px] uppercase tracking-widest text-muted-foreground">Step 0 · optional</div>
        <h1 className="mt-1 font-display text-2xl font-semibold tracking-tight">Upload logback-spring.xml</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          We'll parse MDC fields (<code className="rounded bg-muted px-1 py-0.5 text-xs">%X&#123;…&#125;</code>) and the rolling-file pattern to
          pre-fill the wizard. You still confirm per-node paths on the next screen.
        </p>
      </div>

      <label
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          const f = e.dataTransfer.files?.[0];
          if (f) setFile(f);
        }}
        className={cn(
          "group flex cursor-pointer flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed bg-card p-10 text-center shadow-card transition-all",
          dragging ? "border-primary bg-primary/5 scale-[1.01]" : "border-border hover:border-primary/60",
        )}
      >
        <input ref={input} type="file" accept=".xml" className="sr-only" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <div className={cn("grid h-14 w-14 place-items-center rounded-2xl gradient-signal shadow-glow transition-transform", dragging && "scale-110")}>
          <UploadCloud className="h-6 w-6 text-primary-foreground" />
        </div>
        <div>
          <div className="font-display text-base font-semibold">Drop your logback-spring.xml here</div>
          <div className="text-sm text-muted-foreground">or click to browse — XML only, up to 1 MB</div>
        </div>
      </label>

      {file && (
        <div className="flex items-center gap-3 rounded-2xl border bg-card p-3 shadow-card animate-step-in">
          <div className="grid h-10 w-10 place-items-center rounded-lg bg-primary/10 text-primary">
            <FileCode2 className="h-5 w-5" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate font-mono text-sm font-medium">{file.name}</div>
            <div className="text-[11px] text-muted-foreground">{(file.size / 1024).toFixed(1)} KB · ready to parse</div>
          </div>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => setFile(null)}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {error && (
        <div className="flex items-center gap-1.5 rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" /> {error}
        </div>
      )}

      <div className="flex items-center justify-between pt-2">
        <Button variant="ghost" onClick={() => navigate({ to: "/admin/projects/new" })}>
          Skip
        </Button>
        <Button
          disabled={!file || parsing}
          onClick={continueToWizard}
          className="gradient-signal font-medium text-primary-foreground shadow-glow hover:opacity-90 disabled:opacity-40"
        >
          {parsing ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : null}
          Continue <ArrowRight className="ml-1 h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
