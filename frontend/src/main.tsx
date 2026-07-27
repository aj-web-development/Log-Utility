import { StrictMode } from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider, createRouter } from "@tanstack/react-router";

import { routeTree } from "./routeTree.gen";
import { AuthProvider } from "@/lib/auth";
import "./styles.css";

const queryClient = new QueryClient();
// Mirrors Vite's own `base` (see vite.config.ts) so client-side routing agrees with the
// backend's server.servlet.context-path in every deployment form.
const router = createRouter({ routeTree, context: { queryClient }, basepath: import.meta.env.BASE_URL });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}

const rootEl = document.getElementById("root")!;
ReactDOM.createRoot(rootEl).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
