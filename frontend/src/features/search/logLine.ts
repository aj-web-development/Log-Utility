// The backend parses a timestamp as a naive LocalDateTime and treats it as UTC regardless of any
// zone/offset the raw line prints after it (see DefaultLogLineParser on the backend) - several
// projects' lines carry a static/misconfigured offset suffix that doesn't actually describe the
// zone the digits were written in, so this converts to the viewer's local time the same way.
export function formatLocalTimestamp(isoNaive: string | null): string {
  if (!isoNaive) return "—";
  const d = new Date(isoNaive.endsWith("Z") ? isoNaive : `${isoNaive}Z`);
  if (Number.isNaN(d.getTime())) return isoNaive;
  const p2 = (n: number) => String(n).padStart(2, "0");
  const p3 = (n: number) => String(n).padStart(3, "0");
  return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())} ${p2(d.getHours())}:${p2(d.getMinutes())}:${p2(d.getSeconds())}.${p3(d.getMilliseconds())}`;
}

const LEADING_TIMESTAMP_RE = /^\[?\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d{1,9})?(?:\s?(?:Z|[+-]\d{2}:?\d{2}))?\]?\s*/;

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** First physical line of a (possibly multi-line, continuation-folded) raw entry, with the
 * leading timestamp and level token stripped - the collapsed-card summary; the full `raw` text is
 * still shown in full when a card is expanded. */
export function summaryLine(raw: string, level: string | null): string {
  const newlineIdx = raw.indexOf("\n");
  let line = newlineIdx === -1 ? raw : raw.slice(0, newlineIdx);
  line = line.replace(LEADING_TIMESTAMP_RE, "");
  if (level) {
    const re = new RegExp(`\\[?\\b${escapeRegExp(level)}\\b\\]?\\s*[:\\-]?\\s*`);
    line = line.replace(re, "");
  }
  return line.trim() || line;
}

export const LEVEL_BUCKETS = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"] as const;
export type LevelBucket = (typeof LEVEL_BUCKETS)[number];

export function levelBucket(level: string | null): LevelBucket | null {
  if (!level) return null;
  const u = level.toUpperCase();
  if (u === "WARNING") return "WARN";
  if (u === "FATAL") return "ERROR";
  return (LEVEL_BUCKETS as readonly string[]).includes(u) ? (u as LevelBucket) : null;
}

export const LEVEL_CLASS: Record<LevelBucket, string> = {
  ERROR: "bg-level-error/15 text-level-error",
  WARN: "bg-level-warn/15 text-level-warn",
  INFO: "bg-level-info/15 text-level-info",
  DEBUG: "bg-level-debug/15 text-level-debug",
  TRACE: "bg-level-trace/15 text-level-trace",
};

export function toDatetimeLocal(d: Date): string {
  const p2 = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}T${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

/** datetime-local input value ("yyyy-MM-ddTHH:mm") -> backend LocalDateTime string, or undefined if empty. */
export function toBackendDateTime(value: string): string | undefined {
  return value ? `${value}:00` : undefined;
}
