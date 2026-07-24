import { useEffect, useMemo, useRef, useState, type UIEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { AlertTriangle, ChevronRight, Download, Loader2, Play, RefreshCw, Search as SearchIcon, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { ApiRequestError, searchApi } from "@/lib/api";
import type { LogLine, SearchProgress, SearchRequest, SearchResult, SearchSummary } from "@/lib/types";
import { useActiveProject } from "@/lib/useActiveProject";
import { cn } from "@/lib/utils";
import { LEVEL_BUCKETS, LEVEL_CLASS, formatLocalTimestamp, levelBucket, summaryLine, toBackendDateTime, toDatetimeLocal, type LevelBucket } from "./logLine";

const PAGE_SIZE = 50;
const QUICK_RANGES = [
  { label: "5m", ms: 5 * 60_000 },
  { label: "15m", ms: 15 * 60_000 },
  { label: "1h", ms: 60 * 60_000 },
  { label: "6h", ms: 6 * 60 * 60_000 },
  { label: "24h", ms: 24 * 60 * 60_000 },
  { label: "7d", ms: 7 * 24 * 60 * 60_000 },
];

export function SearchScreen() {
  const { activeId, project: activeSummary, projects, isLoading: projectsLoading } = useActiveProject();
  const { data: fields } = useQuery({
    queryKey: ["public-project", activeId],
    queryFn: () => searchApi.project(activeId as string),
    enabled: !!activeId,
  });

  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [quickRange, setQuickRange] = useState<string | null>(null);
  const [freeText, setFreeText] = useState("");
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [enabledLevels, setEnabledLevels] = useState<Set<LevelBucket>>(new Set(LEVEL_BUCKETS));
  const [density, setDensity] = useState<"comfortable" | "compact">(() => (localStorage.getItem("loguty.density") === "compact" ? "compact" : "comfortable"));
  const [expanded, setExpanded] = useState<string | null>(null);

  const [live, setLive] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [pageInput, setPageInput] = useState("1");
  const [result, setResult] = useState<SearchResult | null>(null);

  const [liveConnecting, setLiveConnecting] = useState(false);
  const [liveLines, setLiveLines] = useState<LogLine[]>([]);
  const [liveProgress, setLiveProgress] = useState<SearchProgress | null>(null);
  const [liveSummary, setLiveSummary] = useState<SearchSummary | null>(null);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    setResult(null);
    setLiveLines([]);
    setLiveSummary(null);
    setLiveProgress(null);
    stopLiveStream();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId]);

  useEffect(() => () => stopLiveStream(), []);

  function stopLiveStream() {
    esRef.current?.close();
    esRef.current = null;
    setLiveConnecting(false);
  }

  function onResultsScroll(e: UIEvent<HTMLDivElement>) {
    const open = e.currentTarget.scrollTop < 12;
    setFiltersOpen((prev) => (prev === open ? prev : open));
  }

  function toggleDensity(next: "comfortable" | "compact") {
    setDensity(next);
    try {
      localStorage.setItem("loguty.density", next);
    } catch {
      // Storage unavailable - density still applies for this page load.
    }
  }

  function pickRange(label: string, ms: number) {
    const now = new Date();
    setTo(toDatetimeLocal(now));
    setFrom(toDatetimeLocal(new Date(now.getTime() - ms)));
    setQuickRange(label);
  }

  function activeFilters(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(fieldValues)) if (v.trim()) out[k] = v.trim();
    return out;
  }

  function toggleLevel(l: LevelBucket) {
    setEnabledLevels((prev) => {
      const next = new Set(prev);
      if (next.has(l)) next.delete(l);
      else next.add(l);
      return next;
    });
  }

  async function runBatch(targetPage: number) {
    if (!activeId) return;
    stopLiveStream();
    setLoading(true);
    setError(null);
    try {
      const body: SearchRequest = {
        projectId: activeId,
        from: toBackendDateTime(from) ?? null,
        to: toBackendDateTime(to) ?? null,
        filters: activeFilters(),
        freeText: freeText.trim() || null,
        page: targetPage,
        pageSize: PAGE_SIZE,
      };
      const res = await searchApi.search(body);
      setResult(res);
      setPage(targetPage);
      setPageInput(String(targetPage + 1));
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "Search failed.");
    } finally {
      setLoading(false);
    }
  }

  function startLiveStream() {
    if (!activeId) return;
    stopLiveStream();
    setResult(null);
    setLiveLines([]);
    setLiveSummary(null);
    setLiveProgress(null);
    setError(null);
    setLiveConnecting(true);

    const url = searchApi.streamUrl({
      projectId: activeId,
      from: toBackendDateTime(from),
      to: toBackendDateTime(to),
      freeText: freeText.trim() || undefined,
      filters: activeFilters(),
    });
    const es = new EventSource(url);
    esRef.current = es;
    es.addEventListener("chunk", (ev) => {
      setLiveConnecting(false);
      const lines = JSON.parse((ev as MessageEvent).data) as LogLine[];
      setLiveLines((prev) => [...prev, ...lines]);
    });
    es.addEventListener("progress", (ev) => {
      setLiveConnecting(false);
      setLiveProgress(JSON.parse((ev as MessageEvent).data) as SearchProgress);
    });
    es.addEventListener("done", (ev) => {
      setLiveSummary(JSON.parse((ev as MessageEvent).data) as SearchSummary);
      stopLiveStream();
    });
    es.onerror = () => {
      if (es.readyState === EventSource.CLOSED) return;
      setError("Live search connection was interrupted.");
      stopLiveStream();
    };
  }

  function run() {
    if (live) startLiveStream();
    else runBatch(0);
  }

  const currentLines = live ? liveLines : (result?.lines ?? []);
  const visibleLines = useMemo(() => currentLines.filter((l) => { const b = levelBucket(l.level); return !b || enabledLevels.has(b); }), [currentLines, enabledLevels]);
  const totalMatched = live ? (liveSummary?.totalMatched ?? liveLines.length) : (result?.totalMatched ?? 0);
  const truncated = live ? (liveSummary?.truncated ?? false) : (result?.truncated ?? false);
  const unreachableNodes = live ? (liveSummary?.unreachableNodes ?? []) : (result?.unreachableNodes ?? []);
  const elapsedMillis = live ? liveSummary?.elapsedMillis : result?.elapsedMillis;
  const totalPages = !live && result ? Math.max(1, Math.ceil(result.totalMatched / PAGE_SIZE)) : 1;
  const isBusy = loading || liveConnecting;

  if (!projectsLoading && projects.length === 0) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
        <div className="font-display text-lg font-semibold">No projects configured yet</div>
        <p className="max-w-sm text-sm text-muted-foreground">Configure a project's nodes and log files before you can search.</p>
        <Button asChild className="gradient-signal font-medium text-primary-foreground shadow-glow hover:opacity-90">
          <Link to="/admin/projects/new">Configure a project</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3 p-3 sm:p-6">
      <div className="rounded-2xl border bg-card shadow-card">
        <div className="flex flex-wrap items-center gap-2 p-3">
          <div className="relative min-w-0 flex-1">
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={freeText}
              onChange={(e) => setFreeText(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && run()}
              placeholder="Search message text…"
              className="h-10 border-transparent bg-muted/40 pl-9 font-mono text-sm focus-visible:bg-background focus-visible:border-ring"
            />
          </div>
          <div className="flex items-center gap-1 rounded-lg bg-muted/40 p-1">
            {QUICK_RANGES.map((r) => (
              <button
                key={r.label}
                onClick={() => pickRange(r.label, r.ms)}
                className={cn(
                  "rounded-md px-2.5 py-1 font-mono text-xs transition-colors",
                  quickRange === r.label ? "bg-primary text-primary-foreground shadow-glow" : "text-muted-foreground hover:text-foreground",
                )}
              >
                {r.label}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-1.5 rounded-lg bg-muted/40 px-2 py-1.5 text-xs text-muted-foreground">
            <span>Live</span>
            <Switch checked={live} onCheckedChange={setLive} />
          </div>
          <Button variant="ghost" size="icon" className="h-9 w-9" onClick={() => setFiltersOpen((v) => !v)}>
            <SearchIcon className={cn("h-4 w-4 transition-transform", filtersOpen && "text-primary")} />
          </Button>
          <Button className="h-9 gap-1.5 gradient-signal font-medium text-primary-foreground shadow-glow hover:opacity-90" onClick={run} disabled={!activeId || isBusy}>
            {isBusy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Play className="h-3.5 w-3.5" fill="currentColor" />}
            Run
          </Button>
        </div>

        <div className={cn("grid border-t transition-[grid-template-rows] duration-300 ease-in-out", filtersOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]")}>
          <div className="overflow-hidden">
            <div className="space-y-2.5 px-3 py-2.5">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[11px] uppercase tracking-widest text-muted-foreground">When</span>
                <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  From
                  <Input
                    type="datetime-local"
                    value={from}
                    onChange={(e) => {
                      setFrom(e.target.value);
                      setQuickRange(null);
                    }}
                    className="h-8 w-auto text-xs"
                  />
                </label>
                <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  To
                  <Input
                    type="datetime-local"
                    value={to}
                    onChange={(e) => {
                      setTo(e.target.value);
                      setQuickRange(null);
                    }}
                    className="h-8 w-auto text-xs"
                  />
                </label>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[11px] uppercase tracking-widest text-muted-foreground">Levels</span>
                {LEVEL_BUCKETS.map((l) => (
                  <button
                    key={l}
                    onClick={() => toggleLevel(l)}
                    className={cn(
                      "rounded-full border px-2.5 py-0.5 font-mono text-[11px] font-medium transition-all",
                      enabledLevels.has(l) ? cn(LEVEL_CLASS[l], "border-transparent") : "border-border bg-transparent text-muted-foreground hover:text-foreground",
                    )}
                  >
                    {l}
                  </button>
                ))}
                {fields && fields.fields.length > 0 && <div className="mx-1 h-4 w-px bg-border" />}
                {fields?.fields.map((f) => (
                  <label key={f.key} className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    {f.label}
                    <Input
                      value={fieldValues[f.key] ?? ""}
                      onChange={(e) => setFieldValues((prev) => ({ ...prev, [f.key]: e.target.value }))}
                      className="h-8 w-32 font-mono text-xs"
                    />
                  </label>
                ))}
                <div className="ml-auto flex items-center gap-1 rounded-md bg-muted/40 p-1">
                  {(["comfortable", "compact"] as const).map((d) => (
                    <button
                      key={d}
                      onClick={() => toggleDensity(d)}
                      className={cn("rounded px-2 py-0.5 text-[11px] capitalize transition-colors", density === d ? "bg-background text-foreground shadow-sm" : "text-muted-foreground")}
                    >
                      {d}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {(truncated || unreachableNodes.length > 0) && (
        <div className="flex items-start gap-2 rounded-xl border border-level-warn/30 bg-level-warn/10 px-3 py-2 text-xs text-level-warn">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <div>
            {truncated && <div>Results were truncated at the server's cap - narrow the date range or filters to see everything.</div>}
            {unreachableNodes.length > 0 && <div>Unreachable, skipped: {unreachableNodes.join(", ")}</div>}
          </div>
        </div>
      )}

      {error && (
        <div className="flex items-center gap-1.5 rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">
          <AlertTriangle className="h-3.5 w-3.5 shrink-0" /> {error}
        </div>
      )}

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border bg-card shadow-card">
        <div className="flex flex-none flex-wrap items-center justify-between gap-2 border-b px-4 py-2 text-xs text-muted-foreground">
          <div className="flex items-center gap-2">
            <span className="font-mono">
              <span className="text-foreground font-semibold">{visibleLines.length.toLocaleString()}</span> shown
              {totalMatched > visibleLines.length && ` of ${totalMatched.toLocaleString()} matched`}
            </span>
            {elapsedMillis !== undefined && (
              <>
                <span>·</span>
                <span className="font-mono">{elapsedMillis}ms</span>
              </>
            )}
            {live && liveProgress && !liveSummary && (
              <>
                <span>·</span>
                <span className="font-mono">
                  {liveProgress.nodesCompleted}/{liveProgress.nodesTotal} nodes
                </span>
              </>
            )}
          </div>
          <div className="flex items-center gap-2">
            {live && !liveSummary && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-2 py-0.5 text-primary">
                <span className="h-1.5 w-1.5 rounded-full bg-primary animate-pulse-dot" />
                live
              </span>
            )}
            {activeId && (result || liveLines.length > 0) && (
              <a
                href={searchApi.exportUrl({ projectId: activeId, from: toBackendDateTime(from), to: toBackendDateTime(to), freeText: freeText.trim() || undefined, filters: activeFilters() })}
                className="inline-flex h-7 items-center gap-1 rounded px-2 text-muted-foreground hover:text-foreground"
              >
                <Download className="h-3.5 w-3.5" /> Export
              </a>
            )}
            <Button variant="ghost" size="icon" className="h-7 w-7" onClick={run} disabled={!activeId || isBusy}>
              <RefreshCw className={cn("h-3.5 w-3.5", isBusy && "animate-spin")} />
            </Button>
          </div>
        </div>

        <div className="flex-1 overflow-auto font-mono text-[12.5px]" onScroll={onResultsScroll}>
          {visibleLines.length === 0 && !isBusy && (
            <div className="flex h-full min-h-64 flex-col items-center justify-center gap-2 p-8 text-center">
              <div className="grid h-12 w-12 place-items-center rounded-full bg-muted">
                <X className="h-5 w-5 text-muted-foreground" />
              </div>
              <div className="font-display text-sm font-medium">{result || liveSummary ? "No events match" : "Run a search to see results"}</div>
              <div className="text-xs text-muted-foreground">Widen the time range or clear a level filter.</div>
            </div>
          )}
          {visibleLines.map((log, i) => {
            const cardKey = `${log.nodeLabel}-${log.fileLabel}-${log.timestamp}-${i}`;
            const isOpen = expanded === cardKey;
            const pad = density === "compact" ? "py-1" : "py-1.5";
            const bucket = levelBucket(log.level);
            return (
              <div key={cardKey} className="group border-b border-border/40 transition-colors hover:bg-accent/30">
                <button onClick={() => setExpanded(isOpen ? null : cardKey)} className={cn("flex w-full items-start gap-3 px-4 text-left", pad)}>
                  <ChevronRight className={cn("mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground transition-transform", isOpen && "rotate-90 text-primary")} />
                  <span className="w-[150px] shrink-0 tabular-nums text-muted-foreground">{formatLocalTimestamp(log.timestamp)}</span>
                  <span className={cn("w-14 shrink-0 rounded px-1.5 py-0.5 text-center text-[10.5px] font-semibold tracking-wider", bucket ? LEVEL_CLASS[bucket] : "bg-muted text-muted-foreground")}>
                    {log.level ?? "—"}
                  </span>
                  <span className="hidden w-32 shrink-0 truncate text-muted-foreground md:inline">
                    {log.nodeLabel} · {log.fileLabel}
                  </span>
                  <span className="min-w-0 flex-1 truncate">{summaryLine(log.raw, log.level)}</span>
                </button>
                {isOpen && (
                  <div className="animate-step-in border-t border-border/50 bg-muted/30 px-4 py-3">
                    <div className="mb-1.5 text-[10px] uppercase tracking-widest text-muted-foreground">
                      {log.nodeLabel} · {log.fileLabel}
                    </div>
                    <pre className="whitespace-pre-wrap break-words rounded-lg bg-background p-3 text-[12px] leading-relaxed">{log.raw}</pre>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {!live && result && result.totalMatched > 0 && (
          <div className="flex flex-none items-center justify-between gap-2 border-t px-4 py-2 text-xs text-muted-foreground">
            <Button variant="ghost" size="sm" disabled={page === 0 || loading} onClick={() => runBatch(page - 1)}>
              Previous
            </Button>
            <div className="flex items-center gap-1.5">
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <Input
                value={pageInput}
                onChange={(e) => setPageInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key !== "Enter") return;
                  const n = Math.min(Math.max(1, Number(pageInput) || 1), totalPages);
                  runBatch(n - 1);
                }}
                className="h-7 w-14 text-center text-xs"
              />
            </div>
            <Button variant="ghost" size="sm" disabled={page + 1 >= totalPages || loading} onClick={() => runBatch(page + 1)}>
              Next
            </Button>
          </div>
        )}
      </div>

      {activeSummary === null && !projectsLoading && (
        <div className="text-center text-xs text-muted-foreground">Pick a project from the switcher above to start searching.</div>
      )}
    </div>
  );
}
