import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import tsConfigPaths from "vite-tsconfig-paths";
import { tanstackRouter } from "@tanstack/router-plugin/vite";

// Plain client-rendered SPA (no TanStack Start/SSR - see CLAUDE.md "frontend" section for why:
// this app is served as static assets by the Spring Boot backend, which already owns the server).
export default defineConfig({
  // Backend is served under /logutility in every deployment form (see application.yml's
  // server.servlet.context-path) - matched here so built asset URLs and the router's basepath
  // (see main.tsx) agree, and so the Vite dev server mirrors the same path locally too.
  base: "/logutility/",
  plugins: [tanstackRouter({ target: "react", autoCodeSplitting: true }), react(), tailwindcss(), tsConfigPaths()],
  build: {
    // Built straight into Spring Boot's static-resource folder so `mvnw clean package` produces
    // one self-contained jar/war with no separate copy step (see pom.xml's frontend-maven-plugin).
    outDir: "../src/main/resources/static",
    emptyOutDir: true,
  },
  server: {
    proxy: {
      "/logutility/api": "http://localhost:8080",
    },
  },
});
