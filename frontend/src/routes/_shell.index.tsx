import { createFileRoute } from "@tanstack/react-router";

import { SearchScreen } from "@/features/search/SearchScreen";

export const Route = createFileRoute("/_shell/")({
  component: SearchScreen,
});
