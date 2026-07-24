import { type ReactNode } from "react";

import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/AppSidebar";
import { TopBar } from "@/components/TopBar";

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <SidebarProvider>
      <div className="flex h-dvh w-full overflow-hidden bg-background text-foreground">
        <AppSidebar />
        <SidebarInset className="flex min-h-0 min-w-0 flex-1 flex-col">
          <header className="flex h-14 flex-none items-center gap-3 border-b bg-background/80 px-3 backdrop-blur-md sm:px-6">
            <SidebarTrigger className="-ml-1" />
            <div className="h-6 w-px bg-border" />
            <TopBar />
          </header>
          <main className="flex min-h-0 flex-1 flex-col overflow-y-auto animate-step-in">{children}</main>
        </SidebarInset>
      </div>
    </SidebarProvider>
  );
}