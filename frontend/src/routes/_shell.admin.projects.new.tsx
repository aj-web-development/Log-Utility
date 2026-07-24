import { createFileRoute } from "@tanstack/react-router";

import { ProjectWizard } from "@/features/wizard/ProjectWizard";

export const Route = createFileRoute("/_shell/admin/projects/new")({
  component: () => <ProjectWizard mode="create" />,
});
