// Plain storage helpers, no React - used by both the api client (attaching the header, and
// clearing it on a 401) and the auth context (reading/writing it). Kept dependency-free so
// neither of those two modules has to import the other.
const KEY = "loguty.adminAuth";

export interface StoredAuth {
  username: string;
  header: string; // "Basic base64(user:pass)"
}

// ponytail: credentials live in sessionStorage for this single-admin internal tool (cleared on
// tab close / sign-out); upgrade to a short-lived server-issued token if this UI is ever exposed
// beyond a trusted admin.
export function getStoredAuth(): StoredAuth | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as StoredAuth) : null;
  } catch {
    return null;
  }
}

export function setStoredAuth(auth: StoredAuth): void {
  sessionStorage.setItem(KEY, JSON.stringify(auth));
}

export function clearStoredAuth(): void {
  sessionStorage.removeItem(KEY);
}

export function getAuthHeader(): string | null {
  return getStoredAuth()?.header ?? null;
}
