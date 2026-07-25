// LogLine.timestamp is a real absolute instant (ISO with a trailing Z) - the backend already
// converted the log's raw wall-clock digits through that project's configured time zone (see
// SearchServiceImpl/LinePattern.zoneId on the backend). This just renders it in the viewer's own
// local time, same as any other instant.
export function formatLocalTimestamp(isoInstant: string | null): string {
  if (!isoInstant) return "—";
  const d = new Date(isoInstant);
  if (Number.isNaN(d.getTime())) return isoInstant;
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

/**
 * How far `timeZone`'s wall clock is ahead of UTC at `instantMs`, in milliseconds. Uses Intl
 * (native, no date-timezone library) to look up the offset actually in effect at that moment, DST
 * included. Reference instant for that lookup is derived from the same wall-clock digits being
 * converted, so right at a DST transition (twice a year, one hour, only in zones that observe it)
 * the chosen offset can be the "wrong side" of the transition by up to an hour - not worth a fully
 * spec-correct gap/overlap resolver for a search date picker.
 */
function zoneOffsetMillis(instantMs: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(new Date(instantMs));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const wallClockAsIfUtc = Date.UTC(get("year"), get("month") - 1, get("day"), get("hour"), get("minute"), get("second"));
  return wallClockAsIfUtc - instantMs;
}

/** Date -> "yyyy-MM-ddTHH:mm" for a <input type="datetime-local">, showing the wall-clock digits
 * as they'd read in `timeZone` (the project's configured zone, from PublicProjectView.zoneId) -
 * NOT the browser's own zone, which may well be different from what the target app's logs use. */
export function toDatetimeLocal(d: Date, timeZone: string): string {
  const zoned = new Date(d.getTime() + zoneOffsetMillis(d.getTime(), timeZone));
  const p2 = (n: number) => String(n).padStart(2, "0");
  return `${zoned.getUTCFullYear()}-${p2(zoned.getUTCMonth() + 1)}-${p2(zoned.getUTCDate())}T${p2(zoned.getUTCHours())}:${p2(zoned.getUTCMinutes())}`;
}

/**
 * Inverse of {@link toDatetimeLocal}: a datetime-local input value, interpreted as wall-clock
 * digits in `timeZone` (not the browser's own zone) -> an absolute instant (ISO with Z) for
 * SearchRequest.from/to. Must agree with the zone the backend actually parses that project's raw
 * log digits in (LinePattern.resolveZoneId) or an explicit "from 11am to 7pm" search silently
 * targets the wrong window relative to what's on disk - see PublicProjectView.zoneId.
 */
export function toBackendDateTime(value: string, timeZone: string): string | undefined {
  if (!value) return undefined;
  const naiveUtcMs = Date.parse(value + "Z");
  if (Number.isNaN(naiveUtcMs)) return undefined;
  return new Date(naiveUtcMs - zoneOffsetMillis(naiveUtcMs, timeZone)).toISOString();
}
