import { useState } from "react";
import DatePicker, { type ReactDatePickerCustomHeaderProps } from "react-datepicker";
import { CalendarIcon, ChevronLeft, ChevronRight } from "lucide-react";
import "react-datepicker/dist/react-datepicker.css";

import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";

const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
const CURRENT_YEAR = new Date().getFullYear();
// Logs can't exist for a day that hasn't happened yet - the year list stops at the current year
// (paired with `maxDate` on the calendar below) instead of offering years that would show as an
// all-disabled month.
const YEARS = Array.from({ length: 13 }, (_, i) => CURRENT_YEAR - 12 + i);
const HOURS = Array.from({ length: 24 }, (_, i) => i);
const MINUTES = Array.from({ length: 60 }, (_, i) => i);
const p2 = (n: number) => String(n).padStart(2, "0");

// Matches the "yyyy-MM-ddTHH:mm" contract used throughout search/logLine.ts: naive wall-clock
// digits with no attached zone (the project's configured time zone, applied separately by
// toBackendDateTime). `new Date("...")` without a Z/offset suffix parses as browser-local time,
// and we only ever read it back through the same browser-local getters below - so the actual
// browser time zone never enters the conversion, this is just a convenient Date-shaped container
// for the four/five naive digits.
function parseValue(value: string): Date | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

