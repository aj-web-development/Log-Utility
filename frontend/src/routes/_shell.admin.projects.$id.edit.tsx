import { createFileRoute } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";

import { Skeleton } from "@/components/ui/skeleton";
import { projectApi } from "@/lib/api";
import { ProjectWizard } from "@/features/wizard/ProjectWizard";

export const Route = createFileRoute("/_shell/admin/projects/$id/edit")({
  component: EditProjectPage,
});

function EditProjectPage() {
  const { id } = Route.useParams();
  const { data, isLoading, isError } = useQuery({ queryKey: ["project-detail", id], queryFn: () => projectApi.get(id) });

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl p-6">
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }
  if (isError || !data) {
    return <div className="mx-auto max-w-4xl p-6 text-sm text-destructive">Could not load this project.</div>;
  }
  return <ProjectWizard mode="edit" project={data} />;
}
