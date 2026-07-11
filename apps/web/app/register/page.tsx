"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { register } from "../../lib/api";

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"buyer" | "seller">("buyer");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const router = useRouter();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    setBusy(true);
    try {
      await register(email, password, role);
      router.push(role === "seller" ? "/seller/menu" : "/");
    } catch (err) {
      setError(err instanceof Error && err.message === "EMAIL_TAKEN"
        ? "An account with this email already exists."
        : "Could not create the account. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main style={{ maxWidth: 420, margin: "48px auto", padding: "0 16px" }}>
      <div className="card">
        <h1 style={{ margin: "0 0 4px", color: "var(--brand-green)" }}>Join Nanas&rsquo; Kitchens</h1>
        <p style={{ margin: "0 0 20px", color: "var(--brand-muted)" }}>
          Local kitchens. Real recipes. Made with love.
        </p>
        {error && <div className="form-error" role="alert">{error}</div>}
        <form onSubmit={onSubmit}>
          <label>I want to</label>
          <div className="role-toggle" role="group" aria-label="Account type">
            <button type="button" aria-pressed={role === "buyer"} onClick={() => setRole("buyer")}>
              🍽️ Order food
            </button>
            <button type="button" aria-pressed={role === "seller"} onClick={() => setRole("seller")}>
              👩‍🍳 Cook &amp; sell
            </button>
          </div>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            className="field"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <label htmlFor="password">Password</label>
          <input
            id="password"
            className="field"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <button className="btn-primary" type="submit" disabled={busy}>
            {busy ? "Creating account…" : "Create account"}
          </button>
        </form>
        <p style={{ marginTop: 16, fontSize: 14, color: "var(--brand-muted)" }}>
          Already have an account? <Link href="/login">Log in</Link>
        </p>
      </div>
    </main>
  );
}