function formatValue(d: Date): string {
  return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}T${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

function formatDisplay(value: string): string {
  const d = parseValue(value);
  if (!d) return "";
  return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())} ${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

function Header({ date, changeYear, changeMonth, decreaseMonth, increaseMonth, prevMonthButtonDisabled, nextMonthButtonDisabled }: ReactDatePickerCustomHeaderProps) {
  return (
    <div className="flex items-center justify-between gap-1 px-1 pb-2">
      <button
        type="button"
        onClick={decreaseMonth}
        disabled={prevMonthButtonDisabled}
        className="grid h-6 w-6 shrink-0 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground disabled:opacity-30"
      >
        <ChevronLeft className="h-3.5 w-3.5" />
      </button>
      <div className="flex items-center gap-1">
        <Select value={String(date.getMonth())} onValueChange={(v) => changeMonth(Number(v))}>
          <SelectTrigger className="h-7 w-[6.5rem] gap-1 px-1.5 font-mono text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent className="font-mono text-xs">
            {MONTHS.map((m, i) => (
              <SelectItem key={m} value={String(i)}>
                {m}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={String(date.getFullYear())} onValueChange={(v) => changeYear(Number(v))}>
          <SelectTrigger className="h-7 w-[4.5rem] gap-1 px-1.5 font-mono text-xs tabular-nums">
            <SelectValue />
          </SelectTrigger>
          <SelectContent className="font-mono text-xs tabular-nums">
            {YEARS.map((y) => (
              <SelectItem key={y} value={String(y)}>
                {y}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <button
        type="button"
        onClick={increaseMonth}
        disabled={nextMonthButtonDisabled}
        className="grid h-6 w-6 shrink-0 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground disabled:opacity-30"
      >
        <ChevronRight className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function TimeSelect({ hour, minute, onChange }: { hour: number; minute: number; onChange: (hour: number, minute: number) => void }) {
  return (
    <div className="flex items-center gap-1">
      <Select value={String(hour)} onValueChange={(v) => onChange(Number(v), minute)}>
        <SelectTrigger className="h-8 w-[4rem] gap-1 px-2 font-mono text-xs tabular-nums">
          <SelectValue />
        </SelectTrigger>
        <SelectContent className="max-h-56 font-mono text-xs tabular-nums">
          {HOURS.map((h) => (
            <SelectItem key={h} value={String(h)}>
              {p2(h)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <span className="text-muted-foreground">:</span>
      <Select value={String(minute)} onValueChange={(v) => onChange(hour, Number(v))}>
        <SelectTrigger className="h-8 w-[4rem] gap-1 px-2 font-mono text-xs tabular-nums">
          <SelectValue />
        </SelectTrigger>
        <SelectContent className="max-h-56 font-mono text-xs tabular-nums">
          {MINUTES.map((m) => (
            <SelectItem key={m} value={String(m)}>
              {p2(m)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

export interface RangePreset {
  label: string;
  getRange: () => { from: string; to: string };
}

export function DateTimeRangeField({
  from,
  to,
  onApply,
  presets,
  activePreset,
}: {
  from: string;
  to: string;
  onApply: (from: string, to: string, presetLabel: string | null) => void;
  presets?: RangePreset[];
  activePreset?: string | null;
}) {
  const [open, setOpen] = useState(false);
  const [draftStart, setDraftStart] = useState<Date | null>(null);
  const [draftEnd, setDraftEnd] = useState<Date | null>(null);
  const [startTime, setStartTime] = useState({ h: 0, m: 0 });
  const [endTime, setEndTime] = useState({ h: 23, m: 59 });

  function openChange(next: boolean) {
    if (next) {
      const s = parseValue(from);
      const e = parseValue(to);
      setDraftStart(s);
      setDraftEnd(e);
      setStartTime(s ? { h: s.getHours(), m: s.getMinutes() } : { h: 0, m: 0 });
      setEndTime(e ? { h: e.getHours(), m: e.getMinutes() } : { h: 23, m: 59 });
    }
    setOpen(next);
  }

  function apply() {
    if (!draftStart || !draftEnd) return;
    const s = new Date(draftStart);
    s.setHours(startTime.h, startTime.m, 0, 0);
    const e = new Date(draftEnd);
    e.setHours(endTime.h, endTime.m, 0, 0);
    onApply(formatValue(s), formatValue(e), null);
    setOpen(false);
  }

  function pickPreset(preset: RangePreset) {
    const range = preset.getRange();
    onApply(range.from, range.to, preset.label);
    setOpen(false);
  }

  const hasValue = Boolean(from && to);

  return (
    <Popover open={open} onOpenChange={openChange}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="flex h-8 items-center gap-1.5 rounded-md border border-input bg-transparent px-2 font-mono text-xs shadow-sm transition-colors hover:border-ring/50 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <CalendarIcon className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          <span className={cn("tabular-nums", !hasValue && "text-muted-foreground")}>
            {hasValue ? `${formatDisplay(from)} → ${formatDisplay(to)}` : "Select time range"}
          </span>
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto max-h-(--radix-popover-content-available-height) space-y-3 overflow-y-auto p-3">
        {presets && presets.length > 0 && (
          <div className="flex flex-wrap items-center gap-1 rounded-lg bg-muted/40 p-1">
            {presets.map((p) => (
              <button
                key={p.label}
                type="button"
                onClick={() => pickPreset(p)}
                className={cn(
                  "rounded-md px-2.5 py-1 font-mono text-xs transition-colors",
                  activePreset === p.label ? "bg-primary text-primary-foreground shadow-glow" : "text-muted-foreground hover:text-foreground",
                )}
              >
                {p.label}
              </button>
            ))}
          </div>
        )}
        <DatePicker
          inline
          selectsRange
          monthsShown={1}
          maxDate={new Date()}
          selected={draftStart}
          startDate={draftStart}
          endDate={draftEnd}
          onChange={(dates) => {
            const [s, e] = dates;
            setDraftStart(s);
            setDraftEnd(e);
          }}
          renderCustomHeader={Header}
          calendarClassName="app-datepicker"
        />
        <div className="flex items-center justify-between gap-3 border-t pt-3">
          <div className="space-y-1">
            <div className="text-[10px] uppercase tracking-widest text-muted-foreground">Start time</div>
            <TimeSelect hour={startTime.h} minute={startTime.m} onChange={(h, m) => setStartTime({ h, m })} />
          </div>
          <div className="space-y-1">
            <div className="text-[10px] uppercase tracking-widest text-muted-foreground">End time</div>
            <TimeSelect hour={endTime.h} minute={endTime.m} onChange={(h, m) => setEndTime({ h, m })} />
          </div>
        </div>
        <div className="flex items-center justify-end gap-2 border-t pt-3">
          <Button type="button" variant="ghost" size="sm" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="button" size="sm" onClick={apply} disabled={!draftStart || !draftEnd}>
            Apply
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
