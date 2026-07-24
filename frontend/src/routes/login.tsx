import { Link, createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { Activity, AlertCircle, ArrowLeft, ArrowRight, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/lib/auth";

export const Route = createFileRoute("/login")({
  component: LoginPage,
});

function LoginPage() {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { signIn } = useAuth();
  const navigate = useNavigate();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await signIn(username, password);
      navigate({ to: "/admin" });
    } catch {
      setError("Invalid username or password.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-background px-4 py-10 text-foreground">
      <div className="absolute inset-0 grid-backdrop opacity-40" />
      <div className="absolute inset-0 gradient-hero" />
      <div className="relative z-10 w-full max-w-sm animate-step-in">
        <Link to="/" className="mb-6 inline-flex items-center gap-2 text-xs text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-3.5 w-3.5" /> Back to search
        </Link>
        <div className="rounded-2xl border bg-card p-8 shadow-elevated">
          <div className="mb-6 flex items-center gap-2">
            <div className="grid h-9 w-9 place-items-center rounded-lg gradient-signal shadow-glow">
              <Activity className="h-4 w-4 text-primary-foreground" strokeWidth={2.5} />
            </div>
            <div>
              <div className="font-display text-base font-semibold leading-tight">Log Utility</div>
              <div className="text-[11px] uppercase tracking-widest text-muted-foreground">Admin sign in</div>
            </div>
          </div>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="u" className="text-xs">
                Username
              </Label>
              <Input id="u" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus className="h-10 font-mono text-sm" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="p" className="text-xs">
                Password
              </Label>
              <Input id="p" type="password" value={password} onChange={(e) => setPassword(e.target.value)} className="h-10 font-mono text-sm" />
            </div>
            {error && (
              <div className="flex items-center gap-1.5 rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">
                <AlertCircle className="h-3.5 w-3.5 shrink-0" /> {error}
              </div>
            )}
            <Button
              type="submit"
              className="h-10 w-full gap-1.5 gradient-signal font-medium text-primary-foreground shadow-glow hover:opacity-90"
              disabled={loading}
            >
              {loading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <>
                  Sign in <ArrowRight className="h-4 w-4" />
                </>
              )}
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
