import { Outlet, createFileRoute, redirect } from "@tanstack/react-router";

import { getStoredAuth } from "@/lib/authStorage";

/**
 * Parent of every /admin/* route (mirrors the file-based nesting _shell.tsx already uses).
 * There's no server-rendered admin page left to session-gate - the SPA shell always loads - so
 * this is the one place client-side "you must be signed in" is enforced, instead of repeating the
 * check in every admin page component.
 */
export const Route = createFileRoute("/_shell/admin")({
  beforeLoad: () => {
    if (!getStoredAuth()) {
      throw redirect({ to: "/login" });
    }
  },
  component: () => <Outlet />,
});
