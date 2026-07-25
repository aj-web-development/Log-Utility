import { useMemo, useState } from "react";
import { Check, ChevronsUpDown } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

// ponytail: Intl.supportedValuesOf needs a fairly modern engine (Safari 15.4+; Chrome/Edge/Firefox
// all current) - acceptable for an admin-only internal tool. Falls back to a short curated list
// (still includes UTC) rather than leaving the picker with nothing to show on an older browser.
const FALLBACK_ZONES = [
  "UTC", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
  "Europe/London", "Europe/Paris", "Europe/Berlin", "Asia/Kolkata", "Asia/Singapore",
  "Asia/Tokyo", "Asia/Shanghai", "Australia/Sydney",
];

/**
 * `Intl.supportedValuesOf("timeZone")`'s notion of "canonical" can lag the IANA tzdb's own
 * preferred name for a zone - verified in this exact codebase: this engine enumerates
 * "Asia/Calcutta", not "Asia/Kolkata", even though the latter is what's actually saved on the
 * seeded "360 API" project (and is the modern IANA name). Both resolve to the identical zone, but
 * an admin searching "kolkata" would find nothing. Hand-maintaining every such legacy/canonical
 * alias pair (there are dozens across the tzdb, and they drift as IANA renames zones) isn't worth
 * it - instead just guarantee whatever is *already selected* stays present and searchable,
 * regardless of whether this engine's enumeration happens to agree with it.
 */
function listTimeZones(currentValue: string): string[] {
  let zones: string[];
  try {
    zones = Intl.supportedValuesOf("timeZone");
  } catch {
    zones = FALLBACK_ZONES;
  }
  const set = new Set(zones);
  if (currentValue) {
    set.add(currentValue);
  }
  set.delete("UTC");
  // Pinned first regardless of alphabetical order - the overwhelmingly common choice, and log
  // lines with no offset info at all default to it (see LinePattern.resolveZoneId on the backend).
  const rest = [...set].sort((a, b) => a.localeCompare(b));
  return ["UTC", ...rest];
}

export function TimeZoneCombobox({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [open, setOpen] = useState(false);
  const zones = useMemo(() => listTimeZones(value), [value]);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="h-9 w-full justify-between px-3 font-mono text-xs font-normal"
        >
          <span className={cn("truncate", !value && "text-muted-foreground")}>{value || "Select time zone…"}</span>
          <ChevronsUpDown className="ml-2 h-3.5 w-3.5 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[300px] p-0" align="start">
        <Command>
          <CommandInput placeholder="Search time zone…" className="text-xs" />
          <CommandList>
            <CommandEmpty>No time zone found.</CommandEmpty>
            <CommandGroup>
              {zones.map((zone) => (
                <CommandItem
                  key={zone}
                  value={zone}
                  onSelect={() => {
                    onChange(zone);
                    setOpen(false);
                  }}
                  className="font-mono text-xs"
                >
                  <Check className={cn("mr-2 h-3.5 w-3.5 shrink-0", value === zone ? "opacity-100" : "opacity-0")} />
                  <span className="truncate">{zone}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
