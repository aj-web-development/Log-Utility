import { Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Moon, Sun, Palette, LogOut, LogIn, ChevronsUpDown, Check } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuth } from "@/lib/auth";
import { useActiveProject } from "@/lib/useActiveProject";
import { cn } from "@/lib/utils";

type Skin = "signal" | "console" | "paper";
const SKINS: { id: Skin; label: string; note: string }[] = [
  { id: "signal", label: "Signal", note: "Mint accent · default" },
  { id: "console", label: "Console", note: "Mono, high contrast" },
  { id: "paper", label: "Paper", note: "Light editorial" },
];

export function TopBar() {
  const [dark, setDark] = useState(true);
  const [skin, setSkin] = useState<Skin>("signal");
  const { projects, activeId, setActiveId } = useActiveProject();
  const { username, signOut } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const root = document.documentElement;
    setDark(root.classList.contains("dark"));
    setSkin((root.getAttribute("data-skin") as Skin) || "signal");
  }, []);

  const toggleTheme = () => {
    const next = !dark;
    setDark(next);
    document.documentElement.classList.toggle("dark", next);
    try {
      localStorage.setItem("theme", next ? "dark" : "light");
    } catch {
      // Storage unavailable - theme still applies for this page load, just won't persist.
    }
  };

  const pickSkin = (id: Skin) => {
    setSkin(id);
    document.documentElement.setAttribute("data-skin", id);
    try {
      localStorage.setItem("skin", id);
    } catch {
      // Storage unavailable - skin still applies for this page load, just won't persist.
    }
  };

  function handleSignOut() {
    signOut();
    navigate({ to: "/login" });
  }

  const activeProject = projects.find((p) => p.id === activeId);

  return (
    <div className="flex flex-1 min-w-0 items-center justify-between gap-3">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" className="h-9 min-w-0 gap-2 px-2 text-sm font-medium hover:bg-accent/50" disabled={projects.length === 0}>
            <span className="relative flex h-2 w-2 shrink-0">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary/60 opacity-70" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-primary" />
            </span>
            <span className="truncate font-mono text-xs sm:text-sm">{activeProject?.name ?? "No projects"}</span>
            <ChevronsUpDown className="h-3.5 w-3.5 opacity-60" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-64">
          <DropdownMenuLabel className="text-xs uppercase tracking-widest text-muted-foreground">Switch project</DropdownMenuLabel>
          {projects.map((p) => (
            <DropdownMenuItem key={p.id} onClick={() => setActiveId(p.id)} className="gap-2">
              <span className="font-mono text-xs">{p.name}</span>
              <span className="ml-auto text-[10px] text-muted-foreground">{p.nodeCount} nodes</span>
              {p.id === activeId && <Check className="h-3.5 w-3.5 text-primary" />}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>

      <div className="flex items-center gap-1">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="h-9 w-9" aria-label="Theme skin">
              <Palette className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel className="text-xs uppercase tracking-widest text-muted-foreground">Skin</DropdownMenuLabel>
            {SKINS.map((s) => (
              <DropdownMenuItem key={s.id} onClick={() => pickSkin(s.id)}>
                <div className="flex flex-col">
                  <span className="font-medium">{s.label}</span>
                  <span className="text-[11px] text-muted-foreground">{s.note}</span>
                </div>
                {skin === s.id && <Check className="ml-auto h-4 w-4 text-primary" />}
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
            <DropdownMenuLabel className="text-[11px] text-muted-foreground">Tip: press ⌘B to collapse the sidebar.</DropdownMenuLabel>
          </DropdownMenuContent>
        </DropdownMenu>

        <Button variant="ghost" size="icon" className="h-9 w-9" onClick={toggleTheme} aria-label="Toggle theme">
          {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </Button>

        {username ? (
          <Button variant="ghost" size="sm" className={cn("hidden h-9 gap-1.5 sm:inline-flex")} onClick={handleSignOut}>
            <LogOut className="h-3.5 w-3.5" />
            Sign out
          </Button>
        ) : (
          <Button asChild variant="ghost" size="sm" className="hidden h-9 gap-1.5 sm:inline-flex">
            <Link to="/login">
              <LogIn className="h-3.5 w-3.5" />
              Sign in
            </Link>
          </Button>
        )}
      </div>
    </div>
  );
}
