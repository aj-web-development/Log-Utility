import { useQueryClient, useMutation, useQuery } from "@tanstack/react-query";
import { Link, createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { MoreHorizontal, Plus, Search, UploadCloud } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { projectApi } from "@/lib/api";
import { setActiveProjectId } from "@/lib/activeProject";
import { STATUS_DOT_CLASS, STATUS_PILL_CLASS, useProjectStatuses, type ProjectStatus } from "@/lib/projectStatus";

export const Route = createFileRoute("/_shell/admin/projects/")({
  component: ProjectsPage,
});

function ProjectsPage() {
  const [q, setQ] = useState("");
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: projects = [], isLoading } = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const statuses = useProjectStatuses(projects.map((p) => p.id));

  const deleteMutation = useMutation({
    mutationFn: (id: string) => projectApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      setPendingDeleteId(null);
    },
  });

  const filtered = projects.filter((p) => p.name.toLowerCase().includes(q.toLowerCase()));
  const pendingDeleteProject = projects.find((p) => p.id === pendingDeleteId);

  function openInSearch(id: string) {
    setActiveProjectId(id);
    navigate({ to: "/" });
  }

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 p-6">
      <header className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 sm:flex sm:justify-between">
        <div className="min-w-0">
          <h1 className="font-display text-2xl font-semibold tracking-tight">Projects</h1>
          <p className="text-sm text-muted-foreground">{projects.length} configured</p>
        </div>
        <div className="flex items-center gap-2">
          <Button asChild variant="outline">
            <Link to="/admin/projects/upload">
              <UploadCloud className="mr-1.5 h-4 w-4" /> Upload logback
            </Link>
          </Button>
          <Button asChild className="gradient-signal font-medium text-primary-foreground shadow-glow hover:opacity-90">
            <Link to="/admin/projects/new">
              <Plus className="mr-1 h-4 w-4" /> New project
            </Link>
          </Button>
        </div>
      </header>

      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Filter projects…"
          className="h-10 border-transparent bg-muted/40 pl-9 focus-visible:bg-background focus-visible:border-ring"
        />
      </div>

      <div className="overflow-hidden rounded-2xl border bg-card shadow-card">
        {isLoading ? (
          <div className="space-y-2 p-5">
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-6 w-full" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-[11px] uppercase tracking-widest text-muted-foreground">
              <tr>
                <th className="px-5 py-2.5 text-left font-medium">Name</th>
                <th className="px-5 py-2.5 text-left font-medium">Status</th>
                <th className="hidden px-5 py-2.5 text-left font-medium sm:table-cell">Nodes</th>
                <th className="hidden px-5 py-2.5 text-left font-medium sm:table-cell">Fields</th>
                <th className="hidden px-5 py-2.5 text-left font-medium md:table-cell">Updated</th>
                <th className="px-5 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y">
              {filtered.map((p) => (
                <tr key={p.id} className="transition-colors hover:bg-accent/30">
                  <td className="px-5 py-3">
                    <div className="font-mono font-medium">{p.name}</div>
                    {p.description && <div className="truncate text-[11px] text-muted-foreground">{p.description}</div>}
                  </td>
                  <td className="px-5 py-3">
                    <StatusPill status={statuses[p.id]} />
                  </td>
                  <td className="hidden px-5 py-3 tabular-nums sm:table-cell">{p.nodeCount}</td>
                  <td className="hidden px-5 py-3 tabular-nums sm:table-cell">{p.fieldCount}</td>
                  <td className="hidden px-5 py-3 text-muted-foreground md:table-cell">{new Date(p.updatedAt).toLocaleDateString()}</td>
                  <td className="px-5 py-3 text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-8 w-8">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => openInSearch(p.id)}>Open in search</DropdownMenuItem>
                        <DropdownMenuItem asChild>
                          <Link to="/admin/projects/$id/edit" params={{ id: p.id }}>
                            Edit configuration
                          </Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem className="text-destructive" onClick={() => setPendingDeleteId(p.id)}>
                          Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {!isLoading && filtered.length === 0 && (
          <div className="p-10 text-center text-sm text-muted-foreground">
            {projects.length === 0 ? (
              "No projects configured yet."
            ) : (
              <>
                No projects match "{q}".{" "}
                <button onClick={() => setQ("")} className="text-primary underline-offset-2 hover:underline">
                  Clear
                </button>
              </>
            )}
          </div>
        )}
      </div>

      <AlertDialog open={pendingDeleteId !== null} onOpenChange={(open) => !open && setPendingDeleteId(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete "{pendingDeleteProject?.name}"?</AlertDialogTitle>
            <AlertDialogDescription>
              This removes the project's nodes, log files, and field mappings. Log files on disk are untouched. This can't be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deleteMutation.isPending}
              onClick={() => pendingDeleteId && deleteMutation.mutate(pendingDeleteId)}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function StatusPill({ status }: { status: ProjectStatus | undefined }) {
  if (!status) return <span className="text-[11px] text-muted-foreground">…</span>;
  return (
    <span className={"inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium capitalize " + STATUS_PILL_CLASS[status]}>
      <span className={"h-1.5 w-1.5 rounded-full " + STATUS_DOT_CLASS[status]} /> {status}
    </span>
  );
}
