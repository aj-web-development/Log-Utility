import { useQuery } from "@tanstack/react-query";
import { Link, createFileRoute } from "@tanstack/react-router";
import { ArrowRight, FolderKanban, Layers, ServerCog, UploadCloud } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { projectApi } from "@/lib/api";
import { STATUS_DOT_CLASS, useProjectStatuses } from "@/lib/projectStatus";

export const Route = createFileRoute("/_shell/admin/")({
  component: AdminHome,
});

function AdminHome() {
  const { data: projects = [], isLoading } = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });

  const recent = [...projects].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 4);
  const statuses = useProjectStatuses(recent.map((p) => p.id));

  const stats = [
    { label: "Projects", value: projects.length, icon: FolderKanban },
    { label: "Nodes", value: projects.reduce((s, p) => s + p.nodeCount, 0), icon: ServerCog },
    { label: "Mapped fields", value: projects.reduce((s, p) => s + p.fieldCount, 0), icon: Layers },
  ];

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 p-6">
      <div className="relative overflow-hidden rounded-3xl border bg-card p-8 shadow-card">
        <div className="absolute inset-0 gradient-hero opacity-70" />
        <div className="relative">
          <div className="text-[11px] uppercase tracking-widest text-muted-foreground">Welcome back</div>
          <h1 className="mt-1 font-display text-3xl font-semibold tracking-tight">
            Everything's quiet — <span className="text-primary">the way you like it.</span>
          </h1>
          <p className="mt-2 max-w-xl text-sm text-muted-foreground">
            Configure the log sources your team depends on, then jump into search when something feels off.
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            <Button asChild className="gradient-signal font-medium text-primary-foreground shadow-glow hover:opacity-90">
              <Link to="/admin/projects/new">
                New project <ArrowRight className="ml-1 h-4 w-4" />
              </Link>
            </Button>
            <Button asChild variant="outline">
              <Link to="/admin/projects/upload">
                <UploadCloud className="mr-1.5 h-4 w-4" /> Upload logback XML
              </Link>
            </Button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        {stats.map((s) => (
          <div key={s.label} className="rounded-2xl border bg-card p-4 shadow-card transition-transform hover:-translate-y-0.5">
            <div className="flex items-center justify-between">
              <span className="text-[11px] uppercase tracking-widest text-muted-foreground">{s.label}</span>
              <s.icon className="h-4 w-4 text-primary" />
            </div>
            <div className="mt-2 font-display text-3xl font-semibold tabular-nums">{isLoading ? "—" : s.value}</div>
          </div>
        ))}
      </div>

      <div className="rounded-2xl border bg-card shadow-card">
        <div className="flex items-center justify-between border-b px-5 py-3">
          <div>
            <div className="font-display font-semibold">Recent projects</div>
            <div className="text-xs text-muted-foreground">Ordered by last updated</div>
          </div>
          <Button asChild variant="ghost" size="sm">
            <Link to="/admin/projects">
              View all <ArrowRight className="ml-1 h-3.5 w-3.5" />
            </Link>
          </Button>
        </div>
        <div className="divide-y">
          {isLoading && (
            <div className="space-y-2 p-5">
              <Skeleton className="h-5 w-full" />
              <Skeleton className="h-5 w-full" />
            </div>
          )}
          {!isLoading && recent.length === 0 && <div className="p-6 text-center text-sm text-muted-foreground">No projects configured yet.</div>}
          {recent.map((p) => {
            const status = statuses[p.id];
            return (
              <div key={p.id} className="flex items-center gap-3 px-5 py-3">
                <span className={"h-2 w-2 rounded-full " + (status ? STATUS_DOT_CLASS[status] : "bg-muted-foreground/20")} />
                <span className="font-mono text-sm">{p.name}</span>
                <span className="ml-auto text-xs text-muted-foreground">
                  {p.nodeCount} nodes · {p.fieldCount} fields
                </span>
                <span className="hidden w-36 text-right text-xs text-muted-foreground sm:inline">{new Date(p.updatedAt).toLocaleString()}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
