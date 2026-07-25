import { forwardRef } from "react";
import DatePicker, { type ReactDatePickerCustomHeaderProps } from "react-datepicker";
import { CalendarIcon, ChevronLeft, ChevronRight } from "lucide-react";
import "react-datepicker/dist/react-datepicker.css";

import { cn } from "@/lib/utils";

const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
const CURRENT_YEAR = new Date().getFullYear();
const YEARS = Array.from({ length: 16 }, (_, i) => CURRENT_YEAR - 12 + i);

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
  const p2 = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}T${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

const Trigger = forwardRef<HTMLButtonElement, { value?: string; onClick?: () => void }>(({ value, onClick }, ref) => (
  <button
    type="button"
    ref={ref}
    onClick={onClick}
    className="flex h-8 items-center gap-1.5 rounded-md border border-input bg-transparent px-2 font-mono text-xs tabular-nums shadow-sm transition-colors hover:border-ring/50 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
  >
    <CalendarIcon className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
    <span className={cn(!value && "text-muted-foreground")}>{value || "yyyy-mm-dd, --:--"}</span>
  </button>
));
Trigger.displayName = "DateTimeFieldTrigger";

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
        <select
          value={date.getMonth()}
          onChange={(e) => changeMonth(Number(e.target.value))}
          className="h-7 cursor-pointer rounded-md border border-input bg-transparent px-1 font-mono text-xs"
        >
          {MONTHS.map((m, i) => (
            <option key={m} value={i}>
              {m}
            </option>
          ))}
        </select>
        <select
          value={date.getFullYear()}
          onChange={(e) => changeYear(Number(e.target.value))}
          className="h-7 cursor-pointer rounded-md border border-input bg-transparent px-1.5 font-mono text-xs tabular-nums"
        >
          {YEARS.map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
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

export function DateTimeField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <DatePicker
      selected={parseValue(value)}
      onChange={(date: Date | null) => onChange(date ? formatValue(date) : "")}
      showTimeSelect
      timeIntervals={5}
      timeCaption="Time"
      dateFormat="yyyy-MM-dd, HH:mm"
      renderCustomHeader={Header}
      customInput={<Trigger />}
      calendarClassName="app-datepicker"
      popperClassName="app-datepicker-popper"
      popperPlacement="bottom-start"
    />
  );
}
