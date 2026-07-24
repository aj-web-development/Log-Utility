// The public search API has no server-side "active project" (no cookie, no session) - every
// /api/search/** call takes projectId explicitly. This is the SPA's own client-side stand-in,
// shared by the search page and the TopBar switcher.
const KEY = "loguty.activeProjectId";

export function getActiveProjectId(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function setActiveProjectId(id: string): void {
  try {
    localStorage.setItem(KEY, id);
  } catch {
    // Storage unavailable (private browsing, quota) - the picker still works in-memory for the
    // rest of this page's lifetime, it just won't persist across a reload.
  }
}
