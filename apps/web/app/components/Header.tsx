"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { getSession, logout, Session } from "../../lib/api";

export default function Header() {
  const [session, setSession] = useState<Session | null>(null);
  const router = useRouter();

  useEffect(() => {
    const sync = () => setSession(getSession());
    sync();
    window.addEventListener("session-changed", sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener("session-changed", sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  return (
    <header
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "14px 24px",
        background: "#fff",
        borderBottom: "1px solid var(--brand-border)",
      }}
    >
      <Link href="/" style={{ textDecoration: "none", display: "flex", alignItems: "baseline", gap: 6 }}>
        <span style={{ fontSize: 22, fontWeight: 800, color: "var(--brand-green)" }}>Nanas&rsquo;</span>
        <span style={{ fontSize: 22, fontWeight: 800, color: "var(--brand-orange)" }}>Kitchens</span>
      </Link>
      <nav style={{ display: "flex", alignItems: "center", gap: 18, fontSize: 15 }}>
        <Link href="/">Home</Link>
        <Link href="/chat">Chat</Link>
        {session ? (
          <>
            <span
              style={{
                background: "var(--brand-green)",
                color: "#fff",
                borderRadius: 999,
                padding: "3px 12px",
                fontSize: 13,
                textTransform: "capitalize",
              }}
            >
              {session.role}
            </span>
            <button
              onClick={() => {
                logout();
                router.push("/");
              }}
              style={{
                background: "transparent",
                border: "1px solid var(--brand-border)",
                borderRadius: 8,
                padding: "6px 14px",
                cursor: "pointer",
                fontSize: 14,
              }}
            >
              Log out
            </button>
          </>
        ) : (
          <>
            <Link href="/login">Log in</Link>
            <Link
              href="/register"
              style={{
                background: "var(--brand-orange)",
                color: "#fff",
                borderRadius: 8,
                padding: "8px 16px",
                textDecoration: "none",
                fontWeight: 600,
              }}
            >
              Sign up
            </Link>
          </>
        )}
      </nav>
    </header>
  );
}
