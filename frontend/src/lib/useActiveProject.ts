import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { searchApi } from "./api";
import { getActiveProjectId, setActiveProjectId } from "./activeProject";

export function useProjects() {
  return useQuery({ queryKey: ["search-projects"], queryFn: searchApi.projects });
}

/** The SPA's client-side "active project" - see activeProject.ts for why there's no server side to it. */
export function useActiveProject() {
  const { data: projects = [], isLoading } = useProjects();
  const [activeId, setActiveIdState] = useState<string | null>(() => getActiveProjectId());

  // Nothing selected yet (first visit) or the stored id no longer exists (deleted project) -
  // fall back to the first available project once the list has loaded.
  useEffect(() => {
    if (projects.length === 0) return;
    if (!activeId || !projects.some((p) => p.id === activeId)) {
      setActiveId(projects[0].id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projects]);

  function setActiveId(id: string) {
    setActiveProjectId(id);
    setActiveIdState(id);
  }

  const project = projects.find((p) => p.id === activeId) ?? null;
  return { projects, activeId, project, setActiveId, isLoading };
}
