import { useQueries } from "@tanstack/react-query";

import { projectApi } from "./api";
import type { ProjectDetailResponse } from "./types";

export type ProjectStatus = "healthy" | "degraded" | "silent";

/**
 * The list/summary endpoint doesn't carry per-log-file check results, so this needs each
 * project's full detail (see useProjectStatuses). No node/log-file configured yet -> "silent";
 * any file that failed its last path check -> "degraded"; everything checked and reachable (or
 * simply not checked yet) -> "healthy".
 */
export function deriveProjectStatus(detail: ProjectDetailResponse): ProjectStatus {
  const statuses = detail.nodes.flatMap((n) => n.logFiles.map((f) => f.lastCheckStatus));
  if (statuses.length === 0) return "silent";
  if (statuses.some((s) => s === "UNREACHABLE")) return "degraded";
  return "healthy";
}

export const STATUS_DOT_CLASS: Record<ProjectStatus, string> = {
  healthy: "bg-primary",
  degraded: "bg-[oklch(0.78_0.16_80)]",
  silent: "bg-muted-foreground/40",
};

export const STATUS_PILL_CLASS: Record<ProjectStatus, string> = {
  healthy: "bg-primary/15 text-primary",
  degraded: "bg-[oklch(0.78_0.16_80)]/15 text-[oklch(0.78_0.16_80)]",
  silent: "bg-muted text-muted-foreground",
};

/** Fetches each project's detail (Basic-auth) in parallel to derive a real status per id. */
export function useProjectStatuses(ids: string[]): Record<string, ProjectStatus | undefined> {
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: ["project-detail", id],
      queryFn: () => projectApi.get(id),
      staleTime: 30_000,
    })),
  });
  const map: Record<string, ProjectStatus | undefined> = {};
  ids.forEach((id, i) => {
    const data = results[i]?.data;
    map[id] = data ? deriveProjectStatus(data) : undefined;
  });
  return map;
}
