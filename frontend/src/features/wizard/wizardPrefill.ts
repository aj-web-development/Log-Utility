import type { LogbackParseResult } from "@/lib/types";

interface WizardPrefill {
  logback: LogbackParseResult;
}

// In-memory handoff from the upload page to the "new project" wizard - both are on the same SPA
// navigation, no reload in between, so a module-level variable is enough; nothing needs this to
// survive a refresh.
let stash: WizardPrefill | null = null;

export function setWizardPrefill(prefill: WizardPrefill): void {
  stash = prefill;
}

export function consumeWizardPrefill(): WizardPrefill | null {
  const prefill = stash;
  stash = null;
  return prefill;
}
