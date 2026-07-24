import { createContext, useContext, useState, type ReactNode } from "react";

import { ApiRequestError } from "./api";
import { clearStoredAuth, getStoredAuth, setStoredAuth, type StoredAuth } from "./authStorage";

interface AuthContextValue {
  username: string | null;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<StoredAuth | null>(() => getStoredAuth());

  async function signIn(username: string, password: string) {
    const header = "Basic " + btoa(`${username}:${password}`);
    // No JSON login endpoint exists - /api/projects/** is stateless HTTP Basic already, so a
    // successful call against it *is* the credential check.
    const res = await fetch("/api/projects", { headers: { Authorization: header } });
    if (!res.ok) {
      throw new ApiRequestError(res.status, null, "Invalid username or password");
    }
    const next: StoredAuth = { username, header };
    setStoredAuth(next);
    setAuth(next);
  }

  function signOut() {
    clearStoredAuth();
    setAuth(null);
  }

  return <AuthContext.Provider value={{ username: auth?.username ?? null, signIn, signOut }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
