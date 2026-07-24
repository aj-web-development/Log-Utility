import { useState } from "react";
import { CircleDot, Loader2, Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { ApiRequestError, projectApi } from "@/lib/api";
import type { CheckStatus } from "@/lib/types";
import { emptyLogFile, emptyNode, type WizardLogFile, type WizardState } from "../types";
import { StepHeader } from "./StepHeader";

type SetState = (fn: (s: WizardState) => WizardState) => void;

export function NodesStep({ state, setState }: { state: WizardState; setState: SetState }) {
  return (
    <div className="space-y-6">
      <StepHeader
        title="Nodes"
        subtitle="Each node can write more than one log output (app.log, error.log, ...). List every live path and its backup/rotation pattern, then test."
      />
      {state.nodes.map((node, ni) => (
        <div key={node._key} className="space-y-3 rounded-xl border bg-background p-3">
          <div className="flex items-center gap-2">
            <Input
              value={node.nodeLabel}
              placeholder="Node label (e.g. node-a)"
              onChange={(e) => setState((s) => ({ ...s, nodes: s.nodes.map((n, i) => (i === ni ? { ...n, nodeLabel: e.target.value } : n)) }))}
              className="font-mono text-sm"
            />
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 shrink-0 text-muted-foreground hover:text-destructive"
              disabled={state.nodes.length === 1}
              onClick={() => setState((s) => ({ ...s, nodes: s.nodes.filter((_, i) => i !== ni) }))}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>

          <div className="space-y-2 pl-1">
            {node.logFiles.map((file, fi) => (
              <LogFileRow
                key={file._key}
                file={file}
                onChange={(next) =>
                  setState((s) => ({
                    ...s,
                    nodes: s.nodes.map((n, i) => (i === ni ? { ...n, logFiles: n.logFiles.map((f, j) => (j === fi ? next : f)) } : n)),
                  }))
                }
                onRemove={
                  node.logFiles.length > 1
                    ? () =>
                        setState((s) => ({
                          ...s,
                          nodes: s.nodes.map((n, i) => (i === ni ? { ...n, logFiles: n.logFiles.filter((_, j) => j !== fi) } : n)),
                        }))
                    : undefined
                }
              />
            ))}
            <Button
              variant="outline"
              size="sm"
              onClick={() => setState((s) => ({ ...s, nodes: s.nodes.map((n, i) => (i === ni ? { ...n, logFiles: [...n.logFiles, emptyLogFile()] } : n)) }))}
            >
              <Plus className="mr-1 h-3.5 w-3.5" /> Add log output
            </Button>
          </div>
        </div>
      ))}
      <Button variant="outline" size="sm" onClick={() => setState((s) => ({ ...s, nodes: [...s.nodes, emptyNode()] }))}>
        <Plus className="mr-1 h-3.5 w-3.5" /> Add node
      </Button>
    </div>
  );
}

function LogFileRow({ file, onChange, onRemove }: { file: WizardLogFile; onChange: (next: WizardLogFile) => void; onRemove?: () => void }) {
  const [testing, setTesting] = useState(false);

  async function test() {
    setTesting(true);
    try {
      const outcome = await projectApi.checkPath({ livePath: file.liveLogPath, backupPath: file.backupRootPath, logFileId: file.logFileId });
      onChange({ ...file, lastCheckStatus: outcome.status, lastCheckMessage: outcome.message });
    } catch (e) {
      onChange({ ...file, lastCheckStatus: "UNREACHABLE", lastCheckMessage: e instanceof ApiRequestError ? e.message : "Check failed" });
    } finally {
      setTesting(false);
    }
  }

  return (
    <div className="grid grid-cols-1 gap-2 rounded-lg border bg-card p-2.5 sm:grid-cols-[1fr_1fr_1fr_auto_auto]">
      <Input value={file.fileLabel} placeholder="Label (e.g. App Log)" onChange={(e) => onChange({ ...file, fileLabel: e.target.value })} className="text-sm" />
      <Input value={file.liveLogPath} placeholder="Live path" onChange={(e) => onChange({ ...file, liveLogPath: e.target.value })} className="font-mono text-sm" />
      <Input
        value={file.backupRootPath}
        placeholder="Backup root"
        onChange={(e) => onChange({ ...file, backupRootPath: e.target.value })}
        className="font-mono text-sm"
      />
      <PathStatusBadge status={file.lastCheckStatus} message={file.lastCheckMessage} />
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="sm" onClick={test} disabled={testing}>
          {testing ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : <CircleDot className="mr-1 h-3.5 w-3.5" />} Test
        </Button>
        {onRemove && (
          <Button variant="ghost" size="icon" className="h-8 w-8 text-muted-foreground hover:text-destructive" onClick={onRemove}>
            <Trash2 className="h-4 w-4" />
          </Button>
        )}
      </div>
      <Input
        value={file.backupPathPattern}
        placeholder="Backup pattern, e.g. {date}/app.{HH}.{i}.log.gz"
        onChange={(e) => onChange({ ...file, backupPathPattern: e.target.value })}
        className="col-span-full font-mono text-xs"
      />
    </div>
  );
}

function PathStatusBadge({ status, message }: { status?: CheckStatus; message?: string | null }) {
  const map: Record<CheckStatus, { cls: string; label: string }> = {
    REACHABLE: { cls: "bg-primary/15 text-primary", label: "Reachable" },
    UNREACHABLE: { cls: "bg-destructive/15 text-destructive", label: "Unreachable" },
    UNKNOWN: { cls: "bg-muted text-muted-foreground", label: "Not tested" },
  };
  const m = map[status ?? "UNKNOWN"];
  return (
    <span title={message ?? undefined} className={cn("inline-flex items-center gap-1 self-center rounded-full px-2 py-0.5 text-[11px] font-medium", m.cls)}>
      <CircleDot className="h-3 w-3" /> {m.label}
    </span>
  );
}
