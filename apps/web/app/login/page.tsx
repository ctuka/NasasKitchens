"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

// Java backend (apps/api-java); same base URL the chat page uses.
const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const body =
        mode === "register" ? { email, password, role: "buyer" } : { email, password };
      const res = await fetch(`${API}/auth/${mode}`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(typeof data.message === "string" ? data.message : "Request failed");
        return;
      }
      localStorage.setItem("access_token", data.accessToken);
      localStorage.setItem("refresh_token", data.refreshToken);
      router.push("/chat");
    } catch {
      setError("Network error. Is the API running?");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main
      style={{
        minHeight: "100dvh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        padding: "0 20px",
      }}
    >
      <div className="fade-up" style={{ width: "100%", maxWidth: 420, marginTop: "13vh" }}>
        <Link href="/" style={{ fontWeight: 700, fontSize: 17, letterSpacing: "-0.02em", paddingLeft: 8 }}>
          Nanas&rsquo; Kitchens
        </Link>
        <div className="shell" style={{ marginTop: 22 }}>
        <div className="shell-core" style={{ padding: "30px 28px" }}>
        <h1 style={{ fontSize: 25, fontWeight: 700, letterSpacing: "-0.02em", margin: "0 0 6px" }}>
          {mode === "login" ? "Welcome back" : "Create your account"}
        </h1>
        <p style={{ color: "var(--text-2)", fontSize: 15, margin: "0 0 26px" }}>
          {mode === "login"
            ? "Sign in to keep ordering."
            : "A buyer account takes ten seconds."}
        </p>

        <form onSubmit={onSubmit} style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              className="input"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              autoComplete="email"
            />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              className="input"
              type="password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 8 characters"
              autoComplete={mode === "login" ? "current-password" : "new-password"}
            />
          </div>

          {error && (
            <p role="alert" style={{ color: "var(--accent)", fontSize: 14, margin: 0 }}>
              {error}
            </p>
          )}

          <button type="submit" disabled={busy} className="btn btn-primary" style={{ width: "100%" }}>
            {busy ? "One moment" : mode === "login" ? "Sign in" : "Create account"}
          </button>
        </form>

        <button
          onClick={() => {
            setMode(mode === "login" ? "register" : "login");
            setError("");
          }}
          style={{
            background: "transparent",
            border: "none",
            color: "var(--text-2)",
            marginTop: 22,
            padding: 0,
            fontSize: 14,
          }}
        >
          {mode === "login" ? (
            <>
              New here? <span style={{ color: "var(--accent)", fontWeight: 600 }}>Create an account</span>
            </>
          ) : (
            <>
              Have an account? <span style={{ color: "var(--accent)", fontWeight: 600 }}>Sign in</span>
            </>
          )}
        </button>
        </div>
        </div>
      </div>
    </main>
  );
}
